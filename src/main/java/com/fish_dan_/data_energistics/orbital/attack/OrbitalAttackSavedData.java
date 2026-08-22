package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.entity.explosive.DataNukePrimedEntity;
import com.fish_dan_.data_energistics.entity.explosive.DigitalAnnihilationWork;
import com.fish_dan_.data_energistics.entity.projectile.OrbitalAnnihilatorProjectileEntity;
import com.fish_dan_.data_energistics.orbital.attack.work.OrbitalAttackWorkState;
import com.fish_dan_.data_energistics.orbital.attack.work.OrbitalTerrainWorkScheduler;
import com.fish_dan_.data_energistics.orbital.attack.work.OrbitalTerrainWorkScheduler.ChunkReadiness;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponAction;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * World-level source of truth for confirmed orbital attacks and their resumable warning, delivery and cooldown work.
 * Mutations are restricted to the server thread; all dimensions share the overworld SavedData instance.
 */
public final class OrbitalAttackSavedData extends SavedData {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String DATA_NAME = Data_Energistics.MODID + "_orbital_attacks";
    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int SCHEMA_VERSION = 1;
    private static final String ATTACKS_TAG = "attacks";
    private static final String ATTACK_ID_TAG = "attack_id";
    private static final String WEAPON_ID_TAG = "weapon_id";
    private static final String MODE_TAG = "mode";
    private static final String PHASE_TAG = "phase";
    private static final String DIMENSION_TAG = "dimension";
    private static final String TARGET_TAG = "target";
    private static final String GEOMETRY_RADIUS_TAG = "geometry_radius";
    private static final String GEOMETRY_DEPTH_TAG = "geometry_depth";
    private static final String GEOMETRY_DEPTH_BLOCKS_TAG = "geometry_depth_blocks";
    private static final String GEOMETRY_DAMAGE_TAG = "geometry_damage";
    private static final String KINETIC_COLUMN_RADIUS_TAG = "kinetic_column_radius";
    private static final String KINETIC_COLUMN_DEPTH_TAG = "kinetic_column_depth";
    private static final String KINETIC_CRATER_RADIUS_TAG = "kinetic_crater_radius";
    private static final String KINETIC_CRATER_DEPTH_TAG = "kinetic_crater_depth";
    private static final String KINETIC_SHOCKWAVE_RADIUS_TAG = "kinetic_shockwave_radius";
    private static final String KINETIC_ENTITY_DAMAGE_TAG = "kinetic_entity_damage";
    private static final String KINETIC_KNOCKBACK_STRENGTH_TAG = "kinetic_knockback_strength";
    private static final String DIGITAL_WORK_INTERVAL_TAG = "digital_work_interval";
    private static final String DIGITAL_MAX_RADIUS_TAG = "digital_max_radius";
    private static final String DIGITAL_CENTER_RADIUS_TAG = "digital_center_radius";
    private static final String CONFIGURATION_REVISION_TAG = "configuration_revision";
    private static final String WARNING_TICKS_TAG = "warning_ticks";
    private static final String WORK_CURSOR_TAG = "work_cursor";
    private static final String WORK_STATE_TAG = "work_state";
    private static final String PHASE_STARTED_AT_TAG = "phase_started_at";
    private static final String FAULT_REASON_TAG = "fault_reason";
    private static final int MAX_FAULT_REASON_LENGTH = 512;
    private static final String PAYLOAD_ENTITY_ID_TAG = "payload_entity_id";
    private static final String PAYLOAD_ARRIVED_TAG = "payload_arrived";
    private static final String IMPACT_APPLIED_TAG = "impact_applied";
    private static final String COOLDOWN_TICKS_TAG = "cooldown_ticks";
    private static final String COOLDOWN_DURATION_TAG = "cooldown_duration";
    private static final String CELESTIAL_ESCROW_TAG = "celestial_escrow";
    private static final String AE_ESCROW_TAG = "ae_escrow";
    private static final String EXEMPTIONS_TAG = "damage_exemptions";
    private static final String UUID_TAG = "uuid";
    private static final Factory<OrbitalAttackSavedData> FACTORY = new Factory<>(
            OrbitalAttackSavedData::new,
            OrbitalAttackSavedData::load);

    private final Map<UUID, OrbitalAttackRecord> attacks = new LinkedHashMap<>();
    private final Object2LongOpenHashMap<UUID> phaseStartedAt = new Object2LongOpenHashMap<>();
    private final OrbitalTerrainWorkScheduler terrainWorkScheduler = new OrbitalTerrainWorkScheduler();
    private int roundRobinOffset;

    private OrbitalAttackSavedData() {}

    /**
     * Returns the one attack store shared by every server dimension.
     */
    public static OrbitalAttackSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Returns a persisted attack by its immutable ID.
     */
    public Optional<OrbitalAttackRecord> find(UUID attackId) {
        return Optional.ofNullable(this.attacks.get(attackId));
    }

    /**
     * Returns whether an attack still blocks ownership transfer. A faulted task only blocks while its escrow remains
     * eligible for administrator recovery; a completed task's consumed cooldown follows the weapon to its new owner.
     */
    public boolean hasOwnershipTransferBlockingState(UUID weaponId) {
        return this.attacks.values().stream()
                .filter(attack -> attack.weaponId().equals(weaponId))
                .anyMatch(attack -> switch (attack.phase()) {
                    case RESERVED_WARNING, COMMITTED, DELIVERY, ABORTED -> true;
                    case COOLDOWN -> false;
                    case FAULTED -> attack.celestialEscrow() > 0L || attack.aeEscrow() > 0L;
                });
    }

    /** Returns whether any persisted attack record still prevents destructive weapon retirement. */
    public boolean hasRetirementBlockingState(UUID weaponId) {
        return this.attacks.values().stream()
                .anyMatch(attack -> attack.weaponId().equals(weaponId));
    }

    /** Retries one faulted attack from its persisted cursor without refunding its escrow. */
    public boolean retryFaulted(MinecraftServer server, UUID attackId) {
        requireServerThread(server);
        OrbitalAttackRecord current = this.attacks.get(attackId);
        if (current == null || current.phase() != OrbitalAttackPhase.FAULTED) {
            return false;
        }
        discardPayload(server, current);
        this.terrainWorkScheduler.release(server, attackId);
        replaceAttack(server, current, current.retryAfterFault());
        setDirty();
        return true;
    }

    /** Aborts an active or faulted attack for an administrator without refunding escrow. */
    public boolean adminAbort(MinecraftServer server, UUID attackId) {
        requireServerThread(server);
        OrbitalAttackRecord current = this.attacks.get(attackId);
        if (current == null || (current.phase() != OrbitalAttackPhase.COMMITTED && current.phase() != OrbitalAttackPhase.DELIVERY && current.phase() != OrbitalAttackPhase.FAULTED)) {
            return false;
        }
        discardPayload(server, current);
        this.terrainWorkScheduler.release(server, attackId);
        replaceAttack(server, current, current.aborted());
        setDirty();
        return true;
    }

    /** Explicitly refunds a faulted attack's escrow and removes its persisted task. */
    public boolean refundFaulted(MinecraftServer server, UUID attackId) {
        requireServerThread(server);
        OrbitalAttackRecord current = this.attacks.get(attackId);
        if (current == null || current.phase() != OrbitalAttackPhase.FAULTED) {
            return false;
        }
        discardPayload(server, current);
        OrbitalWeaponSavedData.get(server).refundFaultedReserve(
                server,
                current.weaponId(),
                current.celestialEscrow(),
                current.aeEscrow());
        this.terrainWorkScheduler.release(server, attackId);
        this.attacks.remove(attackId);
        this.phaseStartedAt.removeLong(attackId);
        setDirty();
        return true;
    }

