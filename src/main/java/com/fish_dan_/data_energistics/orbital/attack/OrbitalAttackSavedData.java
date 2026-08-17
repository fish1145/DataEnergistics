package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * World-level source of truth for confirmed orbital attacks and their resumable warning, delivery and cooldown work.
 * Mutations are restricted to the server thread; all dimensions share the overworld SavedData instance.
 */
public final class OrbitalAttackSavedData extends SavedData {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String DATA_NAME = Data_Energistics.MODID + "_orbital_attacks";
    private static final String ATTACKS_TAG = "attacks";
    private static final String ATTACK_ID_TAG = "attack_id";
    private static final String WEAPON_ID_TAG = "weapon_id";
    private static final String MODE_TAG = "mode";
    private static final String PHASE_TAG = "phase";
    private static final String DIMENSION_TAG = "dimension";
    private static final String TARGET_TAG = "target";
    private static final String CONFIGURATION_REVISION_TAG = "configuration_revision";
    private static final String WARNING_TICKS_TAG = "warning_ticks";
    private static final String WORK_CURSOR_TAG = "work_cursor";
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
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        Optional<OrbitalWeaponRecord> foundWeapon = weapons.find(weaponId);
        if (foundWeapon.isEmpty()) {
            return Optional.empty();
        }
        OrbitalWeaponRecord weapon = foundWeapon.orElseThrow();
        if (!weapon.canPerform(actorId, OrbitalWeaponAction.FIRE) || hasAttackForMode(weaponId, OrbitalAttackMode.KINETIC)) {
            return Optional.empty();
        }

        ServerLevel targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (targetLevel == null || !validTarget(targetLevel, target)) {
            return Optional.empty();
        }
        if (!weapons.hasOnlineEndpoint(server, weaponId, dimensionId)) {
            return Optional.empty();
        }

        DataEnergisticsSettings.OrbitalWeapon settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon();
        OrbitalAttackCost cost = OrbitalAttackCost.kinetic(settings);
        OrbitalAttackRecord warning = OrbitalAttackRecord.warning(
                UUID.randomUUID(),
                weaponId,
                OrbitalAttackMode.KINETIC,
                dimensionId,
                target,
                DataEnergisticsConfiguration.INSTANCE.revision(),
                settings.attackWarningTicks(),
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
        this.attacks.put(warning.attackId(), warning);
        setDirty();
        return Optional.of(warning);
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
        try {
            weapons.refundWarningReserve(
                    server,
                    warning.weaponId(),
                    actorId,
                    warning.celestialEscrow(),
                    warning.aeEscrow());
        } catch (RuntimeException exception) {
            this.attacks.put(attackId, warning);
            throw exception;
        }
        setDirty();
        return true;
    }

    /**
     * Advances all persisted attacks once. This is called from the server tick event and never crosses a dimension's
     * thread boundary.
     */
    public void tick(MinecraftServer server) {
        requireServerThread(server);
        for (OrbitalAttackRecord current : List.copyOf(this.attacks.values())) {
            switch (current.phase()) {
                case RESERVED_WARNING -> tickWarning(server, current);
                case COMMITTED, DELIVERY -> tickDelivery(server, current);
                case COOLDOWN -> tickCooldown(current);
                case FAULTED -> {
                    // A faulted attack is retained for administrator diagnostics and is never retried implicitly.
                }
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag attackList = new ListTag();
        this.attacks.values().stream().map(OrbitalAttackSavedData::writeAttack).forEach(attackList::add);
        tag.put(ATTACKS_TAG, attackList);
        return tag;
    }

    private static OrbitalAttackSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        OrbitalAttackSavedData data = new OrbitalAttackSavedData();
        Tag rawAttacks = tag.get(ATTACKS_TAG);
        if (!(rawAttacks instanceof ListTag attackList)) {
            return data;
        }
        for (Tag rawAttack : attackList) {
            if (!(rawAttack instanceof CompoundTag attackTag)) {
                continue;
            }
            OrbitalAttackRecord attack = readAttack(attackTag);
            if (attack == null || data.attacks.putIfAbsent(attack.attackId(), attack) != null) {
                LOGGER.warn("Ignoring invalid or duplicate orbital attack record");
            }
        }
        return data;
    }

    private static CompoundTag writeAttack(OrbitalAttackRecord attack) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ATTACK_ID_TAG, attack.attackId());
        tag.putUUID(WEAPON_ID_TAG, attack.weaponId());
        tag.putString(MODE_TAG, attack.mode().name());
        tag.putString(PHASE_TAG, attack.phase().name());
        tag.putString(DIMENSION_TAG, attack.dimensionId().toString());
        tag.put(TARGET_TAG, NbtUtils.writeBlockPos(attack.target()));
        tag.putLong(CONFIGURATION_REVISION_TAG, attack.configurationRevision());
        tag.putInt(WARNING_TICKS_TAG, attack.warningTicksRemaining());
        tag.putLong(WORK_CURSOR_TAG, attack.workCursor());
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

