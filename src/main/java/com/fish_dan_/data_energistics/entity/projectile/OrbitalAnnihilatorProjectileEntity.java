package com.fish_dan_.data_energistics.entity.projectile;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.entity.explosive.DataNukePrimedEntity;
import com.fish_dan_.data_energistics.entity.explosive.DigitalAnnihilationWork;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.registry.DEEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative 80-tick orbital digital-annihilation payload.
 *
 * <p>
 * The entity has no collision, gravity, portal handling, or horizontal movement. Its only world side effect is
 * materializing the existing fuse entity when the captured target Y is reached.
 * </p>
 */
public final class OrbitalAnnihilatorProjectileEntity extends Entity {

    public static final int FLIGHT_TICKS = 80;
    private static final int START_HEIGHT_OFFSET = 256;
    private static final UUID INVALID_ATTACK_ID = new UUID(0L, 0L);
    private static final String TAG_ATTACK_ID = "OrbitalAttackId";
    private static final String TAG_TARGET = "Target";
    private static final String TAG_FLIGHT_TICKS = "FlightTicks";
    private static final String TAG_EXEMPTIONS = "DamageExemptions";
    private static final String TAG_UUID = "UUID";
    private static final String TAG_WORK_INTERVAL = "WorkIntervalTicks";
    private static final String TAG_MAX_RADIUS = "MaxRadius";
    private static final String TAG_CENTER_RADIUS = "CenterEntityConsumeRadius";
    private UUID attackId = INVALID_ATTACK_ID;
    private BlockPos target = BlockPos.ZERO;
    private int flightTicks;
    private Set<UUID> damageExemptions = Set.of();
    private DigitalAnnihilationWork.Settings workSettings;
    private @Nullable String persistedStateFailure;

    public OrbitalAnnihilatorProjectileEntity(EntityType<? extends OrbitalAnnihilatorProjectileEntity> entityType,
                                              Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.workSettings = currentWorkSettings();
    }

    public OrbitalAnnihilatorProjectileEntity(ServerLevel level, UUID attackId, BlockPos target,
                                              Set<UUID> damageExemptions,
                                              int workIntervalTicks,
                                              int maxRadius,
                                              double centerEntityConsumeRadius) {
        this(DEEntities.ORBITAL_ANNIHILATOR_PROJECTILE.get(), level);
        this.attackId = attackId;
        this.target = target.immutable();
        this.damageExemptions = Set.copyOf(damageExemptions);
        this.workSettings = new DigitalAnnihilationWork.Settings(
                workIntervalTicks,
                maxRadius,
                centerEntityConsumeRadius);
        this.setPos(this.target.getX() + 0.5D, startY(level), this.target.getZ() + 0.5D);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        String loadFailure = this.persistedStateFailure;
        if (loadFailure != null) {
            discardInvalidPersistedState(serverLevel, loadFailure);
            return;
        }

        int nextFlightTicks = Math.min(FLIGHT_TICKS, this.flightTicks + 1);
        double progress = (double) nextFlightTicks / (double) FLIGHT_TICKS;
        double y = startY(serverLevel) + (this.target.getY() + 0.5D - startY(serverLevel)) * progress;
        this.setPos(this.target.getX() + 0.5D, y, this.target.getZ() + 0.5D);
        this.flightTicks = nextFlightTicks;
        if (nextFlightTicks < FLIGHT_TICKS) {
            return;
        }

        DataNukePrimedEntity nuke = DataNukePrimedEntity.createOrbitalPayload(
                serverLevel,
                this.target,
                this.attackId,
                this.damageExemptions,
                this.workSettings);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(serverLevel.getServer());
        if (!serverLevel.addFreshEntity(nuke)) {
            discardRejectedPayload(serverLevel, attacks, nuke, "fuse entity could not be added to the target dimension");
            return;
        }
        if (!attacks.markDigitalPayloadArrived(
                serverLevel.getServer(),
                this.attackId,
                nuke.getUUID())) {
            discardRejectedPayload(serverLevel, attacks, nuke, "attack state rejected the materialized fuse");
            return;
        }
        this.discard();
    }

