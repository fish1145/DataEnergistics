package com.fish_dan_.data_energistics.orbital.storage;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointKind;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLimitException;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointRecord;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponAction;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * World-level source of truth for orbital-weapon ownership, delegated access and physical endpoint bindings.
 *
 * <p>
 * Mutations are restricted to the server thread. Owner, access and endpoint indexes are rebuilt from authoritative
 * weapon records during loading so redundant serialized indexes cannot disagree with weapon state.
 * </p>
 */
public final class OrbitalWeaponSavedData extends SavedData {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String DATA_NAME = Data_Energistics.MODID + "_orbital_weapons";
    private static final Factory<OrbitalWeaponSavedData> FACTORY = new Factory<>(
            OrbitalWeaponSavedData::new,
            OrbitalWeaponSavedData::load);

    private final Map<UUID, OrbitalWeaponRecord> weapons = new LinkedHashMap<>();
    private final Map<UUID, UUID> ownerIndex = new HashMap<>();
    private final Map<UUID, Set<UUID>> accessIndex = new HashMap<>();
    private final Map<OrbitalEndpointLocation, UUID> endpointIndex = new HashMap<>();

    private OrbitalWeaponSavedData() {}

    /**
     * Returns the overworld-owned SavedData instance shared by all dimensions.
     */
    public static OrbitalWeaponSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Creates exactly one owned weapon for a player, or returns their existing weapon.
     */
    public OrbitalWeaponRecord createForOwner(MinecraftServer server, UUID ownerId) {
        requireServerThread(server);
        UUID existingWeaponId = this.ownerIndex.get(ownerId);
        if (existingWeaponId != null) {
            return requireWeapon(existingWeaponId);
        }

        OrbitalWeaponRecord weapon = OrbitalWeaponRecord.create(newWeaponId(), ownerId);
        putRecord(weapon);
        setDirty();
        return weapon;
    }

    /**
     * Creates or reuses an owned weapon and attaches a physical endpoint in the same SavedData mutation.
     */
    public OrbitalWeaponRecord provisionForOwner(
                                                 MinecraftServer server,
                                                 UUID ownerId,
                                                 OrbitalEndpointLocation location,
                                                 OrbitalEndpointKind kind) {
        requireServerThread(server);
        UUID boundWeaponId = this.endpointIndex.get(location);
        if (boundWeaponId != null) {
            OrbitalWeaponRecord boundWeapon = requireWeapon(boundWeaponId);
            OrbitalEndpointRecord boundEndpoint = boundWeapon.endpoints().get(location);
            if (boundEndpoint == null) {
                throw new IllegalStateException("Endpoint index is inconsistent at " + location);
            }
            if (!boundWeapon.ownerId().equals(ownerId) || boundEndpoint.kind() != kind) {
                throw new IllegalStateException("Endpoint location is already bound to another orbital weapon");
            }
            return boundWeapon;
        }

        UUID ownedWeaponId = this.ownerIndex.get(ownerId);
        boolean creatingWeapon = ownedWeaponId == null;
        OrbitalWeaponRecord current = creatingWeapon ? OrbitalWeaponRecord.create(newWeaponId(), ownerId) : requireWeapon(ownedWeaponId);
        if (current.endpoints().containsKey(location)) {
            throw new IllegalStateException("Endpoint index is inconsistent at " + location);
        }
        requireEndpointCapacity(current, location);
        OrbitalEndpointRecord endpoint = new OrbitalEndpointRecord(
                location,
                kind,
                current.nextEndpointPriority());
        OrbitalWeaponRecord updated = current.withEndpoint(endpoint);

        if (creatingWeapon) {
            putRecord(updated);
        } else {
            this.weapons.put(updated.weaponId(), updated);
            this.endpointIndex.put(location, updated.weaponId());
        }
        setDirty();
        return updated;
    }

    /**
     * Finds a weapon by its stable identity.
     */
    public Optional<OrbitalWeaponRecord> find(UUID weaponId) {
        return Optional.ofNullable(this.weapons.get(weaponId));
    }

    /**
     * Finds the weapon currently bound to a physical endpoint location.
     */
    public Optional<OrbitalWeaponRecord> weaponAt(OrbitalEndpointLocation location) {
        UUID weaponId = this.endpointIndex.get(location);
        return weaponId == null ? Optional.empty() : Optional.of(requireWeapon(weaponId));
    }