    /**
     * Returns a stable read-only view of active attacks owned by one weapon for server-side UI snapshots.
     *
     * <p>
     * The caller must be on the server thread. Access control is intentionally performed by the caller against the
     * current {@link OrbitalWeaponRecord}; this method never exposes the complete attack store to a client.
     * </p>
     */
    public List<OrbitalAttackRecord> forWeapon(UUID weaponId) {
        return this.attacks.values().stream()
                .filter(attack -> attack.weaponId().equals(weaponId))
                .sorted(Comparator.comparing(OrbitalAttackRecord::attackId))
                .toList();
    }

    /** Returns only publicly visible attacks in one dimension for tactical-map marker sampling. */
    public List<OrbitalAttackRecord> publicForDimension(ResourceLocation dimensionId) {
        return this.attacks.values().stream()
                .filter(attack -> attack.dimensionId().equals(dimensionId))
                .filter(attack -> attack.phase() == OrbitalAttackPhase.RESERVED_WARNING || attack.phase() == OrbitalAttackPhase.COMMITTED || attack.phase() == OrbitalAttackPhase.DELIVERY)
                .sorted(Comparator.comparing(OrbitalAttackRecord::attackId))
                .toList();
    }

    /**
     * Builds the public render baseline for one dimension. No owner, reserve, exemption or delegated-role field is
     * included; clients only receive deterministic geometry and coarse progress.
     */
    public List<OrbitalAttackVisualSnapshot> publicVisuals(ServerLevel level, long gameTime) {
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
        ArrayList<OrbitalAttackVisualSnapshot> visuals = new ArrayList<>();
        for (OrbitalAttackRecord attack : this.attacks.values()) {
            if (!attack.dimensionId().equals(level.dimension().location()) || (attack.phase() != OrbitalAttackPhase.RESERVED_WARNING && attack.phase() != OrbitalAttackPhase.COMMITTED && attack.phase() != OrbitalAttackPhase.DELIVERY)) {
                continue;
            }
            VisualEffect visualEffect = switch (attack.mode()) {
                case KINETIC -> {
                    OrbitalAttackGeometry.Kinetic geometry = (OrbitalAttackGeometry.Kinetic) attack.geometry();
                    long totalWork = OrbitalKineticStrike.totalWork(level, attack.target(), geometry);
                    BlockPos effectPosition = OrbitalKineticStrike.workPosition(
                            level,
                            attack.target(),
                            geometry,
                            attack.workCursor());
                    yield new VisualEffect(effectPosition, geometry.shockwaveRadius(), totalWork);
                }
                case DIRECTED_ENERGY -> {
                    OrbitalAttackGeometry.DirectedEnergy geometry = (OrbitalAttackGeometry.DirectedEnergy) attack.geometry();
                    long totalWork = OrbitalDirectedEnergyStrike.totalWork(level, attack.target(), geometry);
                    BlockPos effectPosition = OrbitalDirectedEnergyStrike.workPosition(
                            level,
                            attack.target(),
                            geometry,
                            attack.workCursor());
                    yield new VisualEffect(effectPosition, geometry.radius(), totalWork);
                }
                case DIGITAL_ANNIHILATION -> {
                    OrbitalAttackGeometry.DigitalAnnihilation geometry = (OrbitalAttackGeometry.DigitalAnnihilation) attack.geometry();
                    Entity payload = attack.payloadEntityId() == null ? null : level.getEntity(attack.payloadEntityId());
                    BlockPos effectPosition = payload == null ? attack.target() : payload.blockPosition();
                    yield new VisualEffect(effectPosition, geometry.maxRadius(), Math.max(attack.workCursor(), 1L));
                }
            };
            long phaseAge = attack.phase() == OrbitalAttackPhase.RESERVED_WARNING ? Math.max(0L, settings.attackWarningTicks - attack.warningTicksRemaining()) : Math.max(0L, gameTime - this.phaseStartedAt.getOrDefault(attack.attackId(), gameTime));
            long randomSeed = (attack.attackId().getMostSignificantBits() ^ attack.attackId().getLeastSignificantBits()) & Long.MAX_VALUE;
            visuals.add(new OrbitalAttackVisualSnapshot(
                    attack.attackId(),
                    attack.mode(),
                    attack.dimensionId(),
                    attack.target(),
                    visualEffect.position(),
                    visualEffect.radius(),
                    attack.phase(),
                    phaseAge,
                    randomSeed,
                    attack.workCursor(),
                    visualEffect.totalWork()));
        }
        return visuals.stream()
                .sorted(Comparator.comparing(OrbitalAttackVisualSnapshot::attackId))
                .toList();
    }

    /**
     * Confirms a kinetic attack after rechecking permission, target bounds, an online target-dimension endpoint and
     * both reserve balances. The returned warning owns the debited escrow; an empty result leaves every store intact.
     */
    public Optional<OrbitalAttackRecord> tryConfirmKinetic(
                                                           MinecraftServer server,
                                                           UUID actorId,
                                                           UUID weaponId,
                                                           ResourceLocation dimensionId,
                                                           BlockPos target) {
        requireServerThread(server);
        DataEnergisticsConfiguration configuration = DataEnergisticsConfiguration.INSTANCE;
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = configuration.orbitalWeapon;
        if (!settings.kineticAttackEnabled) {
            return Optional.empty();
        }
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        Optional<OrbitalWeaponRecord> foundWeapon = weapons.find(weaponId);
        if (foundWeapon.isEmpty()) {
            return Optional.empty();
        }
        OrbitalWeaponRecord weapon = foundWeapon.orElseThrow();
        if (!weapon.canPerform(actorId, OrbitalWeaponAction.FIRE) || !weapon.allowsNewAttacks() || hasAttackForMode(weaponId, OrbitalAttackMode.KINETIC)) {
            return Optional.empty();
        }

        if (attackCapacityReached(settings)) {
            return Optional.empty();
        }

        OrbitalAttackGeometry.Kinetic geometry = OrbitalAttackGeometry.Kinetic.fromSettings(settings);
        ServerLevel targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (targetLevel == null || targetOutsideBounds(targetLevel, target, geometry.maximumRadius())) {
            return Optional.empty();
        }
        if (!weapons.hasOnlineEndpoint(server, weaponId, dimensionId)) {
            return Optional.empty();
        }

        OrbitalAttackCost cost = OrbitalAttackCost.kinetic(settings);
        OrbitalAttackRecord warning = OrbitalAttackRecord.warning(
                UUID.randomUUID(),
                weaponId,
                OrbitalAttackMode.KINETIC,
                dimensionId,
                target,
                geometry,
                configuration.revision(),
                settings.attackWarningTicks,
                cost,
                weapon.damageExemptionSnapshot());
        if (!weapons.tryDebitReserve(
                server,
                weaponId,
                actorId,
                cost.celestialEnergy(),
                cost.aeEnergy())) {
            return Optional.empty();
        }
        registerAttack(server, warning);
        return Optional.of(warning);
    }