    @Override
    public boolean isAlwaysTicking() {
        // The shared orbital scheduler pins the target chunk; this keeps the above-build-height payload ticking inside
        // that loaded chunk before its section becomes normally visible.
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putUUID(TAG_ATTACK_ID, this.attackId);
        tag.put(TAG_TARGET, NbtUtils.writeBlockPos(this.target));
        tag.putInt(TAG_FLIGHT_TICKS, this.flightTicks);
        tag.putInt(TAG_WORK_INTERVAL, this.workSettings.workIntervalTicks());
        tag.putInt(TAG_MAX_RADIUS, this.workSettings.maxRadius());
        tag.putDouble(TAG_CENTER_RADIUS, this.workSettings.centerEntityConsumeRadius());
        ListTag exemptionList = new ListTag();
        this.damageExemptions.stream().sorted().forEach(uuid -> {
            CompoundTag exemption = new CompoundTag();
            exemption.putUUID(TAG_UUID, uuid);
            exemptionList.add(exemption);
        });
        tag.put(TAG_EXEMPTIONS, exemptionList);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ATTACK_ID) || !tag.contains(TAG_TARGET, Tag.TAG_INT_ARRAY) || !tag.contains(TAG_FLIGHT_TICKS, Tag.TAG_INT) || !tag.contains(TAG_WORK_INTERVAL, Tag.TAG_INT) || !tag.contains(TAG_MAX_RADIUS, Tag.TAG_INT) || !tag.contains(TAG_CENTER_RADIUS, Tag.TAG_DOUBLE) || !tag.contains(TAG_EXEMPTIONS, Tag.TAG_LIST)) {
            this.persistedStateFailure = "missing required projectile fields";
            return;
        }
        BlockPos persistedTarget = NbtUtils.readBlockPos(tag, TAG_TARGET).orElse(null);
        if (persistedTarget == null) {
            this.persistedStateFailure = "invalid projectile target";
            return;
        }
        this.attackId = tag.getUUID(TAG_ATTACK_ID);
        this.target = persistedTarget.immutable();
        this.flightTicks = Math.clamp(tag.getInt(TAG_FLIGHT_TICKS), 0, FLIGHT_TICKS);
        this.workSettings = DigitalAnnihilationWork.Settings.fromPersisted(
                tag.getInt(TAG_WORK_INTERVAL),
                tag.getInt(TAG_MAX_RADIUS),
                tag.getDouble(TAG_CENTER_RADIUS));
        Tag rawExemptions = tag.get(TAG_EXEMPTIONS);
        if (!(rawExemptions instanceof ListTag exemptionList)) {
            this.persistedStateFailure = "invalid projectile exemptions";
            return;
        }
        HashSet<UUID> exemptions = new HashSet<>();
        for (Tag rawExemption : exemptionList) {
            if (!(rawExemption instanceof CompoundTag exemption) || !exemption.hasUUID(TAG_UUID) || !exemptions.add(exemption.getUUID(TAG_UUID))) {
                this.persistedStateFailure = "invalid projectile exemption entry";
                return;
            }
        }
        this.damageExemptions = Set.copyOf(exemptions);
    }

    private void discardInvalidPersistedState(ServerLevel level, String reason) {
        boolean attackFaulted = !INVALID_ATTACK_ID.equals(this.attackId) && OrbitalAttackSavedData.get(level.getServer()).markDigitalPayloadFaulted(
                level.getServer(),
                this.attackId,
                reason);
        Data_Energistics.LOGGER.error(
                "Discarding invalid orbital annihilator projectile {}: {}; attackFaulted={}",
                this.getUUID(),
                reason,
                attackFaulted);
        this.discard();
    }

    private void discardRejectedPayload(
                                        ServerLevel level,
                                        OrbitalAttackSavedData attacks,
                                        DataNukePrimedEntity nuke,
                                        String reason) {
        nuke.discard();
        boolean attackFaulted = attacks.markDigitalPayloadFaulted(level.getServer(), this.attackId, reason);
        Data_Energistics.LOGGER.error(
                "Discarding rejected orbital annihilator projectile {} for attack {} at {}: {}; attackFaulted={}",
                this.getUUID(),
                this.attackId,
                this.target,
                reason,
                attackFaulted);
        this.discard();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    public UUID attackId() {
        return this.attackId;
    }

    public BlockPos target() {
        return this.target;
    }

    public int flightTicks() {
        return this.flightTicks;
    }

    public static double startY(ServerLevel level) {
        return level.getMaxBuildHeight() + START_HEIGHT_OFFSET;
    }

    private static DigitalAnnihilationWork.Settings currentWorkSettings() {
        var settings = DataEnergisticsConfiguration.INSTANCE.explosives.dataNuke;
        return new DigitalAnnihilationWork.Settings(
                settings.workIntervalTicks,
                settings.maxRadius,
                settings.centerEntityConsumeRadius);
    }
}
