package com.fish_dan_.data_energistics.orbital.storage;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponAction;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

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
 * World-level source of truth for orbital-weapon ownership and delegated access.
 *
 * <p>
 * Mutations are restricted to the server thread. Owner and access indexes are rebuilt from authoritative weapon
 * records during loading so redundant serialized indexes cannot disagree with weapon state.
 * </p>
 */
public final class OrbitalWeaponSavedData extends SavedData {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String DATA_NAME = Data_Energistics.MODID + "_orbital_weapons";
    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int SCHEMA_VERSION = 1;
    private static final String WEAPONS_TAG = "weapons";
    private static final String WEAPON_ID_TAG = "weapon_id";
    private static final String OWNER_ID_TAG = "owner_id";
    private static final String DELEGATED_ROLES_TAG = "delegated_roles";
    private static final String PLAYER_ID_TAG = "player_id";
    private static final String ROLE_TAG = "role";
    private static final Factory<OrbitalWeaponSavedData> FACTORY = new Factory<>(
            OrbitalWeaponSavedData::new,
            OrbitalWeaponSavedData::load);

    private final Map<UUID, OrbitalWeaponRecord> weapons = new LinkedHashMap<>();
    private final Map<UUID, UUID> ownerIndex = new HashMap<>();
    private final Map<UUID, Set<UUID>> accessIndex = new HashMap<>();

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

        UUID weaponId;
        do {
            weaponId = UUID.randomUUID();
        } while (this.weapons.containsKey(weaponId));

        OrbitalWeaponRecord weapon = OrbitalWeaponRecord.create(weaponId, ownerId);
        putRecord(weapon);
        setDirty();
        return weapon;
    }

    /**
     * Finds a weapon by its stable identity.
     */
    public Optional<OrbitalWeaponRecord> find(UUID weaponId) {
        return Optional.ofNullable(this.weapons.get(weaponId));
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
        tag.putInt(SCHEMA_VERSION_TAG, SCHEMA_VERSION);
        ListTag weaponList = new ListTag();
        this.weapons.values().stream()
                .sorted(Comparator.comparing(OrbitalWeaponRecord::weaponId))
                .map(OrbitalWeaponSavedData::writeWeapon)
                .forEach(weaponList::add);
        tag.put(WEAPONS_TAG, weaponList);
        return tag;
    }

    private static OrbitalWeaponSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        OrbitalWeaponSavedData data = new OrbitalWeaponSavedData();
        if (!tag.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            LOGGER.warn("Ignoring orbital weapon SavedData without a schema version");
            return data;
        }
        int schemaVersion = tag.getInt(SCHEMA_VERSION_TAG);
        if (schemaVersion != SCHEMA_VERSION) {
            LOGGER.warn(
                    "Ignoring orbital weapon SavedData schema version {}; expected {}",
                    schemaVersion,
                    SCHEMA_VERSION);
            return data;
        }

        Tag weaponsTag = tag.get(WEAPONS_TAG);
        if (!(weaponsTag instanceof ListTag weaponList)) {
            return data;
        }
        for (Tag weaponTag : weaponList) {
            if (!(weaponTag instanceof CompoundTag weaponEntry)) {
                continue;
            }
            OrbitalWeaponRecord weapon = readWeapon(weaponEntry);
            if (weapon == null) {
                continue;
            }
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
            data.putRecord(weapon);
        }
        return data;
    }

    private static CompoundTag writeWeapon(OrbitalWeaponRecord weapon) {
        CompoundTag weaponTag = new CompoundTag();
        weaponTag.putUUID(WEAPON_ID_TAG, weapon.weaponId());
        weaponTag.putUUID(OWNER_ID_TAG, weapon.ownerId());
        ListTag roleList = new ListTag();
        weapon.delegatedRoles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(OrbitalWeaponSavedData::writeRole)
                .forEach(roleList::add);
        weaponTag.put(DELEGATED_ROLES_TAG, roleList);
        return weaponTag;
    }

    private static CompoundTag writeRole(Map.Entry<UUID, OrbitalAccessRole> entry) {
        CompoundTag roleTag = new CompoundTag();
        roleTag.putUUID(PLAYER_ID_TAG, entry.getKey());
        roleTag.putString(ROLE_TAG, entry.getValue().name());
        return roleTag;
    }

    private static @Nullable OrbitalWeaponRecord readWeapon(CompoundTag weaponTag) {
        UUID weaponId = readUuid(weaponTag, WEAPON_ID_TAG, "weapon id");
        UUID ownerId = readUuid(weaponTag, OWNER_ID_TAG, "owner id");
        if (weaponId == null || ownerId == null) {
            return null;
        }

        LinkedHashMap<UUID, OrbitalAccessRole> roles = new LinkedHashMap<>();
        Tag rolesTag = weaponTag.get(DELEGATED_ROLES_TAG);
        if (rolesTag instanceof ListTag roleList) {
            readRoles(weaponId, ownerId, roleList, roles);
        }
        return new OrbitalWeaponRecord(weaponId, ownerId, roles);
    }

    private static void readRoles(
                                  UUID weaponId,
                                  UUID ownerId,
                                  ListTag roleList,
                                  Map<UUID, OrbitalAccessRole> roles) {
        for (Tag roleTag : roleList) {
            if (!(roleTag instanceof CompoundTag roleEntry)) {
                continue;
            }
            UUID playerId = readUuid(roleEntry, PLAYER_ID_TAG, "delegated player id");
            if (playerId == null) {
                continue;
            }
            if (ownerId.equals(playerId)) {
                LOGGER.warn("Ignoring delegated role for owner {} on orbital weapon {}", ownerId, weaponId);
                continue;
            }

            OrbitalAccessRole role;
            try {
                role = OrbitalAccessRole.valueOf(roleEntry.getString(ROLE_TAG));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn(
                        "Ignoring invalid delegated role '{}' for player {} on orbital weapon {}",
                        roleEntry.getString(ROLE_TAG),
                        playerId,
                        weaponId);
                continue;
            }
            if (roles.putIfAbsent(playerId, role) != null) {
                LOGGER.warn("Ignoring duplicate delegated player {} on orbital weapon {}", playerId, weaponId);
            }
        }
    }

    private static @Nullable UUID readUuid(CompoundTag tag, String key, String description) {
        if (!tag.hasUUID(key)) {
            LOGGER.warn("Ignoring orbital weapon data with missing or invalid {}", description);
            return null;
        }
        return tag.getUUID(key);
    }

    private void putRecord(OrbitalWeaponRecord weapon) {
        this.weapons.put(weapon.weaponId(), weapon);
        this.ownerIndex.put(weapon.ownerId(), weapon.weaponId());
        for (UUID playerId : weapon.delegatedRoles().keySet()) {
            this.accessIndex.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(weapon.weaponId());
        }
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
