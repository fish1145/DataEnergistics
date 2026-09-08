package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamScan;
import com.fish_dan_.data_energistics.orbital.attack.entity.OrbitalEntityErasure;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureStrike;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

/**
 * Deterministic, budgeted work geometry for the spiral directed-energy attack.
 *
 * <p>
 * The fixed muzzle aims through ordered disk coordinates. Every voxel crossed by each ray consumes one bounded
 * work slot. The same traversal supplies the public beam geometry; world access waits for the current FULL chunk.
 * </p>
 */
public final class OrbitalDirectedEnergyStrike {

    private OrbitalDirectedEnergyStrike() {}

    /**
     * Returns the number of scheduled disk coordinates, which is also the per-coordinate billing multiplier count.
     */
    public static long scheduledCoordinateCount(int radius) {
        validateSupportedRadius(radius);
        long radiusSquared = (long) radius * radius;
        long coordinates = 2L * radius + 1L;
        for (int offsetZ = 1; offsetZ <= radius; offsetZ++) {
            long maximumXSquared = radiusSquared - (long) offsetZ * offsetZ;
            int maximumX = (int) Math.sqrt(maximumXSquared);
            while ((long) (maximumX + 1) * (maximumX + 1) <= maximumXSquared) {
                maximumX++;
            }
            while ((long) maximumX * maximumX > maximumXSquared) {
                maximumX--;
            }
            coordinates += 2L * (2L * maximumX + 1L);
        }
        return coordinates;
    }

    /**
     * Returns the total deterministic block positions in one captured scan geometry.
     */
    public static long totalWork(ServerLevel level, BlockPos target, OrbitalAttackGeometry.DirectedEnergy geometry) {
        return scan(level, target, geometry).totalWork();
    }

    /** Returns the exact beam block position represented by a persisted public work cursor. */
    public static BlockPos workPosition(
                                        ServerLevel level,
                                        BlockPos target,
                                        OrbitalAttackGeometry.DirectedEnergy geometry,
                                        long cursor) {
        OrbitalBeamScan scan = scan(level, target, geometry);
        long total = scan.totalWork();
        if (cursor < 0L || cursor > total) {
            throw new IllegalArgumentException("Directed-energy work cursor is outside its geometry");
        }
        long positionCursor = cursor == total ? total - 1L : cursor;
        return scan.walker(positionCursor).position();
    }

    /**
     * Processes a caller-governed slice and stops before accessing the first disk column whose FULL chunk is pending.
     * Entities are erased when the advancing beam first intersects their occupied volume; entities below the
     * current beam position remain untouched until the scan reaches them.
     */
    public static WorkSlice applyBudget(
                                        ServerLevel level,
                                        BlockPos target,
                                        OrbitalAttackGeometry.DirectedEnergy geometry,
                                        long cursor,
                                        OrbitalErasureStrike strike,
                                        int mutationBudget,
                                        Predicate<ChunkPos> chunkReady) {
        OrbitalBeamScan scan = scan(level, target, geometry);
        long total = scan.totalWork();
        if (cursor < 0L || cursor > total) {
            throw new IllegalArgumentException("Directed-energy work cursor is outside its geometry");
        }
        if (mutationBudget <= 0) {
            throw new IllegalArgumentException("Directed-energy mutation budget must be positive");
        }
        if (cursor == total) {
            return new WorkSlice(cursor, total, true, false);
        }

        OrbitalBeamScan.Walker walker = scan.walker(cursor);
        long next = cursor;
        int visited = 0;
        while (next < total && visited < mutationBudget) {
            BlockPos position = walker.position();
            if (!chunkReady.test(new ChunkPos(position))) {
                return new WorkSlice(next, total, false, true);
            }
            eraseBeamEntities(level, position, strike);
            if (!level.getBlockState(position).isAir()) {
                level.setBlock(
                        position,
                        Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
            next++;
            visited++;
            walker.advance();
        }
        return new WorkSlice(next, total, next == total, false);
    }

    /** Shared trajectory for world mutation and synchronized rendering. No world/chunk access is performed. */
    public static OrbitalBeamScan scan(ServerLevel level, BlockPos target, OrbitalAttackGeometry.DirectedEnergy geometry) {
        return new OrbitalBeamScan(target, level.getMaxBuildHeight() - 1, geometry.bottomY(level, target.getY()),
                geometry.radius(), geometry.path());
    }

    /** Validates a player-selected radius against the current server grid. */
    public static void validateRadius(
                                      int radius,
                                      DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        int minimum = settings.directedEnergyMinimumRadius;
        int maximum = settings.directedEnergyMaximumRadius;
        int step = settings.directedEnergyRadiusStep;
        if (minimum < 1 || maximum > OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_RADIUS || minimum > maximum || step < 1 || step > OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_RADIUS) {
            throw new IllegalStateException("Invalid directed-energy radius configuration");
        }
        if (radius < minimum || radius > maximum || Math.floorMod(radius - minimum, step) != 0) {
            throw new IllegalArgumentException("Directed-energy radius is outside the configured server grid");
        }
    }

    /** Validates the immutable protocol and persisted-geometry safety envelope. */
    public static void validateSupportedRadius(int radius) {
        if (radius < 1 || radius > OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_RADIUS) {
            throw new IllegalArgumentException("Directed-energy radius is outside the supported range");
        }
    }

    private static void eraseBeamEntities(
                                          ServerLevel level,
                                          BlockPos column,
                                          OrbitalErasureStrike strike) {
        AABB beam = new AABB(
                column.getX(),
                column.getY(),
                column.getZ(),
                column.getX() + 1.0D,
                column.getY() + 1.0D,
                column.getZ() + 1.0D);
        for (Entity entity : level.getEntities(null, beam)) {
            OrbitalEntityErasure.eraseHit(entity, strike);
        }
    }

    /** Result of one bounded directed-energy geometry slice. */
    public record WorkSlice(long nextCursor, long totalWork, boolean complete, boolean waitingForChunk) {

        public WorkSlice {
            if (nextCursor < 0L || totalWork < 0L || nextCursor > totalWork) {
                throw new IllegalArgumentException("Invalid directed-energy work slice");
            }
            if (complete && waitingForChunk) {
                throw new IllegalArgumentException("A complete directed-energy slice cannot wait for a chunk");
            }
        }
    }
}