    /**
     * Confirms a directed-energy scan after validating its captured radius/depth geometry and complete billing cost.
     * Missing target chunks are accepted and later generated through the bounded future-backed terrain scheduler.
     */
    public Optional<OrbitalAttackRecord> tryConfirmDirectedEnergy(
                                                                  MinecraftServer server,
                                                                  UUID actorId,
                                                                  UUID weaponId,
                                                                  ResourceLocation dimensionId,
                                                                  BlockPos target,
                                                                  int radius,
                                                                  @Nullable OrbitalDirectedEnergyDepth depth) {
        requireServerThread(server);
        DataEnergisticsConfiguration configuration = DataEnergisticsConfiguration.INSTANCE;
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = configuration.orbitalWeapon;
        if (!settings.directedEnergyAttackEnabled) {
            return Optional.empty();
        }
        if (depth == null) {
            return Optional.empty();
        }
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        Optional<OrbitalWeaponRecord> foundWeapon = weapons.find(weaponId);
        if (foundWeapon.isEmpty()) {
            return Optional.empty();
        }
        OrbitalWeaponRecord weapon = foundWeapon.orElseThrow();
        if (!weapon.canPerform(actorId, OrbitalWeaponAction.FIRE) || !weapon.allowsNewAttacks() || hasAttackForMode(weaponId, OrbitalAttackMode.DIRECTED_ENERGY)) {
            return Optional.empty();
        }
        if (attackCapacityReached(settings)) {
            return Optional.empty();
        }

        OrbitalAttackGeometry.DirectedEnergy geometry;
        try {
            OrbitalDirectedEnergyStrike.validateRadius(radius, settings);
            geometry = OrbitalAttackGeometry.DirectedEnergy.fromSettings(radius, depth, settings);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Optional.empty();
        }
        ServerLevel targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (targetLevel == null || targetOutsideBounds(targetLevel, target, radius)) {
            return Optional.empty();
        }
        if (!weapons.hasOnlineEndpoint(server, weaponId, dimensionId)) {
            return Optional.empty();
        }

        OrbitalAttackCost cost;
        try {
            cost = OrbitalAttackCost.directedEnergy(
                    settings,
                    OrbitalDirectedEnergyStrike.scheduledCoordinateCount(radius));
        } catch (ArithmeticException exception) {
            LOGGER.error("Directed-energy cost overflow for radius {}", radius, exception);
            return Optional.empty();
        }
        OrbitalAttackRecord warning = OrbitalAttackRecord.warning(
                UUID.randomUUID(),
                weaponId,
                OrbitalAttackMode.DIRECTED_ENERGY,
                dimensionId,
                target,
                geometry,
                configuration.revision(),
                settings.attackWarningTicks,
                cost,
                weapon.damageExemptionSnapshot());
        if (!weapons.tryDebitReserve(
                server,
                weaponId,
                actorId,
                cost.celestialEnergy(),
                cost.aeEnergy())) {
            return Optional.empty();
        }
        registerAttack(server, warning);
        return Optional.of(warning);
    }

    /**
     * Confirms a digital-annihilation payload after validating the target boundary and freezing the authorization
     * exemption and work-settings snapshots. The target chunk may be absent; the delivery entity owns the subsequent
     * ticket/future lifecycle instead of rejecting a valid future-generation request at confirmation time.
     */
    public Optional<OrbitalAttackRecord> tryConfirmDigitalAnnihilation(
                                                                       MinecraftServer server,
                                                                       UUID actorId,
                                                                       UUID weaponId,
                                                                       ResourceLocation dimensionId,
                                                                       BlockPos target) {
        requireServerThread(server);
        DataEnergisticsConfiguration configuration = DataEnergisticsConfiguration.INSTANCE;
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = configuration.orbitalWeapon;
        if (!settings.digitalAnnihilationAttackEnabled) {
            return Optional.empty();
        }
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        Optional<OrbitalWeaponRecord> foundWeapon = weapons.find(weaponId);
        if (foundWeapon.isEmpty()) {
            return Optional.empty();
        }
        OrbitalWeaponRecord weapon = foundWeapon.orElseThrow();
        if (!weapon.canPerform(actorId, OrbitalWeaponAction.FIRE) || !weapon.allowsNewAttacks() || hasAttackForMode(weaponId, OrbitalAttackMode.DIGITAL_ANNIHILATION)) {
            return Optional.empty();
        }
        DataEnergisticsConfiguration.DataNukeSchema dataNuke = configuration.explosives.dataNuke;
        if (attackCapacityReached(settings)) {
            return Optional.empty();
        }
        ServerLevel targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (targetLevel == null || targetOutsideBounds(targetLevel, target, dataNuke.maxRadius)) {
            return Optional.empty();
        }
        if (!weapons.hasOnlineEndpoint(server, weaponId, dimensionId)) {
            return Optional.empty();
        }

        OrbitalAttackCost cost = OrbitalAttackCost.digitalAnnihilation(settings);
        OrbitalAttackRecord warning = OrbitalAttackRecord.warning(
                UUID.randomUUID(),
                weaponId,
                OrbitalAttackMode.DIGITAL_ANNIHILATION,
                dimensionId,
                target,
                new OrbitalAttackGeometry.DigitalAnnihilation(
                        dataNuke.workIntervalTicks,
                        dataNuke.maxRadius,
                        dataNuke.centerEntityConsumeRadius),
                configuration.revision(),
                settings.attackWarningTicks,
                cost,
                weapon.damageExemptionSnapshot());
        if (!weapons.tryDebitReserve(
                server,
                weaponId,
                actorId,
                cost.celestialEnergy(),
                cost.aeEnergy())) {
            return Optional.empty();
        }
        registerAttack(server, warning);
        return Optional.of(warning);
    }

    /** Records the materialized fuse entity for a digital payload. */
    public boolean markDigitalPayloadArrived(MinecraftServer server, UUID attackId, UUID nukeEntityId) {
        requireServerThread(server);
        OrbitalAttackRecord current = this.attacks.get(attackId);
        if (current == null || current.mode() != OrbitalAttackMode.DIGITAL_ANNIHILATION || current.phase() != OrbitalAttackPhase.DELIVERY) {
            return false;
        }
        this.attacks.put(attackId, current.markDigitalPayloadArrived(nukeEntityId));
        setDirty();
        return true;
    }

    /** Moves a digital delivery into FAULTED while retaining its diagnostics and escrow. */
    public boolean markDigitalPayloadFaulted(MinecraftServer server, UUID attackId, String reason) {
        requireServerThread(server);
        OrbitalAttackRecord current = this.attacks.get(attackId);
        if (current == null || current.mode() != OrbitalAttackMode.DIGITAL_ANNIHILATION || current.phase() == OrbitalAttackPhase.COOLDOWN || current.phase() == OrbitalAttackPhase.FAULTED) {
            return false;
        }
        fault(server, current, "Digital annihilation payload failed: " + reason);
        return true;
    }

    /**
     * Cancels only a still-visible warning and atomically refunds its frozen escrow to the weapon reserve.
     */
    public boolean cancelWarning(MinecraftServer server, UUID actorId, UUID attackId) {
        requireServerThread(server);
        OrbitalAttackRecord warning = this.attacks.get(attackId);
        if (warning == null || warning.phase() != OrbitalAttackPhase.RESERVED_WARNING) {
            return false;
        }
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalWeaponRecord weapon = weapons.find(warning.weaponId()).orElse(null);
        if (weapon == null || !weapon.canPerform(actorId, OrbitalWeaponAction.CANCEL_WARNING_ATTACK)) {
            return false;
        }
        this.attacks.remove(attackId);
        this.phaseStartedAt.removeLong(attackId);
        try {
            weapons.refundWarningReserve(
                    server,
                    warning.weaponId(),
                    actorId,
                    warning.celestialEscrow(),
                    warning.aeEscrow());
        } catch (RuntimeException exception) {
            this.attacks.put(attackId, warning);
            this.phaseStartedAt.put(attackId, server.overworld().getGameTime());
            throw exception;
        }
        setDirty();
        return true;
    }

    /**
     * Aborts a committed or actively delivered attack for an authorized operator. Already debited escrow is retained;
     * any persisted projectile or fuse entity is discarded before the attack enters its diagnostic ABORTED phase.
     */
    public boolean emergencyAbort(MinecraftServer server, UUID actorId, UUID attackId) {
        requireServerThread(server);
        OrbitalAttackRecord current = this.attacks.get(attackId);
        if (current == null || (current.phase() != OrbitalAttackPhase.COMMITTED && current.phase() != OrbitalAttackPhase.DELIVERY)) {
            return false;
        }
        OrbitalWeaponRecord weapon = OrbitalWeaponSavedData.get(server).find(current.weaponId()).orElse(null);
        if (weapon == null || !weapon.canPerform(actorId, OrbitalWeaponAction.EMERGENCY_ABORT)) {
            return false;
        }
        discardPayload(server, current);
        this.terrainWorkScheduler.release(server, attackId);
        replaceAttack(server, current, current.aborted());
        setDirty();
        return true;
    }

