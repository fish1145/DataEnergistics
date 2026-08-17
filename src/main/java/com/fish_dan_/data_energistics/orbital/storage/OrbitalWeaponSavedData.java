package com.fish_dan_.data_energistics.orbital.storage;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointAvailability;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointKind;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLimitException;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointRecord;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponAction;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycle;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.projection.OrbitalProjectionVisualSnapshot;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalReserveCharging;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
    private static final String LAST_SELECTED_WEAPONS_TAG = "last_selected_weapons";
    private static final String PLAYER_ID_TAG = "player_id";
    private static final String WEAPON_ID_TAG = "weapon_id";
    private static final String TRANSFERS_TAG = "ownership_transfers";
    private static final String TRANSFER_ID_TAG = "transfer_id";
    private static final String RECIPIENT_ID_TAG = "recipient_id";
    private static final String EXPIRES_AT_TAG = "expires_at";
    private static final long TRANSFER_WINDOW_TICKS = 20L * 60L;
    private static final Comparator<OrbitalEndpointRecord> ENDPOINT_PRIORITY_ORDER = Comparator
            .comparingInt(OrbitalEndpointRecord::priority)
            .thenComparing(endpoint -> endpoint.location().dimensionId().toString())
            .thenComparingInt(endpoint -> endpoint.location().pos().getX())
            .thenComparingInt(endpoint -> endpoint.location().pos().getY())
            .thenComparingInt(endpoint -> endpoint.location().pos().getZ());
    private static final Factory<OrbitalWeaponSavedData> FACTORY = new Factory<>(
            OrbitalWeaponSavedData::new,
            OrbitalWeaponSavedData::load);

    private final Map<UUID, OrbitalWeaponRecord> weapons = new LinkedHashMap<>();
    private final Map<UUID, UUID> ownerIndex = new HashMap<>();
    private final Map<UUID, Set<UUID>> accessIndex = new HashMap<>();
    private final Map<OrbitalEndpointLocation, UUID> endpointIndex = new HashMap<>();
    private final Set<UUID> reserveChargeFaults = new HashSet<>();
    private final Map<UUID, UUID> lastSelectedWeaponByPlayer = new HashMap<>();
    private final Map<UUID, OrbitalOwnershipTransfer> ownershipTransfers = new LinkedHashMap<>();

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
        Optional<OrbitalWeaponRecord> boundWeapon = findCompatibleBoundEndpoint(ownerId, location, kind);
        if (boundWeapon.isPresent()) {
            return ensurePrimaryAnchor(server, boundWeapon.orElseThrow());
        }

        UUID ownedWeaponId = this.ownerIndex.get(ownerId);
        if (ownedWeaponId != null) {
            return ensurePrimaryAnchor(server, addEndpoint(requireWeapon(ownedWeaponId), location, kind));
        }

        OrbitalWeaponRecord current = OrbitalWeaponRecord.create(newWeaponId(), ownerId);
        OrbitalEndpointRecord endpoint = createEndpoint(current, location, kind);
        OrbitalWeaponRecord updated = current.withEndpoint(endpoint);
        putRecord(updated);
        setDirty();
        return ensurePrimaryAnchor(server, updated);
    }

    /**
     * Binds a physical endpoint to the placing player's existing owned weapon without creating a weapon record.
     *
     * @return the bound weapon, or an empty result when the player does not own a weapon
     */
    public Optional<OrbitalWeaponRecord> bindExistingForOwner(
                                                              MinecraftServer server,
                                                              UUID ownerId,
                                                              OrbitalEndpointLocation location,
                                                              OrbitalEndpointKind kind) {
        requireServerThread(server);
        Optional<OrbitalWeaponRecord> boundWeapon = findCompatibleBoundEndpoint(ownerId, location, kind);
        if (boundWeapon.isPresent()) {
            return Optional.of(ensurePrimaryAnchor(server, boundWeapon.orElseThrow()));
        }

        UUID ownedWeaponId = this.ownerIndex.get(ownerId);
        if (ownedWeaponId == null) {
            return Optional.empty();
        }
        return Optional.of(ensurePrimaryAnchor(server, addEndpoint(requireWeapon(ownedWeaponId), location, kind)));
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
     * Returns an immutable location-to-weapon snapshot for startup endpoint reconciliation.
     */
    public Map<OrbitalEndpointLocation, UUID> endpointBindings() {
        return Map.copyOf(this.endpointIndex);
    }

    /**
     * Returns whether the weapon has a valid, powered AE endpoint in the requested dimension.
     *
     * <p>
     * This server-thread query is the authoritative dimension-unlock boundary used by attack preview and confirmation.
     * </p>
     */
    public boolean hasOnlineEndpoint(
                                     MinecraftServer server,
                                     UUID weaponId,
                                     ResourceLocation dimensionId) {
        requireServerThread(server);
        OrbitalWeaponRecord weapon = requireWeapon(weaponId);
        return weapon.endpoints().values().stream()
                .filter(endpoint -> endpoint.location().dimensionId().equals(dimensionId))
                .anyMatch(endpoint -> OrbitalEndpointAvailability.isOnline(server, weaponId, endpoint));
    }

    /**
     * Captures the public primary-projection baseline for one dimension without exposing private weapon state. The
     * server tick that reconciles endpoint failover runs before the visual ticker, so this view never resurrects a
     * failed anchor on the client.
     */
    public List<OrbitalProjectionVisualSnapshot> publicVisualProjections(ServerLevel level, long gameTime) {
        requireServerThread(level.getServer());
        ResourceLocation dimensionId = level.dimension().location();
        int projectionY = level.getMaxBuildHeight() + 320;
        return this.weapons.values().stream()
                .sorted(Comparator.comparing(OrbitalWeaponRecord::weaponId))
                .filter(weapon -> weapon.lifecycle().state() != OrbitalWeaponLifecycleState.DORMANT)
                .filter(weapon -> weapon.primaryAnchor() != null && weapon.primaryAnchor().dimensionId().equals(dimensionId))
                .map(weapon -> projectionSnapshot(level, gameTime, projectionY, weapon))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Charges every weapon from at most one priority-selected AE endpoint for this server tick.
     *
     * <p>
     * A failure is isolated to its weapon and logged once until a later tick succeeds. Successful transfers replace
     * immutable records and mark this SavedData dirty for persistence.
     * </p>
     */
    public void chargeReserves(MinecraftServer server) {
        requireServerThread(server);
        purgeExpiredTransfers(server);
        DataEnergisticsSettings.OrbitalWeapon settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon();
        boolean changed = false;
        for (Map.Entry<UUID, OrbitalWeaponRecord> entry : this.weapons.entrySet()) {
            UUID weaponId = entry.getKey();
            OrbitalWeaponRecord updated = entry.getValue();
            boolean chargeSucceeded = true;
            try {
                updated = reconcilePrimaryAnchor(server, updated, settings);
                updated = OrbitalReserveCharging.charge(server, updated, settings);
            } catch (RuntimeException exception) {
                chargeSucceeded = false;
                if (this.reserveChargeFaults.add(weaponId)) {
                    LOGGER.error("Failed to charge orbital weapon {} from its AE endpoints", weaponId, exception);
                }
            }
            if (chargeSucceeded && this.reserveChargeFaults.remove(weaponId)) {
                LOGGER.info("Recovered reserve charging for orbital weapon {}", weaponId);
            }
            OrbitalEnergyReserve maintainedReserve = applyDeploymentMaintenance(updated, settings);
            updated = updated.withReserve(maintainedReserve);
            updated = updated.withLifecycle(updated.lifecycle().reconcile(maintainedReserve, settings));
            if (updated.primaryAnchor() == null && updated.lifecycle().state() == OrbitalWeaponLifecycleState.DEPLOYED) {
                updated = updated.withLifecycle(OrbitalWeaponLifecycle.dormant());
            }
            if (updated != entry.getValue()) {
                entry.setValue(updated);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    /**
     * Atomically moves an attack cost out of a weapon reserve after checking the actor's FIRE permission.
     *
     * @return {@code true} when both resources were available and debited, otherwise {@code false} without mutation
     */
    public boolean tryDebitReserve(
                                   MinecraftServer server,
                                   UUID weaponId,
                                   UUID actorId,
                                   long celestialEnergy,
                                   long aeEnergy) {
        requireServerThread(server);
        OrbitalWeaponRecord current = requireWeapon(weaponId);
        if (!current.canPerform(actorId, OrbitalWeaponAction.FIRE)) {
            throw new SecurityException("Player " + actorId + " cannot fire orbital weapon " + weaponId);
        }
        if (!current.allowsNewAttacks()) {
            return false;
        }
        if (!current.reserve().canAfford(celestialEnergy, aeEnergy)) {
            return false;
        }
        OrbitalEnergyReserve debitedReserve = current.reserve().withDebit(celestialEnergy, aeEnergy);
        OrbitalWeaponRecord updated = current.withReserve(debitedReserve)
                .withLifecycle(current.lifecycle().afterDebit(debitedReserve, DataEnergisticsConfiguration.INSTANCE.orbitalWeapon()));
        this.weapons.put(weaponId, updated);
        setDirty();
        return true;
    }

    /**
     * Returns a cancelled warning escrow to the reserve after rechecking the cancelling actor's permission.
     */
    public void refundWarningReserve(
                                     MinecraftServer server,
                                     UUID weaponId,
                                     UUID actorId,
                                     long celestialEnergy,
                                     long aeEnergy) {
        requireServerThread(server);
        OrbitalWeaponRecord current = requireWeapon(weaponId);
        if (!current.canPerform(actorId, OrbitalWeaponAction.CANCEL_WARNING_ATTACK)) {
            throw new SecurityException("Player " + actorId + " cannot cancel orbital weapon " + weaponId);
        }
        OrbitalWeaponRecord updated = current.withReserve(current.reserve().withCredit(celestialEnergy, aeEnergy));
        this.weapons.put(weaponId, updated);
        setDirty();
    }

    /** Credits escrow from an administrator-approved FAULTED attack refund without applying player permissions. */
    public void refundFaultedReserve(
                                     MinecraftServer server,
                                     UUID weaponId,
                                     long celestialEnergy,
                                     long aeEnergy) {
        requireServerThread(server);
        OrbitalWeaponRecord current = requireWeapon(weaponId);
        OrbitalWeaponRecord updated = current.withReserve(current.reserve().withCredit(celestialEnergy, aeEnergy));
        this.weapons.put(weaponId, updated);
        setDirty();
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
        updated = reconcilePrimaryAnchor(server, updated, DataEnergisticsConfiguration.INSTANCE.orbitalWeapon());
        this.weapons.put(weaponId, updated);
        setDirty();
        return true;
    }

    /**
     * Changes the owner-controlled endpoint priority and shifts the other endpoints instead of creating duplicate
     * ranks. The resulting order is persisted as a dense sequence so reserve charging and anchor failover agree.
     */
    public boolean setEndpointPriority(
                                       MinecraftServer server,
                                       UUID actorId,
                                       UUID weaponId,
                                       OrbitalEndpointLocation location,
                                       int priority) {
        requireServerThread(server);
        OrbitalWeaponRecord current = requireWeapon(weaponId);
        if (!current.canPerform(actorId, OrbitalWeaponAction.ORDER_ENDPOINTS)) {
            throw new SecurityException("Player " + actorId + " cannot order endpoints for orbital weapon " + weaponId);
        }
        if (priority < 0 || priority >= current.endpoints().size()) {
            throw new IllegalArgumentException("Endpoint priority is outside the weapon endpoint range");
        }
        OrbitalEndpointRecord selected = current.endpoints().get(location);
        if (selected == null) {
            return false;
        }

        ArrayList<OrbitalEndpointRecord> ordered = new ArrayList<>(current.endpoints().values());
        ordered.sort(ENDPOINT_PRIORITY_ORDER);
        int oldIndex = ordered.indexOf(selected);
        if (oldIndex < 0) {
            throw new IllegalStateException("Endpoint map is inconsistent for " + location);
        }
        ordered.remove(oldIndex);
        ordered.add(priority, selected);

        LinkedHashMap<OrbitalEndpointLocation, OrbitalEndpointRecord> reordered = new LinkedHashMap<>();
        boolean changed = false;
        for (int index = 0; index < ordered.size(); index++) {
            OrbitalEndpointRecord endpoint = ordered.get(index);
            OrbitalEndpointRecord normalized = endpoint.priority() == index ? endpoint : new OrbitalEndpointRecord(endpoint.location(), endpoint.kind(), index);
            reordered.put(normalized.location(), normalized);
            changed |= normalized != endpoint;
        }
        if (!changed) {
            return false;
        }

        OrbitalWeaponRecord updated = new OrbitalWeaponRecord(
                current.weaponId(),
                current.ownerId(),
                current.delegatedRoles(),
                reordered,
                current.reserve(),
                current.lifecycle(),
                current.primaryAnchor());
        this.weapons.put(weaponId, updated);
        setDirty();
        return true;
    }

    /**
     * Selects an online uplink beacon as the owner-controlled primary projection anchor. Changing an active anchor
     * enters the configured teardown/rebuild state while committed attacks continue in the attack SavedData.
     */
    public boolean selectPrimaryAnchor(
                                       MinecraftServer server,
                                       UUID actorId,
                                       UUID weaponId,
                                       OrbitalEndpointLocation location) {
        requireServerThread(server);
        OrbitalWeaponRecord current = requireWeapon(weaponId);
        if (!current.canPerform(actorId, OrbitalWeaponAction.SELECT_PRIMARY_ANCHOR)) {
            throw new SecurityException("Player " + actorId + " cannot select the primary anchor for orbital weapon " + weaponId);
        }
        OrbitalEndpointRecord endpoint = current.endpoints().get(location);
        if (endpoint == null || endpoint.kind() != OrbitalEndpointKind.UPLINK_BEACON) {
            return false;
        }
        if (!OrbitalEndpointAvailability.isOnline(server, weaponId, endpoint)) {
            return false;
        }
        if (location.equals(current.primaryAnchor())) {
            return true;
        }

        DataEnergisticsSettings.OrbitalWeapon settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon();
        OrbitalWeaponLifecycle lifecycle = current.lifecycle();
        if (lifecycle.state() == OrbitalWeaponLifecycleState.DEPLOYED || lifecycle.state() == OrbitalWeaponLifecycleState.REDEPLOYING) {
            lifecycle = lifecycle.beginRedeployment(settings.redeploymentTicks());
        }
        OrbitalWeaponRecord updated = current.withPrimaryAnchor(location).withLifecycle(lifecycle);
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
     * Returns the player's remembered weapon when it is still accessible, otherwise the first stable accessible ID.
     */
    public Optional<UUID> preferredWeaponId(UUID playerId) {
        List<OrbitalWeaponRecord> accessible = accessibleTo(playerId);
        UUID remembered = this.lastSelectedWeaponByPlayer.get(playerId);
        if (remembered != null && accessible.stream().anyMatch(weapon -> weapon.weaponId().equals(remembered))) {
            return Optional.of(remembered);
        }
        return accessible.stream().map(OrbitalWeaponRecord::weaponId).findFirst();
    }

    /**
     * Moves the player's server-side selection through the currently accessible weapons and persists the choice.
     *
     * @param forward {@code true} for the next weapon, {@code false} for the previous weapon
     * @return the newly selected weapon, or empty when the player has no accessible weapon
     */
    public Optional<UUID> selectNext(MinecraftServer server, UUID playerId, boolean forward) {
        requireServerThread(server);
        List<UUID> accessible = accessibleTo(playerId).stream()
                .map(OrbitalWeaponRecord::weaponId)
                .toList();
        if (accessible.isEmpty()) {
            if (this.lastSelectedWeaponByPlayer.remove(playerId) != null) {
                setDirty();
            }
            return Optional.empty();
        }

        UUID remembered = this.lastSelectedWeaponByPlayer.get(playerId);
        int currentIndex = accessible.indexOf(remembered);
        if (currentIndex < 0) {
            // The opening snapshot defaults to the first stable UUID even before a button is pressed.
            // Treat that implicit choice as the current position so the first Next/Previous action moves.
            currentIndex = 0;
        }
        int selectedIndex;
        selectedIndex = Math.floorMod(currentIndex + (forward ? 1 : -1), accessible.size());
        UUID selected = accessible.get(selectedIndex);
        if (!selected.equals(remembered)) {
            this.lastSelectedWeaponByPlayer.put(playerId, selected);
            setDirty();
        }
        return Optional.of(selected);
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

    /**
     * Issues a one-shot, server-owned transfer offer to an online player. The offer is valid for exactly sixty seconds
     * and is invalidated whenever the weapon gains an active attack, escrow or a non-deployed lifecycle state.
     */
    public Optional<OrbitalOwnershipTransfer> requestOwnershipTransfer(
                                                                       MinecraftServer server,
                                                                       UUID actorId,
                                                                       UUID weaponId,
                                                                       UUID recipientId) {
        requireServerThread(server);
        purgeExpiredTransfers(server);
        OrbitalWeaponRecord current = requireWeapon(weaponId);
        if (!current.canPerform(actorId, OrbitalWeaponAction.TRANSFER_OWNERSHIP)) {
            throw new SecurityException("Player " + actorId + " cannot transfer orbital weapon " + weaponId);
        }
        if (recipientId.equals(current.ownerId()) || server.getPlayerList().getPlayer(recipientId) == null || this.ownerIndex.containsKey(recipientId) || current.lifecycle().state() != OrbitalWeaponLifecycleState.DEPLOYED || OrbitalAttackSavedData.get(server).hasTransferBlockingState(weaponId)) {
            return Optional.empty();
        }
        this.ownershipTransfers.values().removeIf(offer -> offer.weaponId().equals(weaponId));
        OrbitalOwnershipTransfer offer = new OrbitalOwnershipTransfer(
                UUID.randomUUID(),
                weaponId,
                current.ownerId(),
                recipientId,
                server.overworld().getGameTime() + TRANSFER_WINDOW_TICKS);
        this.ownershipTransfers.put(offer.transferId(), offer);
        setDirty();
        return Optional.of(offer);
    }

    /** Accepts one unexpired transfer offer while rechecking every ownership and attack precondition on the server. */
    public boolean acceptOwnershipTransfer(
                                           MinecraftServer server,
                                           UUID recipientId,
                                           UUID transferId) {
        requireServerThread(server);
        purgeExpiredTransfers(server);
        OrbitalOwnershipTransfer offer = this.ownershipTransfers.get(transferId);
        if (offer == null || !offer.recipientId().equals(recipientId)) {
            return false;
        }
        OrbitalWeaponRecord current = this.weapons.get(offer.weaponId());
        if (current == null || !current.ownerId().equals(offer.currentOwnerId()) || this.ownerIndex.containsKey(recipientId) || current.lifecycle().state() != OrbitalWeaponLifecycleState.DEPLOYED || OrbitalAttackSavedData.get(server).hasTransferBlockingState(offer.weaponId())) {
            this.ownershipTransfers.remove(transferId);
            setDirty();
            return false;
        }

        HashMap<UUID, OrbitalAccessRole> roles = new HashMap<>(current.delegatedRoles());
        roles.remove(recipientId);
        roles.put(current.ownerId(), OrbitalAccessRole.OPERATOR);
        OrbitalWeaponRecord updated = new OrbitalWeaponRecord(
                current.weaponId(),
                recipientId,
                roles,
                current.endpoints(),
                current.reserve(),
                current.lifecycle(),
                current.primaryAnchor());
        removeAccessIndex(current);
        this.ownerIndex.remove(current.ownerId());
        this.ownerIndex.put(recipientId, updated.weaponId());
        this.weapons.put(updated.weaponId(), updated);
        addAccessIndex(updated);
        this.ownershipTransfers.remove(transferId);
        setDirty();
        return true;
    }

    /** Returns a pending transfer offer for an administrator or server-side UI snapshot. */
    public Optional<OrbitalOwnershipTransfer> findOwnershipTransfer(UUID transferId) {
        return Optional.ofNullable(this.ownershipTransfers.get(transferId));
    }

    /**
     * Retires an empty dormant weapon. No resource is returned and every endpoint must already have been unbound.
     */
    public boolean retire(MinecraftServer server, UUID actorId, UUID weaponId) {
        requireServerThread(server);
        OrbitalWeaponRecord current = requireWeapon(weaponId);
        if (!current.canPerform(actorId, OrbitalWeaponAction.RETIRE)) {
            throw new SecurityException("Player " + actorId + " cannot retire orbital weapon " + weaponId);
        }
        if (current.lifecycle().state() != OrbitalWeaponLifecycleState.DORMANT || !current.reserve().equals(OrbitalEnergyReserve.empty()) || !current.endpoints().isEmpty() || OrbitalAttackSavedData.get(server).hasTransferBlockingState(weaponId)) {
            return false;
        }
        removeAccessIndex(current);
        this.ownerIndex.remove(current.ownerId());
        this.weapons.remove(weaponId);
        this.ownershipTransfers.values().removeIf(offer -> offer.weaponId().equals(weaponId));
        this.lastSelectedWeaponByPlayer.values().removeIf(weaponId::equals);
        setDirty();
        return true;
    }

    /** Rebuilds owner and delegated-access indexes from the authoritative weapon records after an admin repair. */
    public int repairIndexes(MinecraftServer server) {
        requireServerThread(server);
        List<OrbitalWeaponRecord> ordered = this.weapons.values().stream()
                .sorted(Comparator.comparing(OrbitalWeaponRecord::weaponId))
                .toList();
        this.ownerIndex.clear();
        this.accessIndex.clear();
        this.endpointIndex.clear();
        LinkedHashMap<UUID, OrbitalWeaponRecord> repaired = new LinkedHashMap<>();
        int removed = 0;
        for (OrbitalWeaponRecord weapon : ordered) {
            if (this.ownerIndex.containsKey(weapon.ownerId())) {
                removed++;
                continue;
            }
            OrbitalWeaponRecord normalized = filterConflictingEndpoints(weapon);
            repaired.put(normalized.weaponId(), normalized);
            this.ownerIndex.put(normalized.ownerId(), normalized.weaponId());
            addAccessIndex(normalized);
            for (OrbitalEndpointLocation location : normalized.endpoints().keySet()) {
                this.endpointIndex.put(location, normalized.weaponId());
            }
        }
        this.weapons.clear();
        this.weapons.putAll(repaired);
        this.ownershipTransfers.entrySet().removeIf(entry -> {
            OrbitalOwnershipTransfer offer = entry.getValue();
            OrbitalWeaponRecord weapon = this.weapons.get(offer.weaponId());
            return weapon == null || !weapon.ownerId().equals(offer.currentOwnerId());
        });
        setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        OrbitalWeaponNbtCodec.save(tag, this.weapons.values());
        ListTag selections = new ListTag();
        this.lastSelectedWeaponByPlayer.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag selection = new CompoundTag();
                    selection.putUUID(PLAYER_ID_TAG, entry.getKey());
                    selection.putUUID(WEAPON_ID_TAG, entry.getValue());
                    selections.add(selection);
                });
        tag.put(LAST_SELECTED_WEAPONS_TAG, selections);
        ListTag transfers = new ListTag();
        this.ownershipTransfers.values().stream()
                .sorted(Comparator.comparing(OrbitalOwnershipTransfer::transferId))
                .forEach(offer -> {
                    CompoundTag transfer = new CompoundTag();
                    transfer.putUUID(TRANSFER_ID_TAG, offer.transferId());
                    transfer.putUUID(WEAPON_ID_TAG, offer.weaponId());
                    transfer.putUUID(PLAYER_ID_TAG, offer.currentOwnerId());
                    transfer.putUUID(RECIPIENT_ID_TAG, offer.recipientId());
                    transfer.putLong(EXPIRES_AT_TAG, offer.expiresAtGameTime());
                    transfers.add(transfer);
                });
        tag.put(TRANSFERS_TAG, transfers);
        return tag;
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
        Tag selectionTag = tag.get(LAST_SELECTED_WEAPONS_TAG);
        if (selectionTag instanceof ListTag selections) {
            for (Tag rawSelection : selections) {
                if (!(rawSelection instanceof CompoundTag selection) || !selection.hasUUID(PLAYER_ID_TAG) || !selection.hasUUID(WEAPON_ID_TAG)) {
                    continue;
                }
                UUID playerId = selection.getUUID(PLAYER_ID_TAG);
                UUID weaponId = selection.getUUID(WEAPON_ID_TAG);
                OrbitalWeaponRecord weapon = data.weapons.get(weaponId);
                if (weapon != null && (weapon.ownerId().equals(playerId) || weapon.delegatedRoles().containsKey(playerId))) {
                    data.lastSelectedWeaponByPlayer.put(playerId, weaponId);
                }
            }
        }
        Tag transferTag = tag.get(TRANSFERS_TAG);
        if (transferTag instanceof ListTag transfers) {
            for (Tag rawTransfer : transfers) {
                if (!(rawTransfer instanceof CompoundTag transfer) || !transfer.hasUUID(TRANSFER_ID_TAG) || !transfer.hasUUID(WEAPON_ID_TAG) || !transfer.hasUUID(PLAYER_ID_TAG) || !transfer.hasUUID(RECIPIENT_ID_TAG) || !transfer.contains(EXPIRES_AT_TAG, Tag.TAG_LONG)) {
                    continue;
                }
                try {
                    OrbitalOwnershipTransfer offer = new OrbitalOwnershipTransfer(
                            transfer.getUUID(TRANSFER_ID_TAG),
                            transfer.getUUID(WEAPON_ID_TAG),
                            transfer.getUUID(PLAYER_ID_TAG),
                            transfer.getUUID(RECIPIENT_ID_TAG),
                            transfer.getLong(EXPIRES_AT_TAG));
                    OrbitalWeaponRecord weapon = data.weapons.get(offer.weaponId());
                    if (weapon != null && weapon.ownerId().equals(offer.currentOwnerId())) {
                        data.ownershipTransfers.putIfAbsent(offer.transferId(), offer);
                    }
                } catch (IllegalArgumentException exception) {
                    LOGGER.warn("Ignoring invalid orbital ownership transfer", exception);
                }
            }
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

    private void addAccessIndex(OrbitalWeaponRecord weapon) {
        for (UUID playerId : weapon.delegatedRoles().keySet()) {
            this.accessIndex.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(weapon.weaponId());
        }
    }

    private void removeAccessIndex(OrbitalWeaponRecord weapon) {
        for (UUID playerId : weapon.delegatedRoles().keySet()) {
            Set<UUID> weaponIds = this.accessIndex.get(playerId);
            if (weaponIds == null) {
                continue;
            }
            weaponIds.remove(weapon.weaponId());
            if (weaponIds.isEmpty()) {
                this.accessIndex.remove(playerId);
            }
        }
    }

    private void purgeExpiredTransfers(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        if (this.ownershipTransfers.values().removeIf(offer -> offer.expired(gameTime))) {
            setDirty();
        }
    }

    private static Optional<OrbitalProjectionVisualSnapshot> projectionSnapshot(
                                                                                ServerLevel level,
                                                                                long gameTime,
                                                                                int projectionY,
                                                                                OrbitalWeaponRecord weapon) {
        OrbitalEndpointLocation anchor = weapon.primaryAnchor();
        if (anchor == null) {
            return Optional.empty();
        }
        OrbitalEndpointRecord endpoint = weapon.endpoints().get(anchor);
        if (endpoint == null || endpoint.kind() != OrbitalEndpointKind.UPLINK_BEACON || !OrbitalEndpointAvailability.isOnline(level.getServer(), weapon.weaponId(), endpoint)) {
            return Optional.empty();
        }
        long randomSeed = (weapon.weaponId().getMostSignificantBits() ^ weapon.weaponId().getLeastSignificantBits()) & Long.MAX_VALUE;
        return Optional.of(new OrbitalProjectionVisualSnapshot(
                weapon.weaponId(),
                anchor.dimensionId(),
                anchor.pos(),
                projectionY,
                weapon.lifecycle().state(),
                weapon.lifecycle().redeploymentTicksRemaining(),
                gameTime,
                randomSeed));
    }

    /** Applies the first online beacon when a newly bound endpoint has not got an anchor yet. */
    private OrbitalWeaponRecord ensurePrimaryAnchor(MinecraftServer server, OrbitalWeaponRecord weapon) {
        OrbitalWeaponRecord updated = reconcilePrimaryAnchor(
                server,
                weapon,
                DataEnergisticsConfiguration.INSTANCE.orbitalWeapon());
        if (updated != weapon) {
            this.weapons.put(updated.weaponId(), updated);
            setDirty();
        }
        return updated;
    }

    /**
     * Keeps an online primary beacon stable, and fails over once to the next online priority beacon when it becomes
     * unusable. A recovered old beacon is deliberately not selected again because the record now points at the new
     * owner-approved failover location.
     */
    private static OrbitalWeaponRecord reconcilePrimaryAnchor(
                                                              MinecraftServer server,
                                                              OrbitalWeaponRecord weapon,
                                                              DataEnergisticsSettings.OrbitalWeapon settings) {
        OrbitalEndpointRecord currentAnchor = weapon.primaryAnchor() == null ? null : weapon.endpoints().get(weapon.primaryAnchor());
        if (currentAnchor != null && currentAnchor.kind() == OrbitalEndpointKind.UPLINK_BEACON && OrbitalEndpointAvailability.isOnline(server, weapon.weaponId(), currentAnchor)) {
            return weapon;
        }

        OrbitalEndpointLocation fallback = findOnlineBeacon(server, weapon);
        OrbitalEndpointLocation oldAnchor = weapon.primaryAnchor();
        boolean sameAnchor = fallback == oldAnchor || (fallback != null && fallback.equals(oldAnchor));
        if (sameAnchor && !(fallback == null && (weapon.lifecycle().state() == OrbitalWeaponLifecycleState.DEPLOYED || weapon.lifecycle().state() == OrbitalWeaponLifecycleState.REDEPLOYING))) {
            return weapon;
        }

        OrbitalWeaponLifecycle lifecycle = weapon.lifecycle();
        if (fallback == null && (lifecycle.state() == OrbitalWeaponLifecycleState.DEPLOYED || lifecycle.state() == OrbitalWeaponLifecycleState.REDEPLOYING)) {
            lifecycle = OrbitalWeaponLifecycle.dormant();
        } else if (fallback != null && (lifecycle.state() == OrbitalWeaponLifecycleState.DEPLOYED || lifecycle.state() == OrbitalWeaponLifecycleState.REDEPLOYING)) {
            lifecycle = lifecycle.beginRedeployment(settings.redeploymentTicks());
        }
        return weapon.withPrimaryAnchor(fallback).withLifecycle(lifecycle);
    }

    private static @Nullable OrbitalEndpointLocation findOnlineBeacon(
                                                                      MinecraftServer server,
                                                                      OrbitalWeaponRecord weapon) {
        return weapon.endpoints().values().stream()
                .filter(endpoint -> endpoint.kind() == OrbitalEndpointKind.UPLINK_BEACON)
                .sorted(ENDPOINT_PRIORITY_ORDER)
                .filter(endpoint -> OrbitalEndpointAvailability.isOnline(server, weapon.weaponId(), endpoint))
                .map(endpoint -> endpoint.location())
                .findFirst()
                .orElse(null);
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
                acceptedEndpoints,
                weapon.reserve(),
                weapon.lifecycle(),
                acceptedEndpoints.containsKey(weapon.primaryAnchor()) ? weapon.primaryAnchor() : null);
    }

    /** Applies one deployed-tick maintenance debit without allowing either independent reserve to go negative. */
    private static OrbitalEnergyReserve applyDeploymentMaintenance(
                                                                   OrbitalWeaponRecord weapon,
                                                                   DataEnergisticsSettings.OrbitalWeapon settings) {
        if (weapon.lifecycle().state() != OrbitalWeaponLifecycleState.DEPLOYED) {
            return weapon.reserve();
        }
        long celestialDebit = Math.min(weapon.reserve().celestialEnergy(), settings.celestialEnergyUpkeepPerTick());
        long aeDebit = Math.min(weapon.reserve().aeEnergy(), settings.aeEnergyUpkeepPerTick());
        if (celestialDebit == 0L && aeDebit == 0L) {
            return weapon.reserve();
        }
        return new OrbitalEnergyReserve(
                weapon.reserve().celestialEnergy() - celestialDebit,
                weapon.reserve().aeEnergy() - aeDebit);
    }

    private Optional<OrbitalWeaponRecord> findCompatibleBoundEndpoint(
                                                                      UUID ownerId,
                                                                      OrbitalEndpointLocation location,
                                                                      OrbitalEndpointKind kind) {
        UUID boundWeaponId = this.endpointIndex.get(location);
        if (boundWeaponId == null) {
            return Optional.empty();
        }

        OrbitalWeaponRecord boundWeapon = requireWeapon(boundWeaponId);
        OrbitalEndpointRecord boundEndpoint = boundWeapon.endpoints().get(location);
        if (boundEndpoint == null) {
            throw new IllegalStateException("Endpoint index is inconsistent at " + location);
        }
        if (!boundWeapon.ownerId().equals(ownerId) || boundEndpoint.kind() != kind) {
            throw new IllegalStateException("Endpoint location is already bound to another orbital weapon");
        }
        return Optional.of(boundWeapon);
    }

    private OrbitalWeaponRecord addEndpoint(
                                            OrbitalWeaponRecord current,
                                            OrbitalEndpointLocation location,
                                            OrbitalEndpointKind kind) {
        if (current.endpoints().containsKey(location)) {
            throw new IllegalStateException("Endpoint index is inconsistent at " + location);
        }

        OrbitalWeaponRecord updated = current.withEndpoint(createEndpoint(current, location, kind));
        this.weapons.put(updated.weaponId(), updated);
        this.endpointIndex.put(location, updated.weaponId());
        setDirty();
        return updated;
    }

    private static OrbitalEndpointRecord createEndpoint(
                                                        OrbitalWeaponRecord weapon,
                                                        OrbitalEndpointLocation location,
                                                        OrbitalEndpointKind kind) {
        requireEndpointCapacity(weapon, location);
        return new OrbitalEndpointRecord(
                location,
                kind,
                weapon.nextEndpointPriority());
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
