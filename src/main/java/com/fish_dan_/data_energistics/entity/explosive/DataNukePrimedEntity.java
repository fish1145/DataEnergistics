package com.fish_dan_.data_energistics.entity.explosive;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.DataNukeSchema;
import com.fish_dan_.data_energistics.entity.explosive.DigitalAnnihilationWork.Settings;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class DataNukePrimedEntity extends PrimedTnt {

    public static final int DEFAULT_FUSE_TICKS = 80;
    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE = SynchedEntityData.defineId(DataNukePrimedEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final String TAG_ORIGIN = "Origin";
    private static final String TAG_ACTIVE = "DataNukeActive";
    private static final String TAG_WORK_TICKS = "DataNukeWorkTicks";
    private static final String TAG_EXPANSION_RADIUS = "DataNukeExpansionRadius";
    private static final String TAG_WORK_STATE = "DataNukeWorkState";
    private static final String TAG_ORBITAL_ATTACK_ID = "DataNukeOrbitalAttackId";
    private static final String TAG_DAMAGE_EXEMPTIONS = "DataNukeDamageExemptions";
    private static final String TAG_WORK_SETTINGS_INTERVAL = "DataNukeWorkSettingsInterval";
    private static final String TAG_WORK_SETTINGS_RADIUS = "DataNukeWorkSettingsRadius";
    private static final String TAG_WORK_SETTINGS_CENTER = "DataNukeWorkSettingsCenter";
    private static final String TAG_UUID = "UUID";
    private static final TicketType<UUID> CHUNK_TICKET_TYPE = TicketType.create(
            Data_Energistics.MODID + ":digital_annihilator",
            UUID::compareTo);
    private static final int CHUNK_TICKET_DISTANCE = 2;
    private static final double CENTER_Y_OFFSET = 0.5D;

    private BlockPos origin = BlockPos.ZERO;
    private int workTicks;
    private int expansionRadius;
    @Nullable
    private DigitalAnnihilationWork annihilationWork;
    @Nullable
    private LivingEntity owner;
    @Nullable
    private UUID orbitalAttackId;
    private Set<UUID> damageExemptions = Set.of();
    @Nullable
    private Settings capturedWorkSettings;
    @Nullable
    private ChunkPos forcedChunk;

    public DataNukePrimedEntity(EntityType<? extends DataNukePrimedEntity> entityType, Level level) {
        super(entityType, level);
    }

    public DataNukePrimedEntity(Level level, BlockPos origin, @Nullable LivingEntity owner) {
        this(level, origin, owner, Set.of());
    }

    /** Creates a nuke with an immutable authorization exemption snapshot. */
    public DataNukePrimedEntity(Level level, BlockPos origin, @Nullable LivingEntity owner, Set<UUID> damageExemptions) {
        super(DEEntities.DATA_NUKE_PRIMED.get(), level);
        this.origin = origin.immutable();
        this.owner = owner;
        this.damageExemptions = Set.copyOf(damageExemptions);
        this.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        double angle = level.random.nextDouble() * (Math.PI * 2.0D);
        this.setDeltaMovement(-Math.sin(angle) * 0.02D, 0.2D, -Math.cos(angle) * 0.02D);
        this.setFuse(DEFAULT_FUSE_TICKS);
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.setBlockState(DEBlocks.DATA_NUKE.get().defaultBlockState());
    }

    /** Creates an orbital fuse with the Data Nuke settings captured at attack confirmation. */
    public static DataNukePrimedEntity createOrbitalPayload(
                                                            Level level,
                                                            BlockPos origin,
                                                            UUID attackId,
                                                            Set<UUID> damageExemptions,
                                                            DigitalAnnihilationWork.Settings workSettings) {
        DataNukePrimedEntity entity = new DataNukePrimedEntity(level, origin, null, damageExemptions);
        entity.orbitalAttackId = attackId;
        entity.capturedWorkSettings = workSettings;
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setFuse(DEFAULT_FUSE_TICKS);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTIVE, false);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        updateChunkTicket();
    }

    @Override
    public void onRemovedFromLevel() {
        releaseAnnihilationWork();
        removeChunkTicket();
        super.onRemovedFromLevel();
    }

    @Override
    public void remove(RemovalReason reason) {
        releaseAnnihilationWork();
        removeChunkTicket();
        super.remove(reason);
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        updateChunkTicket();
    }

    @Override
    public void tick() {
        if (this.isActive()) {
            tickActive();
            return;
        }

        this.handlePortal();
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
        }

        int remainingFuse = this.getFuse() - 1;
        this.setFuse(remainingFuse);
        if (remainingFuse <= 0) {
            activate();
            return;
        }

        this.updateInWaterStateAndDoFluidPushing();
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        return this.owner;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong(TAG_ORIGIN, this.origin.asLong());
        tag.putBoolean(TAG_ACTIVE, this.isActive());
        tag.putInt(TAG_WORK_TICKS, this.workTicks);
        tag.putInt(TAG_EXPANSION_RADIUS, this.expansionRadius);
        if (this.annihilationWork != null) {
            CompoundTag workTag = new CompoundTag();
            this.annihilationWork.save(workTag);
            tag.put(TAG_WORK_STATE, workTag);
        }
        if (this.orbitalAttackId != null) {
            tag.putUUID(TAG_ORBITAL_ATTACK_ID, this.orbitalAttackId);
        }
        if (this.capturedWorkSettings != null) {
            tag.putInt(TAG_WORK_SETTINGS_INTERVAL, this.capturedWorkSettings.workIntervalTicks());
            tag.putInt(TAG_WORK_SETTINGS_RADIUS, this.capturedWorkSettings.maxRadius());
            tag.putDouble(TAG_WORK_SETTINGS_CENTER, this.capturedWorkSettings.centerEntityConsumeRadius());
        }
        ListTag exemptionList = new ListTag();
        this.damageExemptions.stream().sorted().forEach(uuid -> {
            CompoundTag exemption = new CompoundTag();
            exemption.putUUID(TAG_UUID, uuid);
            exemptionList.add(exemption);
        });
        tag.put(TAG_DAMAGE_EXEMPTIONS, exemptionList);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_ORIGIN)) {
            this.origin = BlockPos.of(tag.getLong(TAG_ORIGIN));
        }
        this.setActive(tag.getBoolean(TAG_ACTIVE));
        this.workTicks = Math.max(0, tag.getInt(TAG_WORK_TICKS));
        this.orbitalAttackId = tag.hasUUID(TAG_ORBITAL_ATTACK_ID) ? tag.getUUID(TAG_ORBITAL_ATTACK_ID) : null;
        this.capturedWorkSettings = tag.contains(TAG_WORK_SETTINGS_INTERVAL) && tag.contains(TAG_WORK_SETTINGS_RADIUS) && tag.contains(TAG_WORK_SETTINGS_CENTER) ? DigitalAnnihilationWork.Settings.fromPersisted(
                tag.getInt(TAG_WORK_SETTINGS_INTERVAL),
                tag.getInt(TAG_WORK_SETTINGS_RADIUS),
                tag.getDouble(TAG_WORK_SETTINGS_CENTER)) : null;
        int maximumRadius = this.capturedWorkSettings != null ? this.capturedWorkSettings.maxRadius() : DataEnergisticsConfiguration.INSTANCE.explosives.dataNuke.maxRadius;
        this.expansionRadius = Math.max(0, Math.min(maximumRadius, tag.getInt(TAG_EXPANSION_RADIUS)));
        ListTag exemptionList = tag.getList(TAG_DAMAGE_EXEMPTIONS, Tag.TAG_COMPOUND);
        HashSet<UUID> exemptions = new HashSet<>();
        for (int index = 0; index < exemptionList.size(); index++) {
            CompoundTag exemption = exemptionList.getCompound(index);
            if (exemption.hasUUID(TAG_UUID)) {
                exemptions.add(exemption.getUUID(TAG_UUID));
            }
        }
        this.damageExemptions = Set.copyOf(exemptions);
        if (this.isActive()) {
            CompoundTag workTag = tag.getCompound(TAG_WORK_STATE);
            this.annihilationWork = DigitalAnnihilationWork.restore(
                    this.origin,
                    this.getUUID(),
                    this.capturedWorkSettings != null ? this.capturedWorkSettings : currentWorkSettings(),
                    this.workTicks,
                    this.expansionRadius,
                    workTag);
            syncLegacyWorkFields();
        }
    }

    private void activate() {
        if (this.isActive()) {
            return;
        }

        this.setActive(true);
        this.setFuse(DEFAULT_FUSE_TICKS);
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.origin.getX() + 0.5D, this.origin.getY(), this.origin.getZ() + 0.5D);
        this.annihilationWork = DigitalAnnihilationWork.create(
                this.origin,
                this.getUUID(),
                this.capturedWorkSettings != null ? this.capturedWorkSettings : currentWorkSettings());
        syncLegacyWorkFields();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 0.5D, this.getZ(), 1,
                0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                1.2F, 0.8F + serverLevel.random.nextFloat() * 0.2F);
        LOGGER.info("Activated data nuke at {} in dimension {}.", this.origin, serverLevel.dimension().location());
    }

    private void tickActive() {
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.origin.getX() + 0.5D, this.origin.getY(), this.origin.getZ() + 0.5D);
        if (this.level().isClientSide) {
            return;
        }

        try {
            Level level = this.level();
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }
            if (this.orbitalAttackId != null) {
                return;
            }
            if (this.annihilationWork == null) {
                this.annihilationWork = DigitalAnnihilationWork.create(
                        this.origin,
                        this.getUUID(),
                        this.capturedWorkSettings != null ? this.capturedWorkSettings : currentWorkSettings());
            }
            DigitalAnnihilationWork.TickResult result = this.annihilationWork.tick(serverLevel);
            syncLegacyWorkFields();
            if (this.orbitalAttackId != null && (result.state() == DigitalAnnihilationWork.State.SHELL_COMPLETED || serverLevel.getGameTime() % 20L == 0L)) {
                OrbitalAttackSavedData.get(serverLevel.getServer()).markDigitalWorkProgress(
                        serverLevel.getServer(),
                        this.orbitalAttackId,
                        this.annihilationWork.blockCursor());
            }
            consumeCenterEntities(level, this.annihilationWork.centerEntityConsumeRadius());
            if (this.annihilationWork.expansionRadius() > 0) {
                consumeExpandedEntities(level, this.annihilationWork.expansionRadius());
            }
            if (result.state() == DigitalAnnihilationWork.State.FINISHED) {
                if (this.orbitalAttackId != null) {
                    OrbitalAttackSavedData.get(serverLevel.getServer()).markDigitalPayloadCompleted(
                            serverLevel.getServer(),
                            this.orbitalAttackId,
                            this.getUUID());
                }
                LOGGER.info("Data nuke finished at {} in dimension {}.", this.origin, level.dimension().location());
                this.discard();
            } else if (result.state() == DigitalAnnihilationWork.State.FAULTED) {
                if (this.orbitalAttackId != null) {
                    OrbitalAttackSavedData.get(serverLevel.getServer()).markDigitalPayloadFaulted(
                            serverLevel.getServer(),
                            this.orbitalAttackId,
                            this.annihilationWork.failure() == null ? "work fault" : this.annihilationWork.failure());
                }
                LOGGER.error("Data nuke work failed at {} in dimension {}: {}",
                        this.origin,
                        level.dimension().location(),
                        this.annihilationWork.failure());
                this.discard();
            }
        } catch (RuntimeException exception) {
            if (this.orbitalAttackId != null && this.level() instanceof ServerLevel serverLevel) {
                OrbitalAttackSavedData.get(serverLevel.getServer()).markDigitalPayloadFaulted(
                        serverLevel.getServer(),
                        this.orbitalAttackId,
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            }
            LOGGER.error("Data nuke failed at {} in dimension {} and will be discarded.",
                    this.origin, this.level().dimension().location(), exception);
            this.discard();
        }
    }

    /**
     * Advances an active orbital payload with the shared orbital scheduler's mutation and chunk allowance. Manual
     * data nukes never call this entry point and retain their standalone work behavior.
     */
    public DigitalAnnihilationWork.TickResult tickOrbitalWork(
                                                              int mutationBudget,
                                                              Predicate<ChunkPos> chunkReady) {
        if (this.orbitalAttackId == null) {
            throw new IllegalStateException("A manual data nuke cannot use the orbital work scheduler");
        }
        if (!this.isActive()) {
            throw new IllegalStateException("An orbital data nuke cannot start terrain work before its fuse activates");
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Orbital data nuke work requires a server level");
        }
        if (this.annihilationWork == null) {
            this.annihilationWork = DigitalAnnihilationWork.create(
                    this.origin,
                    this.getUUID(),
                    this.capturedWorkSettings != null ? this.capturedWorkSettings : currentWorkSettings());
        }
        DigitalAnnihilationWork.TickResult result = this.annihilationWork.tickBudgeted(
                serverLevel,
                mutationBudget,
                chunkReady);
        syncLegacyWorkFields();
        consumeCenterEntities(serverLevel, this.annihilationWork.centerEntityConsumeRadius());
        if (this.annihilationWork.expansionRadius() > 0) {
            consumeExpandedEntities(serverLevel, this.annihilationWork.expansionRadius());
        }
        return result;
    }

    /** Returns the persisted block cursor owned by the active orbital work. */
    public long orbitalWorkCursor() {
        if (this.annihilationWork == null) {
            return 0L;
        }
        return this.annihilationWork.blockCursor();
    }

    /** Returns the stable work failure reported by an active orbital payload, if present. */
    @Nullable
    public String orbitalWorkFailure() {
        return this.annihilationWork == null ? null : this.annihilationWork.failure();
    }

    private int consumeCenterEntities(Level level, double radius) {
        return consumeEntities(level, radius);
    }

    private int consumeExpandedEntities(Level level, int radius) {
        return consumeEntities(level, radius);
    }

    private int consumeEntities(Level level, double radius) {
        if (radius <= 0.0D) {
            return 0;
        }

        double centerX = getCenterX();
        double centerY = getCenterY();
        double centerZ = getCenterZ();
        AABB bounds = new AABB(
                centerX - radius,
                Math.max(level.getMinBuildHeight(), centerY - radius),
                centerZ - radius,
                centerX + radius,
                Math.min(level.getMaxBuildHeight(), centerY + radius),
                centerZ + radius);
        double radiusSqr = radius * radius;
        List<Entity> entities = level.getEntities(this, bounds,
                entity -> isConsumableEntity(entity) && distanceToCenterSqr(entity, centerX, centerY, centerZ) <= radiusSqr);
        if (entities.isEmpty()) {
            return 0;
        }

        int consumed = 0;
        for (Entity entity : entities) {
            if (entity instanceof ServerPlayer player) {
                player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            } else {
                entity.discard();
            }
            consumed++;
        }
        return consumed;
    }

    private boolean isConsumableEntity(Entity entity) {
        if (entity == this || entity.isRemoved() || !entity.isAlive()) {
            return false;
        }
        if (this.damageExemptions.contains(entity.getUUID())) {
            return false;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (entity instanceof ServerPlayer player && player.hasPermissions(2)) {
            return false;
        }
        return true;
    }

    private double getCenterX() {
        return this.origin.getX() + 0.5D;
    }

    private double getCenterY() {
        return this.origin.getY() + CENTER_Y_OFFSET;
    }

    private double getCenterZ() {
        return this.origin.getZ() + 0.5D;
    }

    /**
     * Returns whether this entity has finished its fuse and is actively annihilating the surrounding area.
     */
    public boolean isActive() {
        return this.entityData.get(DATA_ACTIVE);
    }

    /** Returns the orbital attack identity that created this payload, if any. */
    @Nullable
    public UUID orbitalAttackId() {
        return this.orbitalAttackId;
    }

    /** Returns the frozen UUID exemption snapshot used by the annihilation work. */
    public Set<UUID> damageExemptions() {
        return this.damageExemptions;
    }

    private void setActive(boolean active) {
        this.entityData.set(DATA_ACTIVE, active);
    }

    private void syncLegacyWorkFields() {
        if (this.annihilationWork == null) {
            return;
        }
        this.workTicks = this.annihilationWork.workTicks();
        this.expansionRadius = this.annihilationWork.expansionRadius();
    }

    private static DigitalAnnihilationWork.Settings currentWorkSettings() {
        DataNukeSchema settings = DataEnergisticsConfiguration.INSTANCE.explosives.dataNuke;
        return new DigitalAnnihilationWork.Settings(
                settings.workIntervalTicks,
                settings.maxRadius,
                settings.centerEntityConsumeRadius);
    }

    private void releaseAnnihilationWork() {
        if (this.annihilationWork == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.annihilationWork.release(serverLevel);
    }

    private void updateChunkTicket() {
        if (this.orbitalAttackId != null || !this.isAddedToLevel() || this.isRemoved() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkPos currentChunk = this.chunkPosition();
        if (currentChunk.equals(this.forcedChunk)) {
            return;
        }

        serverLevel.getChunkSource().addRegionTicket(
                CHUNK_TICKET_TYPE,
                currentChunk,
                CHUNK_TICKET_DISTANCE,
                this.getUUID(),
                true);
        if (this.forcedChunk != null) {
            serverLevel.getChunkSource().removeRegionTicket(
                    CHUNK_TICKET_TYPE,
                    this.forcedChunk,
                    CHUNK_TICKET_DISTANCE,
                    this.getUUID(),
                    true);
        }
        this.forcedChunk = currentChunk;
    }

    private void removeChunkTicket() {
        if (this.forcedChunk == null) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("A client-side digital annihilator unexpectedly owns a server chunk ticket");
        }

        serverLevel.getChunkSource().removeRegionTicket(
                CHUNK_TICKET_TYPE,
                this.forcedChunk,
                CHUNK_TICKET_DISTANCE,
                this.getUUID(),
                true);
        this.forcedChunk = null;
    }

    private static double distanceToCenterSqr(Entity entity, double centerX, double centerY, double centerZ) {
        Vec3 entityCenter = entity.getBoundingBox().getCenter();
        double x = entityCenter.x - centerX;
        double y = entityCenter.y - centerY;
        double z = entityCenter.z - centerZ;
        return x * x + y * y + z * z;
    }
}