    private static OrbitalAttackRecord readAttack(CompoundTag tag) {
        if (!tag.hasUUID(ATTACK_ID_TAG) || !tag.hasUUID(WEAPON_ID_TAG) || !tag.contains(DIMENSION_TAG, Tag.TAG_STRING) || !tag.contains(TARGET_TAG, Tag.TAG_INT_ARRAY)) {
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
            List<UUID> exemptions = new ArrayList<>();
            Tag rawExemptions = tag.get(EXEMPTIONS_TAG);
            if (rawExemptions instanceof ListTag exemptionList) {
                for (Tag rawExemption : exemptionList) {
                    if (rawExemption instanceof CompoundTag exemption && exemption.hasUUID(UUID_TAG)) {
                        exemptions.add(exemption.getUUID(UUID_TAG));
                    }
                }
            }
            return new OrbitalAttackRecord(
                    tag.getUUID(ATTACK_ID_TAG),
                    tag.getUUID(WEAPON_ID_TAG),
                    mode,
                    phase,
                    dimensionId,
                    target,
                    tag.getLong(CONFIGURATION_REVISION_TAG),
                    tag.getInt(WARNING_TICKS_TAG),
                    tag.getLong(WORK_CURSOR_TAG),
                    tag.getBoolean(IMPACT_APPLIED_TAG),
                    tag.getInt(COOLDOWN_TICKS_TAG),
                    tag.getInt(COOLDOWN_DURATION_TAG),
                    tag.getLong(CELESTIAL_ESCROW_TAG),
                    tag.getLong(AE_ESCROW_TAG),
                    Set.copyOf(exemptions));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void tickWarning(MinecraftServer server, OrbitalAttackRecord current) {
        if (current.warningTicksRemaining() > 1) {
            this.attacks.put(current.attackId(), current.withWarningTicks(current.warningTicksRemaining() - 1));
            setDirty();
            return;
        }
        OrbitalAttackRecord committed = current.committed();
        this.attacks.put(current.attackId(), committed);
        setDirty();
        tickDelivery(server, committed);
    }

    private void tickDelivery(MinecraftServer server, OrbitalAttackRecord current) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, current.dimensionId());
        ServerLevel level = server.getLevel(dimension);
        if (level == null || current.mode() != OrbitalAttackMode.KINETIC) {
            fault(current, "Target dimension or attack mode is unavailable");
            return;
        }
        if (!OrbitalKineticStrike.areTerrainChunksLoaded(level, current.target())) {
            // Keep the cursor unchanged; the next tick may resume after the target chunks are loaded by normal world
            // use.
            return;
        }
        try {
            OrbitalAttackRecord delivery = current;
            if (!delivery.impactApplied()) {
                OrbitalKineticStrike.applyImpactDamage(level, delivery.target(), delivery.damageExemptions());
                delivery = delivery.markImpactApplied();
            }
            OrbitalKineticStrike.WorkSlice slice = OrbitalKineticStrike.applyBudget(level, current.target(), current.workCursor());
            OrbitalAttackRecord updated = delivery.withWorkCursor(slice.nextCursor());
            if (slice.complete()) {
                updated = updated.cooldown(current.cooldownDurationTicks());
            }
            this.attacks.put(current.attackId(), updated);
            setDirty();
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Orbital attack {} failed during kinetic delivery at {}",
                    current.attackId(),
                    current.target(),
                    exception);
            fault(current, "Kinetic delivery failed");
        }
    }

    private void tickCooldown(OrbitalAttackRecord current) {
        if (current.cooldownTicksRemaining() <= 1) {
            this.attacks.remove(current.attackId());
        } else {
            this.attacks.put(current.attackId(), current.withCooldownTicks(current.cooldownTicksRemaining() - 1));
        }
        setDirty();
    }

    private void fault(OrbitalAttackRecord current, String reason) {
        LOGGER.error("Orbital attack {} entered FAULTED: {}", current.attackId(), reason);
        this.attacks.put(current.attackId(), current.faulted());
        setDirty();
    }

    private boolean hasAttackForMode(UUID weaponId, OrbitalAttackMode mode) {
        return this.attacks.values().stream()
                .anyMatch(attack -> attack.weaponId().equals(weaponId) && attack.mode() == mode);
    }

    private static boolean validTarget(ServerLevel level, BlockPos target) {
        if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        int radius = OrbitalKineticStrike.SHOCKWAVE_RADIUS;
        if (!level.getWorldBorder().isWithinBounds(target) || !level.getWorldBorder().isWithinBounds(target.offset(-radius, 0, -radius)) || !level.getWorldBorder().isWithinBounds(target.offset(radius, 0, radius))) {
            return false;
        }
        if (!OrbitalKineticStrike.areTerrainChunksLoaded(level, target)) {
            return false;
        }
        return true;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Orbital attack state may only be modified on the server thread");
        }
    }
}
