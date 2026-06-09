package com.fish_dan_.data_energistics.entity;

import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DispersingDataEntity extends Entity {

    private static final EntityDataAccessor<Integer> TEXTURE_VARIANT = SynchedEntityData.defineId(DispersingDataEntity.class, EntityDataSerializers.INT);
    private static final int LIFETIME_TICKS = 1200;
    private static final double DRIFT_STRENGTH = 0.015D;
    private static final double VERTICAL_DRIFT = 0.01D;
    private static final double HITBOX_Y_OFFSET = 0.0625D;
    private static final int MAX_LIQUID_ESCAPE_DISTANCE = 64;
    private int age;

    public DispersingDataEntity(EntityType<? extends DispersingDataEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TEXTURE_VARIANT, 0);
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        Vec3 drift = new Vec3(
                (this.random.nextDouble() - 0.5D) * DRIFT_STRENGTH,
                (this.random.nextDouble() - 0.5D) * VERTICAL_DRIFT,
                (this.random.nextDouble() - 0.5D) * DRIFT_STRENGTH);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.92D).add(drift));
        this.move(MoverType.SELF, this.getDeltaMovement());

        if (this.level() instanceof ServerLevel && ++this.age >= LIFETIME_TICKS) {
            this.discard();
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
        if (!(heldStack.getItem() instanceof DataCaptureBallItem captureBallItem)) {
            return InteractionResult.PASS;
        }

        boolean captured = captureBallItem.hasRangeCapture(heldStack) ? captureBallItem.captureNearbyDispersingData(heldStack, player, this.getBoundingBox().getCenter()) : captureBallItem.captureDispersingData(heldStack, player, this);
        return captured ? InteractionResult.sidedSuccess(this.level().isClientSide()) : InteractionResult.PASS;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
        if (tag.contains("TextureVariant")) {
            this.setTextureVariant(tag.getInt("TextureVariant"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
        tag.putInt("TextureVariant", this.getTextureVariant());
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

    public static void spawnAt(ServerLevel level, BlockPos pos, RandomSource random) {
        DispersingDataEntity entity = ModEntities.DISPERSING_DATA.get().create(level);
        if (entity == null) {
            return;
        }

        BlockPos spawnPos = findSpawnPos(level, pos);
        double x = spawnPos.getX() + 0.35D + random.nextDouble() * 0.3D;
        double y = spawnPos.getY() + 0.7D + random.nextDouble() * 0.4D;
        double z = spawnPos.getZ() + 0.35D + random.nextDouble() * 0.3D;
        entity.setPos(x, y, z);
        entity.setTextureVariant(random.nextInt(4));
        entity.setDeltaMovement(
                (random.nextDouble() - 0.5D) * 0.08D,
                0.01D + random.nextDouble() * 0.03D,
                (random.nextDouble() - 0.5D) * 0.08D);
        level.addFreshEntity(entity);
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
