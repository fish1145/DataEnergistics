package com.fish_dan_.data_energistics.entity.projectile;

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
    private static final String TAG_ATTACK_ID = "OrbitalAttackId";
    private static final String TAG_TARGET = "Target";
    private static final String TAG_FLIGHT_TICKS = "FlightTicks";
    private static final String TAG_EXEMPTIONS = "DamageExemptions";
    private static final String TAG_UUID = "UUID";
    private static final String TAG_WORK_INTERVAL = "WorkIntervalTicks";
    private static final String TAG_MAX_RADIUS = "MaxRadius";
    private static final String TAG_CENTER_RADIUS = "CenterEntityConsumeRadius";
    private UUID attackId = new UUID(0L, 0L);
    private BlockPos target = BlockPos.ZERO;
    private int flightTicks;
    private Set<UUID> damageExemptions = Set.of();
    private DigitalAnnihilationWork.Settings workSettings;

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
        serverLevel.addFreshEntity(nuke);
        OrbitalAttackSavedData.get(serverLevel.getServer()).markDigitalPayloadArrived(
                serverLevel.getServer(),
                this.attackId,
                nuke.getUUID());
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
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
        if (tag.hasUUID(TAG_ATTACK_ID)) {
            this.attackId = tag.getUUID(TAG_ATTACK_ID);
        }
        this.target = NbtUtils.readBlockPos(tag, TAG_TARGET).orElse(BlockPos.ZERO).immutable();
        this.flightTicks = Math.clamp(tag.getInt(TAG_FLIGHT_TICKS), 0, FLIGHT_TICKS);
        this.workSettings = tag.contains(TAG_WORK_INTERVAL) && tag.contains(TAG_MAX_RADIUS) && tag.contains(TAG_CENTER_RADIUS) ? new DigitalAnnihilationWork.Settings(
                tag.getInt(TAG_WORK_INTERVAL),
                tag.getInt(TAG_MAX_RADIUS),
                tag.getDouble(TAG_CENTER_RADIUS)) : currentWorkSettings();
        ListTag exemptionList = tag.getList(TAG_EXEMPTIONS, Tag.TAG_COMPOUND);
        HashSet<UUID> exemptions = new HashSet<>();
        for (int index = 0; index < exemptionList.size(); index++) {
            CompoundTag exemption = exemptionList.getCompound(index);
            if (exemption.hasUUID(TAG_UUID)) {
                exemptions.add(exemption.getUUID(TAG_UUID));
            }
        }
        this.damageExemptions = Set.copyOf(exemptions);
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

    public Set<UUID> damageExemptions() {
        return this.damageExemptions;
    }

    public DigitalAnnihilationWork.Settings workSettings() {
        return this.workSettings;
    }

    public static double startY(ServerLevel level) {
        return level.getMaxBuildHeight() + START_HEIGHT_OFFSET;
    }

    private static DigitalAnnihilationWork.Settings currentWorkSettings() {
        var settings = DataEnergisticsConfiguration.INSTANCE.explosives.dataNuke;
        return new DigitalAnnihilationWork.Settings(
                settings.workIntervalTicks(),
                settings.maxRadius(),
                settings.centerEntityConsumeRadius());
    }
}