    /**
     * Removes a physical endpoint after its bound block has been destroyed or explicitly unbound.
     */
    public boolean removeEndpoint(
                                  MinecraftServer server,
                                  UUID weaponId,
                                  OrbitalEndpointLocation location) {
        requireServerThread(server);
        OrbitalWeaponRecord current = requireWeapon(weaponId);
        UUID indexedWeaponId = this.endpointIndex.get(location);
        boolean recorded = current.endpoints().containsKey(location);
        if (recorded != weaponId.equals(indexedWeaponId)) {
            throw new IllegalStateException("Endpoint index is inconsistent at " + location);
        }
        if (!recorded) {
            return false;
        }

        OrbitalWeaponRecord updated = current.withoutEndpoint(location);
        this.endpointIndex.remove(location);
        this.weapons.put(weaponId, updated);
        setDirty();
        return true;
    }

    /**
     * Finds the weapon owned by a player.
     */
    public Optional<OrbitalWeaponRecord> ownedBy(UUID ownerId) {
        UUID weaponId = this.ownerIndex.get(ownerId);
        return weaponId == null ? Optional.empty() : Optional.of(requireWeapon(weaponId));
    }

    /**
     * Returns every owned or delegated weapon visible to a player in stable weapon-ID order.
     */
    public List<OrbitalWeaponRecord> accessibleTo(UUID playerId) {
        HashSet<UUID> weaponIds = new HashSet<>(
                this.accessIndex.getOrDefault(playerId, Set.of()));
        UUID ownedWeaponId = this.ownerIndex.get(playerId);
        if (ownedWeaponId != null) {
            weaponIds.add(ownedWeaponId);
        }
        return weaponIds.stream()
                .map(this::requireWeapon)
                .sorted(Comparator.comparing(OrbitalWeaponRecord::weaponId))
                .toList();
    }

    /**
     * Adds or changes a delegated role after verifying the acting player against authoritative state.
     */
    public void authorize(
                          MinecraftServer server,
                          UUID weaponId,
                          UUID actorId,
                          UUID playerId,
                          OrbitalAccessRole role) {
        requireServerThread(server);
        OrbitalWeaponRecord current = requireAuthorizedOwner(weaponId, actorId);
        Set<UUID> accessibleWeaponIds = this.accessIndex.getOrDefault(playerId, Set.of());
        boolean indexed = accessibleWeaponIds.contains(weaponId);
        if (current.delegatedRoles().containsKey(playerId) != indexed) {
            throw new IllegalStateException("Access index is inconsistent for weapon " + weaponId);
        }

        OrbitalWeaponRecord updated = current.withRole(playerId, role);
        if (updated == current) {
            return;
        }

        if (!indexed) {
            this.accessIndex.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(weaponId);
        }
        this.weapons.put(weaponId, updated);
        setDirty();
    }

    /**
     * Removes a delegated role after verifying the acting player against authoritative state.
     */
    public void revoke(
                       MinecraftServer server,
                       UUID weaponId,
                       UUID actorId,
                       UUID playerId) {
        requireServerThread(server);
        OrbitalWeaponRecord current = requireAuthorizedOwner(weaponId, actorId);
        Set<UUID> accessibleWeaponIds = this.accessIndex.getOrDefault(playerId, Set.of());
        boolean indexed = accessibleWeaponIds.contains(weaponId);
        if (current.delegatedRoles().containsKey(playerId) != indexed) {
            throw new IllegalStateException("Access index is inconsistent for weapon " + weaponId);
        }

        OrbitalWeaponRecord updated = current.withoutRole(playerId);
        if (updated == current) {
            return;
        }

        accessibleWeaponIds.remove(weaponId);
        if (accessibleWeaponIds.isEmpty()) {
            this.accessIndex.remove(playerId);
        }
        this.weapons.put(weaponId, updated);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return OrbitalWeaponNbtCodec.save(tag, this.weapons.values());
    }