    /**
     * Advances all persisted attacks once. This is called from the server tick event and never crosses a dimension's
     * thread boundary.
     */
    public void tick(MinecraftServer server) {
        requireServerThread(server);
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
        long gameTime = server.overworld().getGameTime();
        boolean phaseTimesChanged = this.phaseStartedAt.keySet().removeIf(attackId -> !this.attacks.containsKey(attackId));
        for (OrbitalAttackRecord attack : this.attacks.values()) {
            if (attack.phase() == OrbitalAttackPhase.RESERVED_WARNING || attack.phase() == OrbitalAttackPhase.COMMITTED || attack.phase() == OrbitalAttackPhase.DELIVERY) {
                if (!this.phaseStartedAt.containsKey(attack.attackId())) {
                    this.phaseStartedAt.put(attack.attackId(), gameTime);
                    phaseTimesChanged = true;
                }
            }
        }
        if (phaseTimesChanged) {
            setDirty();
        }
        this.terrainWorkScheduler.beginTick(server, settings);
        Set<UUID> liveSchedulerAttacks = this.attacks.values().stream()
                .filter(attack -> attack.phase() == OrbitalAttackPhase.COMMITTED || attack.phase() == OrbitalAttackPhase.DELIVERY)
                .map(OrbitalAttackRecord::attackId)
                .collect(Collectors.toUnmodifiableSet());
        this.terrainWorkScheduler.releaseMissing(server, liveSchedulerAttacks);

        List<OrbitalAttackRecord> snapshot = List.copyOf(this.attacks.values());
        if (snapshot.isEmpty()) {
            this.roundRobinOffset = 0;
            return;
        }
        int start = Math.floorMod(this.roundRobinOffset, snapshot.size());
        this.roundRobinOffset = (start + 1) % snapshot.size();
        for (int index = 0; index < snapshot.size(); index++) {
            OrbitalAttackRecord current = snapshot.get((start + index) % snapshot.size());
            tickAttack(server, current);
        }
    }

