package com.fish_dan_.data_energistics.entity;

import com.fish_dan_.data_energistics.config.FlatteningTntConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import org.jetbrains.annotations.Nullable;

public abstract class AbstractFlatteningTntPrimedEntity extends PrimedTnt {

    private static final String TAG_ORIGIN = "Origin";
    private BlockPos origin = BlockPos.ZERO;

    protected AbstractFlatteningTntPrimedEntity(EntityType<? extends AbstractFlatteningTntPrimedEntity> entityType,
                                                Level level) {
        super(entityType, level);
    }

    protected AbstractFlatteningTntPrimedEntity(EntityType<? extends AbstractFlatteningTntPrimedEntity> entityType,
                                                Level level, BlockPos origin, @Nullable LivingEntity owner, BlockState displayBlockState) {
        super(entityType, level);
        this.origin = origin.immutable();
        this.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        double angle = level.random.nextDouble() * (Math.PI * 2.0D);
        this.setDeltaMovement(-Math.sin(angle) * 0.02D, 0.2D, -Math.cos(angle) * 0.02D);
        this.setFuse(80);
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.setBlockState(displayBlockState);
    }

    @Override
    protected void explode() {
        if (this.level().isClientSide) {
            return;
        }

        FlatteningTntConfig.Definition definition = getDefinition();
        Level level = this.level();
        BlockPos center = this.origin.offset(definition.explosionCenterOffset());
        int minY = Math.max(level.getMinBuildHeight(), center.getY() + definition.clearStartYOffset());
        int maxY = Math.min(level.getMaxBuildHeight() - 1,
                center.getY() + definition.clearStartYOffset() + definition.clearHeight() - 1);
        int fillY = center.getY() + definition.fillYOffset();
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        BlockState fillState = definition.fillBlockState();

        if (minY > maxY) {
            return;
        }

        for (int chunkX = centerChunkX - definition.clearChunkRadius(); chunkX <= centerChunkX + definition.clearChunkRadius(); chunkX++) {
            for (int chunkZ = centerChunkZ - definition.clearChunkRadius(); chunkZ <= centerChunkZ + definition.clearChunkRadius(); chunkZ++) {
                BlockPos chunkOrigin = new BlockPos(chunkX << 4, center.getY(), chunkZ << 4);
                if (!level.hasChunkAt(chunkOrigin)) {
                    continue;
                }

                int startX = chunkX << 4;
                int startZ = chunkZ << 4;
                for (int x = startX; x < startX + 16; x++) {
                    for (int z = startZ; z < startZ + 16; z++) {
                        for (int y = minY; y <= maxY; y++) {
                            BlockPos targetPos = new BlockPos(x, y, z);
                            BlockState state = level.getBlockState(targetPos);
                            FluidState fluidState = level.getFluidState(targetPos);
                            if (state.isAir()) {
                                continue;
                            }
                            if (!definition.replaceUnbreakableBlocks() && state.getDestroySpeed(level, targetPos) < 0.0F) {
                                continue;
                            }
                            if (definition.preserveFluids() && !fluidState.isEmpty()) {
                                continue;
                            }
                            if (!state.isAir()) {
                                level.removeBlock(targetPos, false);
                            }
                        }

                        if (fillY >= level.getMinBuildHeight() && fillY < level.getMaxBuildHeight()) {
                            level.setBlock(new BlockPos(x, fillY, z), fillState, 3);
                        }
                    }
                }
            }
        }

        if (fillY < level.getMinBuildHeight() || fillY >= level.getMaxBuildHeight()) {
            return;
        }

        for (int chunkX = centerChunkX - definition.fillChunkRadius(); chunkX <= centerChunkX + definition.fillChunkRadius(); chunkX++) {
            for (int chunkZ = centerChunkZ - definition.fillChunkRadius(); chunkZ <= centerChunkZ + definition.fillChunkRadius(); chunkZ++) {
                BlockPos chunkOrigin = new BlockPos(chunkX << 4, fillY, chunkZ << 4);
                if (!level.hasChunkAt(chunkOrigin)) {
                    continue;
                }

                int startX = chunkX << 4;
                int startZ = chunkZ << 4;
                for (int x = startX; x < startX + 16; x++) {
                    for (int z = startZ; z < startZ + 16; z++) {
                        BlockPos targetPos = new BlockPos(x, fillY, z);
                        BlockState existingState = level.getBlockState(targetPos);
                        FluidState existingFluidState = level.getFluidState(targetPos);
                        if (!definition.replaceUnbreakableBlocks() && existingState.getDestroySpeed(level, targetPos) < 0.0F) {
                            continue;
                        }
                        if (definition.preserveFluids() && !existingFluidState.isEmpty()) {
                            continue;
                        }
                        level.setBlock(targetPos, fillState, 3);
                    }
                }
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong(TAG_ORIGIN, this.origin.asLong());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_ORIGIN)) {
            this.origin = BlockPos.of(tag.getLong(TAG_ORIGIN));
        }
    }

    protected abstract FlatteningTntConfig.Definition getDefinition();
}