    private static OrbitalWeaponSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        OrbitalWeaponSavedData data = new OrbitalWeaponSavedData();
        for (OrbitalWeaponRecord weapon : OrbitalWeaponNbtCodec.load(tag)) {
            if (data.weapons.containsKey(weapon.weaponId())) {
                LOGGER.warn("Ignoring duplicate orbital weapon id {}", weapon.weaponId());
                continue;
            }
            if (data.ownerIndex.containsKey(weapon.ownerId())) {
                LOGGER.warn(
                        "Ignoring orbital weapon {} because owner {} already owns another weapon",
                        weapon.weaponId(),
                        weapon.ownerId());
                continue;
            }
            data.putRecord(data.filterConflictingEndpoints(weapon));
        }
        return data;
    }

    private void putRecord(OrbitalWeaponRecord weapon) {
        if (this.weapons.containsKey(weapon.weaponId())) {
            throw new IllegalStateException("Duplicate orbital weapon id " + weapon.weaponId());
        }
        if (this.ownerIndex.containsKey(weapon.ownerId())) {
            throw new IllegalStateException("Owner already has an orbital weapon " + weapon.ownerId());
        }
        for (OrbitalEndpointLocation location : weapon.endpoints().keySet()) {
            if (this.endpointIndex.containsKey(location)) {
                throw new IllegalStateException("Endpoint location is already bound " + location);
            }
        }

        this.weapons.put(weapon.weaponId(), weapon);
        this.ownerIndex.put(weapon.ownerId(), weapon.weaponId());
        for (UUID playerId : weapon.delegatedRoles().keySet()) {
            this.accessIndex.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(weapon.weaponId());
        }
        for (OrbitalEndpointLocation location : weapon.endpoints().keySet()) {
            this.endpointIndex.put(location, weapon.weaponId());
        }
    }

    private OrbitalWeaponRecord filterConflictingEndpoints(OrbitalWeaponRecord weapon) {
        LinkedHashMap<OrbitalEndpointLocation, OrbitalEndpointRecord> acceptedEndpoints = new LinkedHashMap<>();
        for (OrbitalEndpointRecord endpoint : weapon.endpoints().values()) {
            UUID indexedWeaponId = this.endpointIndex.get(endpoint.location());
            if (indexedWeaponId != null) {
                LOGGER.warn(
                        "Ignoring endpoint {} on orbital weapon {} because it is already bound to {}",
                        endpoint.location(),
                        weapon.weaponId(),
                        indexedWeaponId);
                continue;
            }
            acceptedEndpoints.put(endpoint.location(), endpoint);
        }
        if (acceptedEndpoints.size() == weapon.endpoints().size()) {
            return weapon;
        }
        return new OrbitalWeaponRecord(
                weapon.weaponId(),
                weapon.ownerId(),
                weapon.delegatedRoles(),
                acceptedEndpoints);
    }

    private static void requireEndpointCapacity(
                                                OrbitalWeaponRecord weapon,
                                                OrbitalEndpointLocation location) {
        DataEnergisticsSettings.OrbitalWeapon settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon();
        if (weapon.endpoints().size() >= settings.maxEndpointsPerWeapon()) {
            throw new OrbitalEndpointLimitException(
                    "Orbital weapon " + weapon.weaponId() + " has reached its endpoint limit");
        }
        long dimensionEndpointCount = weapon.endpoints().keySet().stream()
                .filter(existing -> existing.dimensionId().equals(location.dimensionId()))
                .count();
        if (dimensionEndpointCount >= settings.maxEndpointsPerDimension()) {
            throw new OrbitalEndpointLimitException(
                    "Orbital weapon " + weapon.weaponId() + " has reached its endpoint limit in " + location.dimensionId());
        }
    }

    private UUID newWeaponId() {
        UUID weaponId;
        do {
            weaponId = UUID.randomUUID();
        } while (this.weapons.containsKey(weaponId));
        return weaponId;
    }

    private OrbitalWeaponRecord requireAuthorizedOwner(UUID weaponId, UUID actorId) {
        OrbitalWeaponRecord weapon = requireWeapon(weaponId);
        if (!weapon.canPerform(actorId, OrbitalWeaponAction.MANAGE_AUTHORIZATIONS)) {
            throw new SecurityException("Player " + actorId + " cannot manage authorizations for weapon " + weaponId);
        }
        return weapon;
    }

    private OrbitalWeaponRecord requireWeapon(UUID weaponId) {
        OrbitalWeaponRecord weapon = this.weapons.get(weaponId);
        if (weapon == null) {
            throw new IllegalStateException("Unknown orbital weapon " + weaponId);
        }
        return weapon;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Orbital weapon state may only be modified on the server thread");
        }
    }
}