    /** Isolates one persisted attack so a failed worker cannot stop the remaining round-robin tasks. */
    private void tickAttack(MinecraftServer server, OrbitalAttackRecord current) {
        try {
            switch (current.phase()) {
                case RESERVED_WARNING -> tickWarning(server, current);
                case COMMITTED, DELIVERY -> tickDelivery(server, current);
                case ABORTED -> tickAborted(server, current);
                case COOLDOWN -> tickCooldown(server, current);
                case FAULTED -> {
                    // A faulted attack is retained for administrator diagnostics and is never retried implicitly.
                }
            }
        } catch (RuntimeException exception) {
            faultAfterWorkerException(server, current, exception);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(SCHEMA_VERSION_TAG, SCHEMA_VERSION);
        ListTag attackList = new ListTag();
        this.attacks.values()
                .stream()
                .map(attack -> writeAttack(attack, requirePhaseStartedAt(attack.attackId())))
                .forEach(attackList::add);
        tag.put(ATTACKS_TAG, attackList);
        return tag;
    }

    private static OrbitalAttackSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        OrbitalAttackSavedData data = new OrbitalAttackSavedData();
        if (!tag.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT) || tag.getInt(SCHEMA_VERSION_TAG) != SCHEMA_VERSION) {
            LOGGER.warn("Ignoring orbital attack SavedData with an unsupported schema");
            return data;
        }
        Tag rawAttacks = tag.get(ATTACKS_TAG);
        if (!(rawAttacks instanceof ListTag attackList)) {
            LOGGER.warn("Ignoring orbital attack SavedData without its attack list");
            return data;
        }
        for (Tag rawAttack : attackList) {
            if (!(rawAttack instanceof CompoundTag attackTag)) {
                continue;
            }
            OrbitalAttackRecord attack = readAttack(attackTag);
            if (attack == null || data.attacks.putIfAbsent(attack.attackId(), attack) != null) {
                LOGGER.warn("Ignoring invalid or duplicate orbital attack record");
            } else {
                data.phaseStartedAt.put(
                        attack.attackId(),
                        attackTag.getLong(PHASE_STARTED_AT_TAG));
            }
        }
        return data;
    }

    private long requirePhaseStartedAt(UUID attackId) {
        if (!this.phaseStartedAt.containsKey(attackId)) {
            throw new IllegalStateException("Orbital attack has no persisted phase start: " + attackId);
        }
        return this.phaseStartedAt.getLong(attackId);
    }

    private static CompoundTag writeAttack(OrbitalAttackRecord attack, long phaseStartedAt) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ATTACK_ID_TAG, attack.attackId());
        tag.putUUID(WEAPON_ID_TAG, attack.weaponId());
        tag.putString(MODE_TAG, attack.mode().name());
        tag.putString(PHASE_TAG, attack.phase().name());
        tag.putLong(PHASE_STARTED_AT_TAG, Math.max(0L, phaseStartedAt));
        tag.putString(DIMENSION_TAG, attack.dimensionId().toString());
        tag.put(TARGET_TAG, NbtUtils.writeBlockPos(attack.target()));
        switch (attack.geometry()) {
            case OrbitalAttackGeometry.Kinetic kinetic -> {
                tag.putInt(KINETIC_COLUMN_RADIUS_TAG, kinetic.columnRadius());
                tag.putInt(KINETIC_COLUMN_DEPTH_TAG, kinetic.columnDepth());
                tag.putInt(KINETIC_CRATER_RADIUS_TAG, kinetic.craterRadius());
                tag.putInt(KINETIC_CRATER_DEPTH_TAG, kinetic.craterDepth());
                tag.putInt(KINETIC_SHOCKWAVE_RADIUS_TAG, kinetic.shockwaveRadius());
                tag.putLong(KINETIC_ENTITY_DAMAGE_TAG, kinetic.entityDamage());
                tag.putDouble(KINETIC_KNOCKBACK_STRENGTH_TAG, kinetic.knockbackStrength());
            }
            case OrbitalAttackGeometry.DirectedEnergy directedEnergy -> {
                tag.putInt(GEOMETRY_RADIUS_TAG, directedEnergy.radius());
                tag.putString(GEOMETRY_DEPTH_TAG, directedEnergy.depth().name());
                tag.putInt(GEOMETRY_DEPTH_BLOCKS_TAG, directedEnergy.depthBlocks());
                tag.putLong(GEOMETRY_DAMAGE_TAG, directedEnergy.entityDamage());
            }
            case OrbitalAttackGeometry.DigitalAnnihilation digital -> {
                tag.putInt(DIGITAL_WORK_INTERVAL_TAG, digital.workIntervalTicks());
                tag.putInt(DIGITAL_MAX_RADIUS_TAG, digital.maxRadius());
                tag.putDouble(DIGITAL_CENTER_RADIUS_TAG, digital.centerEntityConsumeRadius());
            }
        }
        tag.putLong(CONFIGURATION_REVISION_TAG, attack.configurationRevision());
        tag.putInt(WARNING_TICKS_TAG, attack.warningTicksRemaining());
        tag.putLong(WORK_CURSOR_TAG, attack.workCursor());
        tag.putString(WORK_STATE_TAG, attack.workState().name());
        if (attack.faultReason() != null) {
            tag.putString(FAULT_REASON_TAG, attack.faultReason());
        }
        if (attack.payloadEntityId() != null) {
            tag.putUUID(PAYLOAD_ENTITY_ID_TAG, attack.payloadEntityId());
        }
        tag.putBoolean(PAYLOAD_ARRIVED_TAG, attack.payloadArrived());
        tag.putBoolean(IMPACT_APPLIED_TAG, attack.impactApplied());
        tag.putInt(COOLDOWN_TICKS_TAG, attack.cooldownTicksRemaining());
        tag.putInt(COOLDOWN_DURATION_TAG, attack.cooldownDurationTicks());
        tag.putLong(CELESTIAL_ESCROW_TAG, attack.celestialEscrow());
        tag.putLong(AE_ESCROW_TAG, attack.aeEscrow());
        ListTag exemptionList = new ListTag();
        attack.damageExemptions().stream().sorted().forEach(uuid -> {
            CompoundTag exemption = new CompoundTag();
            exemption.putUUID(UUID_TAG, uuid);
            exemptionList.add(exemption);
        });
        tag.put(EXEMPTIONS_TAG, exemptionList);
        return tag;
    }

    @Nullable
    private static OrbitalAttackRecord readAttack(CompoundTag tag) {
        if (!hasCurrentAttackFields(tag)) {
            return null;
        }
        try {
            ResourceLocation dimensionId = ResourceLocation.parse(tag.getString(DIMENSION_TAG));
            BlockPos target = NbtUtils.readBlockPos(tag, TARGET_TAG).orElse(null);
            if (target == null) {
                return null;
            }
            OrbitalAttackMode mode = OrbitalAttackMode.valueOf(tag.getString(MODE_TAG));
            OrbitalAttackPhase phase = OrbitalAttackPhase.valueOf(tag.getString(PHASE_TAG));
            OrbitalAttackGeometry geometry;
            switch (mode) {
                case KINETIC -> geometry = readKineticGeometry(tag);
                case DIRECTED_ENERGY -> geometry = readDirectedEnergyGeometry(tag);
                case DIGITAL_ANNIHILATION -> geometry = readDigitalAnnihilationGeometry(tag);
                default -> throw new IllegalArgumentException("Unsupported orbital attack mode");
            }
            Set<UUID> exemptions = readDamageExemptions(tag);
            if (exemptions == null) {
                return null;
            }
            return new OrbitalAttackRecord(
                    tag.getUUID(ATTACK_ID_TAG),
                    tag.getUUID(WEAPON_ID_TAG),
                    mode,
                    phase,
                    dimensionId,
                    target,
                    geometry,
                    tag.getLong(CONFIGURATION_REVISION_TAG),
                    tag.getInt(WARNING_TICKS_TAG),
                    tag.getLong(WORK_CURSOR_TAG),
                    OrbitalAttackWorkState.valueOf(tag.getString(WORK_STATE_TAG)),
                    readFaultReason(tag),
                    tag.hasUUID(PAYLOAD_ENTITY_ID_TAG) ? tag.getUUID(PAYLOAD_ENTITY_ID_TAG) : null,
                    tag.getBoolean(PAYLOAD_ARRIVED_TAG),
                    tag.getBoolean(IMPACT_APPLIED_TAG),
                    tag.getInt(COOLDOWN_TICKS_TAG),
                    tag.getInt(COOLDOWN_DURATION_TAG),
                    tag.getLong(CELESTIAL_ESCROW_TAG),
                    tag.getLong(AE_ESCROW_TAG),
                    exemptions);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean hasCurrentAttackFields(CompoundTag tag) {
        return tag.hasUUID(ATTACK_ID_TAG) && tag.hasUUID(WEAPON_ID_TAG) && tag.contains(MODE_TAG, Tag.TAG_STRING) && tag.contains(PHASE_TAG, Tag.TAG_STRING) && tag.contains(PHASE_STARTED_AT_TAG, Tag.TAG_LONG) && tag.getLong(PHASE_STARTED_AT_TAG) >= 0L && tag.contains(DIMENSION_TAG, Tag.TAG_STRING) && tag.contains(TARGET_TAG, Tag.TAG_INT_ARRAY) && tag.contains(CONFIGURATION_REVISION_TAG, Tag.TAG_LONG) && tag.contains(WARNING_TICKS_TAG, Tag.TAG_INT) && tag.contains(WORK_CURSOR_TAG, Tag.TAG_LONG) && tag.contains(WORK_STATE_TAG, Tag.TAG_STRING) && (!tag.contains(FAULT_REASON_TAG) || tag.contains(FAULT_REASON_TAG, Tag.TAG_STRING)) && (!tag.contains(PAYLOAD_ENTITY_ID_TAG) || tag.hasUUID(PAYLOAD_ENTITY_ID_TAG)) && tag.contains(PAYLOAD_ARRIVED_TAG, Tag.TAG_BYTE) && tag.contains(IMPACT_APPLIED_TAG, Tag.TAG_BYTE) && tag.contains(COOLDOWN_TICKS_TAG, Tag.TAG_INT) && tag.contains(COOLDOWN_DURATION_TAG, Tag.TAG_INT) && tag.contains(CELESTIAL_ESCROW_TAG, Tag.TAG_LONG) && tag.contains(AE_ESCROW_TAG, Tag.TAG_LONG) && tag.contains(EXEMPTIONS_TAG, Tag.TAG_LIST);
    }

    private static @Nullable Set<UUID> readDamageExemptions(CompoundTag tag) {
        Tag rawExemptions = tag.get(EXEMPTIONS_TAG);
        if (!(rawExemptions instanceof ListTag exemptionList)) {
            return null;
        }
        HashSet<UUID> exemptions = new HashSet<>();
        for (Tag rawExemption : exemptionList) {
            if (!(rawExemption instanceof CompoundTag exemption) || !exemption.hasUUID(UUID_TAG) || !exemptions.add(exemption.getUUID(UUID_TAG))) {
                return null;
            }
        }
        return Set.copyOf(exemptions);
    }

    private static OrbitalAttackGeometry.Kinetic readKineticGeometry(CompoundTag tag) {
        boolean hasCompleteGeometry = tag.contains(KINETIC_COLUMN_RADIUS_TAG, Tag.TAG_INT) && tag.contains(KINETIC_COLUMN_DEPTH_TAG, Tag.TAG_INT) && tag.contains(KINETIC_CRATER_RADIUS_TAG, Tag.TAG_INT) && tag.contains(KINETIC_CRATER_DEPTH_TAG, Tag.TAG_INT) && tag.contains(KINETIC_SHOCKWAVE_RADIUS_TAG, Tag.TAG_INT) && tag.contains(KINETIC_ENTITY_DAMAGE_TAG, Tag.TAG_LONG) && tag.contains(KINETIC_KNOCKBACK_STRENGTH_TAG, Tag.TAG_DOUBLE);
        if (!hasCompleteGeometry) {
            throw new IllegalArgumentException("Incomplete persisted kinetic attack geometry");
        }
        return OrbitalAttackGeometry.Kinetic.fromPersisted(
                tag.getInt(KINETIC_COLUMN_RADIUS_TAG),
                tag.getInt(KINETIC_COLUMN_DEPTH_TAG),
                tag.getInt(KINETIC_CRATER_RADIUS_TAG),
                tag.getInt(KINETIC_CRATER_DEPTH_TAG),
                tag.getInt(KINETIC_SHOCKWAVE_RADIUS_TAG),
                tag.getLong(KINETIC_ENTITY_DAMAGE_TAG),
                tag.getDouble(KINETIC_KNOCKBACK_STRENGTH_TAG));
    }

    private static OrbitalAttackGeometry.DirectedEnergy readDirectedEnergyGeometry(CompoundTag tag) {
        if (!tag.contains(GEOMETRY_RADIUS_TAG, Tag.TAG_INT) || !tag.contains(GEOMETRY_DEPTH_TAG, Tag.TAG_STRING) || !tag.contains(GEOMETRY_DEPTH_BLOCKS_TAG, Tag.TAG_INT) || !tag.contains(GEOMETRY_DAMAGE_TAG, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Incomplete persisted directed-energy geometry");
        }
        OrbitalDirectedEnergyDepth depth = OrbitalDirectedEnergyDepth.valueOf(tag.getString(GEOMETRY_DEPTH_TAG));
        return OrbitalAttackGeometry.DirectedEnergy.fromPersisted(
                tag.getInt(GEOMETRY_RADIUS_TAG),
                depth,
                tag.getInt(GEOMETRY_DEPTH_BLOCKS_TAG),
                tag.getLong(GEOMETRY_DAMAGE_TAG));
    }

    private static OrbitalAttackGeometry.DigitalAnnihilation readDigitalAnnihilationGeometry(
                                                                                             CompoundTag tag) {
        if (!tag.contains(DIGITAL_WORK_INTERVAL_TAG, Tag.TAG_INT) || !tag.contains(DIGITAL_MAX_RADIUS_TAG, Tag.TAG_INT) || !tag.contains(DIGITAL_CENTER_RADIUS_TAG, Tag.TAG_DOUBLE)) {
            throw new IllegalArgumentException("Incomplete persisted digital-annihilation geometry");
        }
        DigitalAnnihilationWork.Settings persistedSettings = DigitalAnnihilationWork.Settings.fromPersisted(
                tag.getInt(DIGITAL_WORK_INTERVAL_TAG),
                tag.getInt(DIGITAL_MAX_RADIUS_TAG),
                tag.getDouble(DIGITAL_CENTER_RADIUS_TAG));
        return new OrbitalAttackGeometry.DigitalAnnihilation(
                persistedSettings.workIntervalTicks(),
                persistedSettings.maxRadius(),
                persistedSettings.centerEntityConsumeRadius());
    }

    @Nullable
    private static String readFaultReason(CompoundTag tag) {
        if (!tag.contains(FAULT_REASON_TAG, Tag.TAG_STRING)) {
            return null;
        }
        String reason = tag.getString(FAULT_REASON_TAG);
        return reason.length() <= MAX_FAULT_REASON_LENGTH ? reason : reason.substring(0, MAX_FAULT_REASON_LENGTH);
    }

    private void tickWarning(MinecraftServer server, OrbitalAttackRecord current) {
        if (current.warningTicksRemaining() > 1) {
            this.attacks.put(current.attackId(), current.withWarningTicks(current.warningTicksRemaining() - 1));
            setDirty();
            return;
        }
        OrbitalAttackRecord committed = current.committed();
        this.attacks.put(current.attackId(), committed);
        this.phaseStartedAt.put(current.attackId(), server.overworld().getGameTime());
        setDirty();
        tickDelivery(server, committed);
    }

    private void tickDelivery(MinecraftServer server, OrbitalAttackRecord current) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, current.dimensionId());
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            fault(server, current, "Target dimension or attack mode is unavailable");
            return;
        }
        switch (current.mode()) {
            case KINETIC -> tickKineticDelivery(server, level, current);
            case DIRECTED_ENERGY -> tickDirectedEnergyDelivery(server, level, current);
            case DIGITAL_ANNIHILATION -> tickDigitalAnnihilationDelivery(server, level, current);
        }
    }

    private void tickDigitalAnnihilationDelivery(
                                                 MinecraftServer server,
                                                 ServerLevel level,
                                                 OrbitalAttackRecord current) {
        if (!(current.geometry() instanceof OrbitalAttackGeometry.DigitalAnnihilation geometry)) {
            fault(server, current, "Digital-annihilation attack has incompatible geometry");
            return;
        }
        if (targetOutsideBounds(level, current.target(), geometry.maxRadius())) {
            fault(server, current, "Digital-annihilation target is no longer fully inside the world boundary");
            return;
        }
        ChunkReadiness targetReadiness = this.terrainWorkScheduler.pinChunk(
                level,
                current.attackId(),
                new ChunkPos(current.target()));
        if (targetReadiness != ChunkReadiness.READY) {
            handleTerrainBoundary(server, current, targetReadiness, "Digital-annihilation target chunk failed");
            return;
        }

        if (current.payloadEntityId() == null) {
            spawnDigitalProjectile(server, level, current, geometry);
            return;
        }

        Entity payload = level.getEntity(current.payloadEntityId());
        if (current.payloadArrived()) {
            if (payload instanceof DataNukePrimedEntity nuke && current.attackId().equals(nuke.orbitalAttackId())) {
                if (nuke.isActive()) {
                    tickDigitalAnnihilationWork(server, level, current, nuke);
                }
                return;
            }
            if (payload != null) {
                fault(server, current, "Digital-annihilation fuse entity was replaced unexpectedly");
                return;
            }
            if (reconcileLoadedDigitalNuke(server, level, current)) {
                return;
            }
            spawnDigitalFuse(server, level, current, geometry);
            return;
        }

        if (payload instanceof OrbitalAnnihilatorProjectileEntity projectile && current.attackId().equals(projectile.attackId())) {
            return;
        }
        if (payload instanceof DataNukePrimedEntity nuke && current.attackId().equals(nuke.orbitalAttackId())) {
            this.attacks.put(current.attackId(), current.markDigitalPayloadArrived(nuke.getUUID()));
            setDirty();
            return;
        }
        if (payload == null) {
            if (reconcileLoadedDigitalProjectile(server, level, current)) {
                return;
            }
            spawnDigitalProjectile(server, level, current, geometry);
        } else {
            fault(server, current, "Digital-annihilation payload entity was replaced unexpectedly");
        }
    }

    private void spawnDigitalProjectile(
                                        MinecraftServer server,
                                        ServerLevel level,
                                        OrbitalAttackRecord current,
                                        OrbitalAttackGeometry.DigitalAnnihilation geometry) {
        OrbitalAnnihilatorProjectileEntity projectile = new OrbitalAnnihilatorProjectileEntity(
                level,
                current.attackId(),
                current.target(),
                current.damageExemptions(),
                geometry.workIntervalTicks(),
                geometry.maxRadius(),
                geometry.centerEntityConsumeRadius());
        if (!level.addFreshEntity(projectile)) {
            fault(server, current, "Digital-annihilation payload could not be added to the target dimension");
            return;
        }
        this.attacks.put(current.attackId(), current.withPayloadEntity(projectile.getUUID(), false));
        setDirty();
    }

    private void spawnDigitalFuse(
                                  MinecraftServer server,
                                  ServerLevel level,
                                  OrbitalAttackRecord current,
                                  OrbitalAttackGeometry.DigitalAnnihilation geometry) {
        DataNukePrimedEntity nuke = DataNukePrimedEntity.createOrbitalPayload(
                level,
                current.target(),
                current.attackId(),
                current.damageExemptions(),
                new DigitalAnnihilationWork.Settings(
                        geometry.workIntervalTicks(),
                        geometry.maxRadius(),
                        geometry.centerEntityConsumeRadius()));
        if (!level.addFreshEntity(nuke)) {
            fault(server, current, "Digital-annihilation fuse could not be restored in the target dimension");
            return;
        }
        this.attacks.put(current.attackId(), current.markDigitalPayloadArrived(nuke.getUUID()));
        setDirty();
    }

    /** Advances one active orbital fuse through the shared chunk and mutation governor. */
    private void tickDigitalAnnihilationWork(
                                             MinecraftServer server,
                                             ServerLevel level,
                                             OrbitalAttackRecord current,
                                             DataNukePrimedEntity nuke) {
        try {
            int mutationBudget = this.terrainWorkScheduler.reserveMutationBudget(current.attackId());
            DigitalAnnihilationWork.TickResult result = nuke.tickOrbitalWork(
                    mutationBudget,
                    chunk -> this.terrainWorkScheduler.prepareChunk(level, current.attackId(), chunk) == ChunkReadiness.READY);
            if (mutationBudget > 0) {
                this.terrainWorkScheduler.settleMutationBudget(
                        current.attackId(),
                        mutationBudget,
                        result.visitedBlocks());
            }
            ChunkReadiness readiness = this.terrainWorkScheduler.lastReadiness(current.attackId());
            if (result.state() == DigitalAnnihilationWork.State.FAULTED || readiness == ChunkReadiness.FAULTED) {
                String reason = nuke.orbitalWorkFailure();
                nuke.discard();
                markDigitalPayloadFaulted(server, current.attackId(), reason == null ? "work fault" : reason);
                return;
            }
            if (result.state() == DigitalAnnihilationWork.State.FINISHED) {
                completeDigitalPayload(server, current);
                nuke.discard();
                return;
            }

            OrbitalAttackWorkState workState = switch (result.state()) {
                case WAITING -> OrbitalAttackWorkState.WORKING;
                case WAITING_FOR_CHUNK -> persistedWorkState(readiness);
                case WORKING, SHELL_COMPLETED -> mutationBudget == 0 ? OrbitalAttackWorkState.WAITING_FOR_BUDGET : OrbitalAttackWorkState.WORKING;
                case FINISHED, FAULTED -> throw new IllegalStateException("Digital work state was handled before persistence");
            };
            updateAttack(
                    server,
                    current,
                    current.withWork(nuke.orbitalWorkCursor(), workState));
        } finally {
            this.terrainWorkScheduler.endTaskTick(server, current.attackId());
        }
    }

    /** Completes the scheduler-owned payload after its resumable terrain work has finished. */
    private void completeDigitalPayload(MinecraftServer server, OrbitalAttackRecord current) {
        this.terrainWorkScheduler.release(server, current.attackId());
        replaceAttack(server, current, current.cooldown(current.cooldownDurationTicks()));
        setDirty();
    }

    private boolean reconcileLoadedDigitalProjectile(
                                                     MinecraftServer server,
                                                     ServerLevel level,
                                                     OrbitalAttackRecord current) {
        ChunkPos targetChunk = new ChunkPos(current.target());
        if (level.getChunkSource().getChunkNow(targetChunk.x, targetChunk.z) == null) {
            return false;
        }
        List<OrbitalAnnihilatorProjectileEntity> matches = level.getEntitiesOfClass(
                OrbitalAnnihilatorProjectileEntity.class,
                digitalPayloadSearchBounds(level, current.target()),
                projectile -> current.attackId().equals(projectile.attackId()));
        if (matches.size() > 1) {
            fault(server, current, "Multiple digital-annihilation projectiles claimed the same attack");
            return true;
        }
        if (matches.isEmpty()) {
            return false;
        }
        this.attacks.put(current.attackId(), current.withPayloadEntity(matches.getFirst().getUUID(), false));
        setDirty();
        return true;
    }

    private boolean reconcileLoadedDigitalNuke(
                                               MinecraftServer server,
                                               ServerLevel level,
                                               OrbitalAttackRecord current) {
        ChunkPos targetChunk = new ChunkPos(current.target());
        if (level.getChunkSource().getChunkNow(targetChunk.x, targetChunk.z) == null) {
            return false;
        }
        List<DataNukePrimedEntity> matches = level.getEntitiesOfClass(
                DataNukePrimedEntity.class,
                digitalPayloadSearchBounds(level, current.target()),
                nuke -> current.attackId().equals(nuke.orbitalAttackId()));
        if (matches.size() > 1) {
            fault(server, current, "Multiple digital-annihilation fuses claimed the same attack");
            return true;
        }
        if (matches.isEmpty()) {
            return false;
        }
        this.attacks.put(current.attackId(), current.markDigitalPayloadArrived(matches.getFirst().getUUID()));
        setDirty();
        return true;
    }

    private static AABB digitalPayloadSearchBounds(ServerLevel level, BlockPos target) {
        ChunkPos chunk = new ChunkPos(target);
        return new AABB(
                chunk.getMinBlockX(),
                level.getMinBuildHeight(),
                chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1.0D,
                OrbitalAnnihilatorProjectileEntity.startY(level) + 2.0D,
                chunk.getMaxBlockZ() + 1.0D);
    }

    private void tickKineticDelivery(
                                     MinecraftServer server,
                                     ServerLevel level,
                                     OrbitalAttackRecord current) {
        try {
            OrbitalAttackGeometry.Kinetic geometry = (OrbitalAttackGeometry.Kinetic) current.geometry();
            OrbitalAttackRecord delivery = current;
            if (!delivery.impactApplied()) {
                ChunkReadiness impactReadiness = this.terrainWorkScheduler.prepareChunk(
                        level,
                        current.attackId(),
                        new ChunkPos(current.target()));
                if (impactReadiness != ChunkReadiness.READY) {
                    handleTerrainBoundary(server, delivery, impactReadiness, "Kinetic impact chunk failed");
                    return;
                }
                OrbitalKineticStrike.applyImpactDamage(
                        level,
                        delivery.target(),
                        geometry,
                        delivery.damageExemptions());
                delivery = delivery.markImpactApplied();
            }

            int mutationBudget = this.terrainWorkScheduler.reserveMutationBudget(current.attackId());
            if (mutationBudget <= 0) {
                updateAttack(server, delivery, delivery.withWorkState(OrbitalAttackWorkState.WAITING_FOR_BUDGET));
                return;
            }
            long previousCursor = delivery.workCursor();
            OrbitalKineticStrike.WorkSlice slice = OrbitalKineticStrike.applyBudget(
                    level,
                    delivery.target(),
                    geometry,
                    previousCursor,
                    mutationBudget,
                    chunk -> this.terrainWorkScheduler.prepareChunk(level, current.attackId(), chunk) == ChunkReadiness.READY);
            int visited = Math.toIntExact(slice.nextCursor() - previousCursor);
            this.terrainWorkScheduler.settleMutationBudget(current.attackId(), mutationBudget, visited);
            ChunkReadiness readiness = this.terrainWorkScheduler.lastReadiness(current.attackId());
            if (slice.waitingForChunk() && readiness == ChunkReadiness.FAULTED) {
                handleTerrainBoundary(server, delivery, readiness, "Kinetic terrain chunk failed");
                return;
            }
            OrbitalAttackWorkState workState = slice.waitingForChunk() ? persistedWorkState(readiness) : OrbitalAttackWorkState.WORKING;
            OrbitalAttackRecord updated = delivery.withWork(slice.nextCursor(), workState);
            if (slice.complete()) {
                this.terrainWorkScheduler.release(server, current.attackId());
                updated = updated.cooldown(current.cooldownDurationTicks());
            }
            updateAttack(server, current, updated);
        } finally {
            this.terrainWorkScheduler.endTaskTick(server, current.attackId());
        }
    }

    private void tickDirectedEnergyDelivery(
                                            MinecraftServer server,
                                            ServerLevel level,
                                            OrbitalAttackRecord current) {
        OrbitalAttackGeometry.DirectedEnergy geometry = (OrbitalAttackGeometry.DirectedEnergy) current.geometry();
        try {
            int mutationBudget = this.terrainWorkScheduler.reserveMutationBudget(current.attackId());
            if (mutationBudget <= 0) {
                updateAttack(server, current, current.withWorkState(OrbitalAttackWorkState.WAITING_FOR_BUDGET));
                return;
            }
            long previousCursor = current.workCursor();
            OrbitalDirectedEnergyStrike.WorkSlice slice = OrbitalDirectedEnergyStrike.applyBudget(
                    level,
                    current.target(),
                    geometry,
                    current.workCursor(),
                    current.damageExemptions(),
                    mutationBudget,
                    chunk -> this.terrainWorkScheduler.prepareChunk(level, current.attackId(), chunk) == ChunkReadiness.READY);
            int visited = Math.toIntExact(slice.nextCursor() - previousCursor);
            this.terrainWorkScheduler.settleMutationBudget(current.attackId(), mutationBudget, visited);
            ChunkReadiness readiness = this.terrainWorkScheduler.lastReadiness(current.attackId());
            if (slice.waitingForChunk() && readiness == ChunkReadiness.FAULTED) {
                handleTerrainBoundary(server, current, readiness, "Directed-energy terrain chunk failed");
                return;
            }
            OrbitalAttackWorkState workState = slice.waitingForChunk() ? persistedWorkState(readiness) : OrbitalAttackWorkState.WORKING;
            OrbitalAttackRecord updated = current.withWork(slice.nextCursor(), workState);
            if (slice.complete()) {
                this.terrainWorkScheduler.release(server, current.attackId());
                updated = updated.cooldown(current.cooldownDurationTicks());
            }
            updateAttack(server, current, updated);
        } finally {
            this.terrainWorkScheduler.endTaskTick(server, current.attackId());
        }
    }

    /**
     * Releases transient attack tickets and future permits before server dimensions stop.
     */
    public void releaseRuntimeResources(MinecraftServer server) {
        requireServerThread(server);
        this.terrainWorkScheduler.releaseAll(server);
    }

    private void tickCooldown(MinecraftServer server, OrbitalAttackRecord current) {
        this.terrainWorkScheduler.release(server, current.attackId());
        if (current.cooldownTicksRemaining() <= 1) {
            this.attacks.remove(current.attackId());
            this.phaseStartedAt.removeLong(current.attackId());
        } else {
            this.attacks.put(current.attackId(), current.withCooldownTicks(current.cooldownTicksRemaining() - 1));
        }
        setDirty();
    }

    private void tickAborted(MinecraftServer server, OrbitalAttackRecord current) {
        this.terrainWorkScheduler.release(server, current.attackId());
        replaceAttack(server, current, current.cooldown(current.cooldownDurationTicks()));
        setDirty();
    }

    private static void discardPayload(MinecraftServer server, OrbitalAttackRecord attack) {
        UUID payloadId = attack.payloadEntityId();
        if (payloadId == null) {
            return;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, attack.dimensionId());
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return;
        }
        Entity payload = level.getEntity(payloadId);
        if (payload != null) {
            payload.discard();
        }
    }

    private void fault(MinecraftServer server, OrbitalAttackRecord current, String reason) {
        discardPayload(server, current);
        this.terrainWorkScheduler.release(server, current.attackId());
        LOGGER.error("Orbital attack {} entered FAULTED: {}", current.attackId(), reason);
        replaceAttack(server, current, current.faulted(reason));
        setDirty();
    }

    private void faultAfterWorkerException(
                                           MinecraftServer server,
                                           OrbitalAttackRecord snapshot,
                                           RuntimeException exception) {
        OrbitalAttackRecord current = this.attacks.get(snapshot.attackId());
        if (current == null) {
            logWorkerException(snapshot, exception);
            return;
        }
        try {
            discardPayload(server, current);
        } catch (RuntimeException cleanupException) {
            exception.addSuppressed(cleanupException);
        }
        try {
            this.terrainWorkScheduler.release(server, current.attackId());
        } catch (RuntimeException cleanupException) {
            exception.addSuppressed(cleanupException);
        }
        replaceAttack(server, current, current.faulted(workerFailureReason(exception)));
        setDirty();
        logWorkerException(current, exception);
    }

    private static void logWorkerException(OrbitalAttackRecord attack, RuntimeException exception) {
        LOGGER.error(
                "Orbital attack worker failed: weaponId={}, attackId={}, mode={}, dimension={}, target={}, cursor={}",
                attack.weaponId(),
                attack.attackId(),
                attack.mode(),
                attack.dimensionId(),
                attack.target(),
                attack.workCursor(),
                exception);
    }

    private static String workerFailureReason(RuntimeException exception) {
        String message = exception.getMessage();
        String reason = message == null || message.isBlank() ? exception.getClass().getSimpleName() : exception.getClass().getSimpleName() + ": " + message.replace('\r', ' ').replace('\n', ' ');
        return reason.length() <= MAX_FAULT_REASON_LENGTH ? reason : reason.substring(0, MAX_FAULT_REASON_LENGTH);
    }

    private boolean hasAttackForMode(UUID weaponId, OrbitalAttackMode mode) {
        return this.attacks.values().stream()
                .anyMatch(attack -> attack.weaponId().equals(weaponId) && attack.mode() == mode);
    }

    private boolean attackCapacityReached(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        long occupiedTasks = this.attacks.values().stream()
                .filter(OrbitalAttackSavedData::occupiesWorkSlot)
                .count();
        return occupiedTasks >= settings.maxCommittedAttackTasks;
    }

    private static boolean occupiesWorkSlot(OrbitalAttackRecord attack) {
        return switch (attack.phase()) {
            case RESERVED_WARNING, COMMITTED, DELIVERY -> true;
            case ABORTED, COOLDOWN, FAULTED -> false;
        };
    }

    private void registerAttack(MinecraftServer server, OrbitalAttackRecord attack) {
        this.attacks.put(attack.attackId(), attack);
        this.phaseStartedAt.put(attack.attackId(), server.overworld().getGameTime());
        setDirty();
    }

    private void replaceAttack(
                               MinecraftServer server,
                               OrbitalAttackRecord current,
                               OrbitalAttackRecord updated) {
        if (!current.attackId().equals(updated.attackId())) {
            throw new IllegalArgumentException("An orbital attack update cannot change its identity");
        }
        this.attacks.put(updated.attackId(), updated);
        if (current.phase() != updated.phase()) {
            this.phaseStartedAt.put(updated.attackId(), server.overworld().getGameTime());
        }
    }

    private void handleTerrainBoundary(
                                       MinecraftServer server,
                                       OrbitalAttackRecord current,
                                       ChunkReadiness readiness,
                                       String fallbackFailure) {
        if (readiness == ChunkReadiness.FAULTED) {
            String failure = this.terrainWorkScheduler.failure(current.attackId());
            fault(server, current, failure == null ? fallbackFailure : failure);
            return;
        }
        updateAttack(server, current, current.withWorkState(persistedWorkState(readiness)));
    }

    private void updateAttack(MinecraftServer server, OrbitalAttackRecord current, OrbitalAttackRecord updated) {
        if (!current.attackId().equals(updated.attackId())) {
            throw new IllegalArgumentException("An orbital attack update cannot change its identity");
        }
        if (current.equals(updated)) {
            return;
        }
        replaceAttack(server, current, updated);
        setDirty();
    }

    private static OrbitalAttackWorkState persistedWorkState(ChunkReadiness readiness) {
        return switch (readiness) {
            case READY -> OrbitalAttackWorkState.WORKING;
            case WAITING_FOR_CHUNK -> OrbitalAttackWorkState.WAITING_FOR_CHUNK;
            case WAITING_FOR_BUDGET -> OrbitalAttackWorkState.WAITING_FOR_BUDGET;
            case FAULTED -> throw new IllegalArgumentException("A faulted chunk request has no active work state");
        };
    }

    private static boolean targetOutsideBounds(ServerLevel level, BlockPos target, int radius) {
        if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()) {
            return true;
        }
        return !level.getWorldBorder().isWithinBounds(target) || !level.getWorldBorder().isWithinBounds(target.offset(-radius, 0, -radius)) || !level.getWorldBorder().isWithinBounds(target.offset(radius, 0, radius));
    }

    private record VisualEffect(BlockPos position, int radius, long totalWork) {}

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Orbital attack state may only be modified on the server thread");
        }
    }
}
