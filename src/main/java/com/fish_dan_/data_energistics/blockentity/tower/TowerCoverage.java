package com.fish_dan_.data_energistics.blockentity.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

/**
 * Describes the spatial coverage owned by a Data Distribution Tower.
 *
 * <p>
 * The tower needs one boundary for chunk radius, vertical reach, and chunk index keys so block entity logic can ask
 * range questions without duplicating coordinate math.
 */
public interface TowerCoverage {

    /**
     * Computes the server-side chunk radius from the configured base range and booster count.
     *
     * @param boosterCount amount of wireless boosters installed in the tower
     * @return horizontal chunk radius around the tower base
     */
    int computeChunkRadius(int boosterCount);

    /**
     * Returns the number of chunks covered by the supplied radius.
     *
     * @param chunkRadius radius returned by {@link #computeChunkRadius(int)}
     * @return covered chunk count
     */
    int coveredChunkCount(int chunkRadius);

    /**
     * Checks whether a target block position is inside tower coverage.
     *
     * @param targetPos   target position being tested
     * @param chunkRadius active horizontal chunk radius
     * @return true when the target is horizontally and vertically covered
     */
    boolean contains(BlockPos targetPos, int chunkRadius);

    /**
     * Builds the render/query bounding box for the active range.
     *
     * @param level       optional level used to clamp build height on server/client
     * @param chunkRadius active horizontal chunk radius
     * @return coverage AABB
     */
    AABB aabb(@Nullable Level level, int chunkRadius);

    /**
     * Returns the minimum covered chunk X.
     *
     * @param chunkRadius active horizontal chunk radius
     * @return chunk coordinate
     */
    int minChunkX(int chunkRadius);

    /**
     * Returns the maximum covered chunk X.
     *
     * @param chunkRadius active horizontal chunk radius
     * @return chunk coordinate
     */
    int maxChunkX(int chunkRadius);

    /**
     * Returns the minimum covered chunk Z.
     *
     * @param chunkRadius active horizontal chunk radius
     * @return chunk coordinate
     */
    int minChunkZ(int chunkRadius);

    /**
     * Returns the maximum covered chunk Z.
     *
     * @param chunkRadius active horizontal chunk radius
     * @return chunk coordinate
     */
    int maxChunkZ(int chunkRadius);
}
