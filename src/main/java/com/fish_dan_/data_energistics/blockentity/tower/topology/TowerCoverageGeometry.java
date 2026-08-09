package com.fish_dan_.data_energistics.blockentity.tower.topology;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * Coordinate geometry for the spatial coverage owned by a Data Distribution Tower.
 *
 * <p>
 * The tower needs one boundary for chunk radius, vertical reach, and chunk index keys so block entity logic can ask
 * range questions without duplicating coordinate math.
 * </p>
 */
public final class TowerCoverageGeometry {

    private static final int BOOSTERS_PER_CHUNK_RING = 16;
    private static final int VERTICAL_RANGE_ABOVE = 256;
    private static final int VERTICAL_RANGE_BELOW = 128;

    private final BlockPos origin;
    private final IntSupplier baseRange;

    /**
     * Creates coverage math anchored at the tower base position.
     *
     * @param origin tower base position used as coverage center
     */
    public TowerCoverageGeometry(BlockPos origin) {
        this(origin, () -> DataEnergisticsConfiguration.INSTANCE.dataDistributionTower().range());
    }

    TowerCoverageGeometry(BlockPos origin, IntSupplier baseRange) {
        this.origin = origin.immutable();
        this.baseRange = baseRange;
    }

    /**
     * Computes the server-side chunk radius from the configured base range and booster count.
     *
     * @param boosterCount amount of wireless boosters installed in the tower
     * @return horizontal chunk radius around the tower base
     */
    public int computeChunkRadius(int boosterCount) {
        return Math.max(0, this.baseRange.getAsInt() - 1 + boosterCount / BOOSTERS_PER_CHUNK_RING);
    }

    /**
     * Returns the number of chunks covered by the supplied radius.
     *
     * @param chunkRadius radius returned by {@link #computeChunkRadius(int)}
     * @return covered chunk count
     */
    public int coveredChunkCount(int chunkRadius) {
        int diameter = chunkRadius * 2 + 1;
        return diameter * diameter;
    }

    /**
     * Checks whether a target block position is inside tower coverage.
     *
     * @param targetPos   target position being tested
     * @param chunkRadius active horizontal chunk radius
     * @return true when the target is horizontally and vertically covered
     */
    public boolean contains(BlockPos targetPos, int chunkRadius) {
        return isWithinCenteredHorizontalRange(targetPos, chunkRadius) && targetPos.getY() >= this.origin.getY() - VERTICAL_RANGE_BELOW && targetPos.getY() <= this.origin.getY() + VERTICAL_RANGE_ABOVE;
    }

    /**
     * Builds the render/query bounding box for the active range.
     *
     * @param level       optional level used to clamp build height on server/client
     * @param chunkRadius active horizontal chunk radius
     * @return coverage AABB
     */
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

    /**
     * Returns the minimum covered chunk X.
     *
     * @param chunkRadius active horizontal chunk radius
     * @return chunk coordinate
     */
    public int minChunkX(int chunkRadius) {
        return Math.floorDiv((int) Math.floor(minX(chunkRadius)), 16);
    }

    /**
     * Returns the maximum covered chunk X.
     *
     * @param chunkRadius active horizontal chunk radius
     * @return chunk coordinate
     */
    public int maxChunkX(int chunkRadius) {
        return Math.floorDiv((int) Math.ceil(maxX(chunkRadius)) - 1, 16);
    }

    /**
     * Returns the minimum covered chunk Z.
     *
     * @param chunkRadius active horizontal chunk radius
     * @return chunk coordinate
     */
    public int minChunkZ(int chunkRadius) {
        return Math.floorDiv((int) Math.floor(minZ(chunkRadius)), 16);
    }

    /**
     * Returns the maximum covered chunk Z.
     *
     * @param chunkRadius active horizontal chunk radius
     * @return chunk coordinate
     */
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
