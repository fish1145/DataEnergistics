package com.fish_dan_.data_energistics.entity.resource;

import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;
import com.fish_dan_.data_energistics.registry.DEEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DispersingDataEntity extends Entity {

    private static final EntityDataAccessor<Integer> TEXTURE_VARIANT = SynchedEntityData.defineId(DispersingDataEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AMOUNT = SynchedEntityData.defineId(DispersingDataEntity.class, EntityDataSerializers.INT);
    public static final int MAX_DATA_AMOUNT = 8;
    private static final int LIFETIME_TICKS = 1200;
    private static final double DRIFT_STRENGTH = 0.015D;
    private static final double VERTICAL_DRIFT = 0.01D;
    private static final double HITBOX_Y_OFFSET = 0.0625D;
    private static final double MERGE_RADIUS = 0.5D;
    private static final double ATTRACTION_RADIUS = 4.0D;
    private static final double ATTRACTION_STRENGTH = 0.012D;
    private static final int MAX_LIQUID_ESCAPE_DISTANCE = 64;
    private int age;
    private int pendingDataOverflow;

    public DispersingDataEntity(EntityType<? extends DispersingDataEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TEXTURE_VARIANT, 0);
        builder.define(DATA_AMOUNT, 1);
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        Vec3 drift = new Vec3(
                (this.random.nextDouble() - 0.5D) * DRIFT_STRENGTH,
                (this.random.nextDouble() - 0.5D) * VERTICAL_DRIFT,
                (this.random.nextDouble() - 0.5D) * DRIFT_STRENGTH);
        Vec3 attraction = this.level() instanceof ServerLevel ? this.getAttractionAcceleration() : Vec3.ZERO;
        this.setDeltaMovement(this.getDeltaMovement().scale(0.92D).add(drift).add(attraction));
        this.move(MoverType.SELF, this.getDeltaMovement());

        if (this.level() instanceof ServerLevel serverLevel) {
            this.releasePendingDataOverflow(serverLevel);
            this.mergeNearbyData();
            if (++this.age >= LIFETIME_TICKS) {
                this.discard();
            }
        }
    }

    private Vec3 getAttractionAcceleration() {
        DispersingDataEntity nearest = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (DispersingDataEntity other : this.level().getEntitiesOfClass(
                DispersingDataEntity.class,
                this.getBoundingBox().inflate(ATTRACTION_RADIUS),
                entity -> entity != this && entity.isAlive() && this.canMergeWith(entity))) {
            double distanceSqr = this.distanceToSqr(other);
            if (distanceSqr < nearestDistanceSqr) {
                nearest = other;
                nearestDistanceSqr = distanceSqr;
            }
        }

        if (nearest == null || nearestDistanceSqr < 1.0E-6D) {
            return Vec3.ZERO;
        }
        return nearest.position().subtract(this.position()).normalize().scale(ATTRACTION_STRENGTH);
    }

    private boolean canMergeWith(DispersingDataEntity other) {
        DispersingDataEntity receiver = this.getId() < other.getId() ? this : other;
        return receiver.getDataAmount() < MAX_DATA_AMOUNT;
    }

    private void mergeNearbyData() {
        if (this.getDataAmount() >= MAX_DATA_AMOUNT) {
            return;
        }

        for (DispersingDataEntity other : this.level().getEntitiesOfClass(
                DispersingDataEntity.class,
                this.getBoundingBox().inflate(MERGE_RADIUS),
                entity -> entity != this && entity.isAlive() && this.getId() < entity.getId())) {
            int transferred = Math.min(MAX_DATA_AMOUNT - this.getDataAmount(), other.getDataAmount());
            if (transferred <= 0) {
                continue;
            }

            this.age = Math.min(this.age, other.age);
            this.setDataAmount(this.getDataAmount() + transferred);
            other.removeDataAmount(transferred);
            if (this.getDataAmount() >= MAX_DATA_AMOUNT) {
                break;
            }
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected AABB makeBoundingBox() {
        EntityDimensions dimensions = this.getDimensions(this.getPose());
        float width = dimensions.width();
        float height = dimensions.height();
        double halfWidth = width / 2.0F;
        return new AABB(
                this.getX() - halfWidth,
                this.getY() - HITBOX_Y_OFFSET,
                this.getZ() - halfWidth,
                this.getX() + halfWidth,
                this.getY() - HITBOX_Y_OFFSET + height,
                this.getZ() + halfWidth);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (!(heldStack.getItem() instanceof RadixContainmentSphereItem containmentSphereItem)) {
            return InteractionResult.PASS;
        }

        boolean captured = containmentSphereItem.hasRangeCapture(heldStack) ? containmentSphereItem.captureNearbyDispersingData(heldStack, player, this.getBoundingBox().getCenter()) : containmentSphereItem.captureDispersingData(heldStack, player, this);
        return captured ? InteractionResult.sidedSuccess(this.level().isClientSide()) : InteractionResult.PASS;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
        if (tag.contains("TextureVariant")) {
            this.setTextureVariant(tag.getInt("TextureVariant"));
        }
        int dataAmount = tag.contains("DataAmount") ? tag.getInt("DataAmount") : 1;
        this.pendingDataOverflow = Math.max(0, dataAmount - MAX_DATA_AMOUNT);
        this.setDataAmount(dataAmount);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
        tag.putInt("TextureVariant", this.getTextureVariant());
        tag.putInt("DataAmount", this.getDataAmount() + this.pendingDataOverflow);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    public int getTextureVariant() {
        return this.entityData.get(TEXTURE_VARIANT);
    }

    public void setTextureVariant(int variant) {
        this.entityData.set(TEXTURE_VARIANT, Math.floorMod(variant, 4));
    }

    @Override
    public Component getName() {
        Component name = super.getName();
        return this.getDataAmount() > 1 ? name.copy().append("*").append(String.valueOf(this.getDataAmount())) : name;
    }

    public int getDataAmount() {
        return this.entityData.get(DATA_AMOUNT);
    }

    public void setDataAmount(int amount) {
        this.entityData.set(DATA_AMOUNT, Math.clamp(amount, 1, MAX_DATA_AMOUNT));
    }

    public void removeDataAmount(int amount) {
        if (amount <= 0) {
            return;
        }

        int remaining = this.getDataAmount() - amount;
        if (remaining <= 0) {
            this.discard();
        } else {
            this.setDataAmount(remaining);
        }
    }

    public float getSizeScale() {
        return (float) Math.cbrt(this.getDataAmount());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(this.getSizeScale());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_AMOUNT.equals(key)) {
            this.refreshDimensions();
        }
    }

    public static boolean spawnAt(ServerLevel level, BlockPos pos, RandomSource random) {
        return spawnAt(level, pos, random, 1);
    }

    private static boolean spawnAt(ServerLevel level, BlockPos pos, RandomSource random, int dataAmount) {
        DispersingDataEntity entity = DEEntities.DISPERSING_DATA.get().create(level);
        if (entity == null) {
            return false;
        }

        BlockPos spawnPos = findSpawnPos(level, pos);
        double x = spawnPos.getX() + 0.35D + random.nextDouble() * 0.3D;
        double y = spawnPos.getY() + 0.7D + random.nextDouble() * 0.4D;
        double z = spawnPos.getZ() + 0.35D + random.nextDouble() * 0.3D;
        entity.setPos(x, y, z);
        entity.setTextureVariant(random.nextInt(4));
        entity.setDataAmount(dataAmount);
        entity.setDeltaMovement(
                (random.nextDouble() - 0.5D) * 0.08D,
                0.01D + random.nextDouble() * 0.03D,
                (random.nextDouble() - 0.5D) * 0.08D);
        return level.addFreshEntity(entity);
    }

    private void releasePendingDataOverflow(ServerLevel level) {
        if (this.pendingDataOverflow <= 0) {
            return;
        }

        int amountToRelease = Math.min(this.pendingDataOverflow, MAX_DATA_AMOUNT);
        if (spawnAt(level, this.blockPosition(), this.random, amountToRelease)) {
            this.pendingDataOverflow -= amountToRelease;
        }
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos pos) {
        if (level.getFluidState(pos).isEmpty()) {
            return pos;
        }

        int maxY = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + MAX_LIQUID_ESCAPE_DISTANCE);
        for (int y = pos.getY() + 1; y <= maxY; y++) {
            BlockPos candidate = new BlockPos(pos.getX(), y, pos.getZ());
            if (level.getFluidState(candidate).isEmpty() && level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()) {
                return candidate;
            }
        }
        return pos;
    }
}
