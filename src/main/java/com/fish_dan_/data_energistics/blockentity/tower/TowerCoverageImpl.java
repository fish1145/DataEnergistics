package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

/**
 * Default coordinate implementation for Data Distribution Tower coverage.
 */
public final class TowerCoverageImpl implements TowerCoverage {

    private static final int BOOSTERS_PER_CHUNK_RING = 16;
    private static final int VERTICAL_RANGE_ABOVE = 256;
    private static final int VERTICAL_RANGE_BELOW = 128;

    private final BlockPos origin;

    /**
     * Creates coverage math anchored at the tower base position.
     *
     * @param origin tower base position used as coverage center
     */
    public TowerCoverageImpl(BlockPos origin) {
        this.origin = origin.immutable();
    }

    @Override
    public int computeChunkRadius(int boosterCount) {
        return Math.max(0, Config.dataDistributionTowerRange - 1 + boosterCount / BOOSTERS_PER_CHUNK_RING);
    }

    @Override
    public int coveredChunkCount(int chunkRadius) {
        int diameter = chunkRadius * 2 + 1;
        return diameter * diameter;
    }

    @Override
    public boolean contains(BlockPos targetPos, int chunkRadius) {
        return isWithinCenteredHorizontalRange(targetPos, chunkRadius) && targetPos.getY() >= this.origin.getY() - VERTICAL_RANGE_BELOW && targetPos.getY() <= this.origin.getY() + VERTICAL_RANGE_ABOVE;
    }

    @Override
    public AABB aabb(@Nullable Level level, int chunkRadius) {
        double minX = minX(chunkRadius);
        double minZ = minZ(chunkRadius);
        double maxX = maxX(chunkRadius);
        double maxZ = maxZ(chunkRadius);
        int minY = this.origin.getY() - VERTICAL_RANGE_BELOW;
        int maxY = this.origin.getY() + VERTICAL_RANGE_ABOVE + 1;

        if (level == null) {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return new AABB(
                minX,
                Math.max(level.getMinBuildHeight(), minY),
                minZ,
                maxX,
                Math.min(level.getMaxBuildHeight(), maxY),
                maxZ);
    }

    @Override
    public int minChunkX(int chunkRadius) {
        return Math.floorDiv((int) Math.floor(minX(chunkRadius)), 16);
    }

    @Override
    public int maxChunkX(int chunkRadius) {
        return Math.floorDiv((int) Math.ceil(maxX(chunkRadius)) - 1, 16);
    }

    @Override
    public int minChunkZ(int chunkRadius) {
        return Math.floorDiv((int) Math.floor(minZ(chunkRadius)), 16);
    }

    @Override
    public int maxChunkZ(int chunkRadius) {
        return Math.floorDiv((int) Math.ceil(maxZ(chunkRadius)) - 1, 16);
    }

    private boolean isWithinCenteredHorizontalRange(BlockPos targetPos, int chunkRadius) {
        double targetCenterX = targetPos.getX() + 0.5D;
        double targetCenterZ = targetPos.getZ() + 0.5D;
        return targetCenterX >= minX(chunkRadius) && targetCenterX < maxX(chunkRadius) && targetCenterZ >= minZ(chunkRadius) && targetCenterZ < maxZ(chunkRadius);
    }

    private double minX(int chunkRadius) {
        return this.origin.getX() + 0.5D - halfWidth(chunkRadius);
    }

    private double minZ(int chunkRadius) {
        return this.origin.getZ() + 0.5D - halfWidth(chunkRadius);
    }

    private double maxX(int chunkRadius) {
        return this.origin.getX() + 0.5D + halfWidth(chunkRadius);
    }

    private double maxZ(int chunkRadius) {
        return this.origin.getZ() + 0.5D + halfWidth(chunkRadius);
    }

    private double halfWidth(int chunkRadius) {
        return diameterBlocks(chunkRadius) / 2.0D;
    }

    private int diameterBlocks(int chunkRadius) {
        return (chunkRadius * 2 + 1) * 16;
    }
}
