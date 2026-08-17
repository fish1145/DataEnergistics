package com.fish_dan_.data_energistics.entity.explosive;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.DataNukeSchema;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class DataNukePrimedEntity extends PrimedTnt {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE = SynchedEntityData.defineId(DataNukePrimedEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final String TAG_ORIGIN = "Origin";
    private static final String TAG_ACTIVE = "DataNukeActive";
    private static final String TAG_WORK_TICKS = "DataNukeWorkTicks";
    private static final String TAG_EXPANSION_RADIUS = "DataNukeExpansionRadius";
    private static final TicketType<UUID> CHUNK_TICKET_TYPE = TicketType.create(
            Data_Energistics.MODID + ":digital_annihilator",
            UUID::compareTo);
    private static final int CHUNK_TICKET_DISTANCE = 2;
    private static final double CENTER_Y_OFFSET = 0.5D;
    private static final int SURFACE_INNER_MARGIN = 3;
    private static final int SURFACE_OUTER_MARGIN = 3;

    private BlockPos origin = BlockPos.ZERO;
    private int workTicks;
    private int expansionRadius;
    @Nullable
    private LivingEntity owner;
    @Nullable
    private ChunkPos forcedChunk;

    public DataNukePrimedEntity(EntityType<? extends DataNukePrimedEntity> entityType, Level level) {
        super(entityType, level);
    }

    public DataNukePrimedEntity(Level level, BlockPos origin, @Nullable LivingEntity owner) {
        super(DEEntities.DATA_NUKE_PRIMED.get(), level);
        this.origin = origin.immutable();
        this.owner = owner;
        this.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        double angle = level.random.nextDouble() * (Math.PI * 2.0D);
        this.setDeltaMovement(-Math.sin(angle) * 0.02D, 0.2D, -Math.cos(angle) * 0.02D);
        this.setFuse(80);
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.setBlockState(DEBlocks.DATA_NUKE.get().defaultBlockState());
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
        removeChunkTicket();
        super.onRemovedFromLevel();
    }

    @Override
    public void remove(RemovalReason reason) {
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_ORIGIN)) {
            this.origin = BlockPos.of(tag.getLong(TAG_ORIGIN));
        }
        this.setActive(tag.getBoolean(TAG_ACTIVE));
        this.workTicks = Math.max(0, tag.getInt(TAG_WORK_TICKS));
        this.expansionRadius = Math.max(0, Math.min(
                DataEnergisticsConfiguration.INSTANCE.explosives.dataNuke.maxRadius,
                tag.getInt(TAG_EXPANSION_RADIUS)));
    }

    private void activate() {
        if (this.isActive()) {
            return;
        }

        this.setActive(true);
        this.setFuse(80);
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.origin.getX() + 0.5D, this.origin.getY(), this.origin.getZ() + 0.5D);
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
            DataNukeSchema settings = DataEnergisticsConfiguration.INSTANCE.explosives.dataNuke;
            consumeCenterEntities(level, settings);
            this.workTicks++;
            if (this.workTicks < settings.workIntervalTicks) {
                return;
            }

            this.workTicks = 0;
            int nextRadius = getNextExpansionRadius(settings);
            if (!consumeSurfaceBlocks(level, nextRadius, settings)) {
                return;
            }
            this.expansionRadius = nextRadius;
            consumeExpandedEntities(level, this.expansionRadius);
            if (isFinished(settings)) {
                LOGGER.info("Data nuke finished at {} in dimension {}.", this.origin, level.dimension().location());
                this.discard();
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Data nuke failed at {} in dimension {} and will be discarded.",
                    this.origin, this.level().dimension().location(), exception);
            this.discard();
        }
    }

    private boolean isFinished(DataNukeSchema settings) {
        return this.expansionRadius >= settings.maxRadius;
    }

    private int getNextExpansionRadius(DataNukeSchema settings) {
        int maxRadius = settings.maxRadius;
        return this.expansionRadius < maxRadius ? this.expansionRadius + 1 : maxRadius;
    }

    private boolean consumeSurfaceBlocks(Level level, int radius, DataNukeSchema settings) {
        if (radius <= 0) {
            return true;
        }

        int innerRadius = Math.max(0, radius - SURFACE_INNER_MARGIN);
        int outerRadius = Math.min(settings.maxRadius, radius + SURFACE_OUTER_MARGIN);
        double innerRadiusSqr = innerRadius * innerRadius;
        double outerRadiusSqr = outerRadius * outerRadius;
        int minOffsetY = Math.max(level.getMinBuildHeight() - this.origin.getY(),
                (int) Math.floor(CENTER_Y_OFFSET - outerRadius - 0.5D));
        int maxOffsetY = Math.min(level.getMaxBuildHeight() - 1 - this.origin.getY(),
                (int) Math.ceil(CENTER_Y_OFFSET + outerRadius - 0.5D));
        int minX = this.origin.getX() - outerRadius;
        int maxX = this.origin.getX() + outerRadius;
        int minZ = this.origin.getZ() - outerRadius;
        int maxZ = this.origin.getZ() + outerRadius;
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);

        BlockPos.MutableBlockPos targetPos = new BlockPos.MutableBlockPos();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, true);
                if (chunk == null) {
                    LOGGER.warn("Data nuke at {} is waiting for chunk [{}, {}] in dimension {}.",
                            this.origin, chunkX, chunkZ, level.dimension().location());
                    return false;
                }

                int startX = Math.max(minX, chunkX << 4);
                int endX = Math.min(maxX, (chunkX << 4) + 15);
                int startZ = Math.max(minZ, chunkZ << 4);
                int endZ = Math.min(maxZ, (chunkZ << 4) + 15);
                for (int x = startX; x <= endX; x++) {
                    int offsetX = x - this.origin.getX();
                    for (int z = startZ; z <= endZ; z++) {
                        int offsetZ = z - this.origin.getZ();
                        double horizontalDistanceSqr = offsetX * offsetX + offsetZ * offsetZ;
                        if (horizontalDistanceSqr > outerRadiusSqr) {
                            continue;
                        }

                        for (int offsetY = minOffsetY; offsetY <= maxOffsetY; offsetY++) {
                            double dy = offsetY + 0.5D - CENTER_Y_OFFSET;
                            double distanceSqr = horizontalDistanceSqr + dy * dy;
                            if (distanceSqr < innerRadiusSqr || distanceSqr > outerRadiusSqr) {
                                continue;
                            }

                            targetPos.set(x, this.origin.getY() + offsetY, z);
                            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(),
                                    Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                        }
                    }
                }
            }
        }
        return true;
    }

    private int consumeCenterEntities(Level level, DataNukeSchema settings) {
        return consumeEntities(level, settings.centerEntityConsumeRadius);
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

    private void setActive(boolean active) {
        this.entityData.set(DATA_ACTIVE, active);
    }

    private void updateChunkTicket() {
        if (!this.isAddedToLevel() || this.isRemoved() || !(this.level() instanceof ServerLevel serverLevel)) {
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
