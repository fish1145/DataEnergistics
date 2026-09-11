package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamScan;
import com.fish_dan_.data_energistics.orbital.attack.entity.OrbitalEntityErasure;
import com.fish_dan_.data_energistics.orbital.attack.entity.geometry.OrbitalEntityHitGeometry;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureStrike;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.function.Predicate;

/**
 * Deterministic, bounded work geometry for the first orbital kinetic strike.
 *
 * <p>
 * The column and shallow impact crater are enumerated without allocating a complete world-sized block list. A
 * persisted cursor lets the attack scheduler process a caller-budgeted number of positions per server tick and resume
 * the same captured geometry after a restart.
 * </p>
 */
public final class OrbitalKineticStrike {

    private static final Int2ObjectOpenHashMap<DiskGeometry> DISK_GEOMETRIES = new Int2ObjectOpenHashMap<>();

    private OrbitalKineticStrike() {}

    /**
     * Returns the total deterministic position count for a target in the supplied world.
     */
    public static long totalWork(
                                 ServerLevel level,
                                 BlockPos target,
                                 OrbitalAttackGeometry.Kinetic geometry) {
        DiskGeometry column = disk(geometry.columnRadius());
        DiskGeometry crater = disk(geometry.craterRadius());
        return totalWork(level, target, geometry, column, crater);
    }

    /** Returns the exact server geometry position represented by a persisted public work cursor. */
    public static BlockPos workPosition(
                                        ServerLevel level,
                                        BlockPos target,
                                        OrbitalAttackGeometry.Kinetic geometry,
                                        long cursor) {
        DiskGeometry column = disk(geometry.columnRadius());
        DiskGeometry crater = disk(geometry.craterRadius());
        long total = totalWork(level, target, geometry, column, crater);
        if (cursor < 0L || cursor > total) {
            throw new IllegalArgumentException("Kinetic strike cursor is outside its geometry");
        }
        return total == 0L ? target.immutable() : positionAt(
                level,
                target,
                geometry,
                column,
                crater,
                cursor == total ? total - 1L : cursor);
    }

    /**
     * Processes a caller-governed slice and stops before the first position whose FULL chunk is not ready. The stopped
     * position does not consume the persisted cursor or mutation allowance.
     */
    public static WorkSlice applyBudget(
                                        ServerLevel level,
                                        BlockPos target,
                                        OrbitalAttackGeometry.Kinetic geometry,
                                        long cursor,
                                        int mutationBudget,
                                        Predicate<ChunkPos> chunkReady) {
        DiskGeometry column = disk(geometry.columnRadius());
        DiskGeometry crater = disk(geometry.craterRadius());
        long total = totalWork(level, target, geometry, column, crater);
        if (cursor < 0L || cursor > total) {
            throw new IllegalArgumentException("Kinetic strike cursor is outside its geometry");
        }
        if (mutationBudget <= 0) {
            throw new IllegalArgumentException("Kinetic strike mutation budget must be positive");
        }
        long columnWork = segmentSize(columnHeight(level, target, geometry), column.coordinateCount());
        int craterTop = craterTopY(level, target, geometry);
        int craterBottom = craterBottom(level, target, geometry);
        long next = cursor;
        int visited = 0;
        while (next < total && visited < mutationBudget) {
            BlockPos position = positionAt(level, target, geometry, column, crater, next);
            if (next < columnWork || geometry.containsCraterPosition(target, position, craterTop, craterBottom)) {
                if (!chunkReady.test(new ChunkPos(position))) {
                    return new WorkSlice(next, total, false, true);
                }
                if (!level.getBlockState(position).isAir()) {
                    level.setBlock(
                            position,
                            Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
            next++;
            visited++;
        }
        return new WorkSlice(next, total, next == total, false);
    }

    /**
     * Erases non-exempt entities in the impact volume on the commit tick before budgeted terrain work.
     */
    public static void eraseImpactEntities(
                                           ServerLevel level,
                                           BlockPos target,
                                           OrbitalAttackGeometry.Kinetic geometry,
                                           OrbitalErasureStrike strike) {
        Vec3 center = Vec3.atCenterOf(target);
        int bottom = columnBottom(level, target, geometry);
        double top = OrbitalBeamScan.muzzle(target, level.getMaxBuildHeight() - 1).y;
        double columnExtent = geometry.columnRadius() + 0.5;
        int craterTop = craterTopY(level, target, geometry);
        int craterBottom = craterBottom(level, target, geometry);
        double craterExtent = geometry.craterRadius() + 0.5;
        AABB area = new AABB(center, center).inflate(geometry.shockwaveRadius())
                .minmax(new AABB(center.x - craterExtent, craterBottom, center.z - craterExtent,
                        center.x + craterExtent, craterTop + 1, center.z + craterExtent))
                .inflate(OrbitalEntityHitGeometry.CONTACT_EPSILON);
        for (Entity entity : level.getEntities((Entity) null, area,
                candidate -> OrbitalEntityHitGeometry.intersectsSphere(candidate.getBoundingBox(), center, geometry.shockwaveRadius()) || OrbitalEntityHitGeometry.intersectsCrater(candidate.getBoundingBox(), target, geometry, craterBottom, craterTop))) {
            OrbitalEntityErasure.eraseHit(entity, strike);
        }
        AABB column = new AABB(center.x - columnExtent, bottom, center.z - columnExtent,
                center.x + columnExtent, top, center.z + columnExtent).inflate(OrbitalEntityHitGeometry.CONTACT_EPSILON);
        for (Entity entity : level.getEntities((Entity) null, column,
                candidate -> OrbitalEntityHitGeometry.intersectsVerticalColumn(candidate.getBoundingBox(), center, geometry.columnRadius(), bottom, top))) {
            OrbitalEntityErasure.eraseHit(entity, strike);
        }
    }

    private static int craterTopY(ServerLevel level, BlockPos target, OrbitalAttackGeometry.Kinetic geometry) {
        if (geometry.craterTopY() != OrbitalAttackGeometry.Kinetic.UNCAPTURED_CRATER_TOP) {
            return geometry.craterTopY();
        }
        return Math.max(target.getY() - 1, level.getHeight(Heightmap.Types.WORLD_SURFACE, target.getX(), target.getZ()) - 1);
    }

    private static int craterBottom(ServerLevel level, BlockPos target, OrbitalAttackGeometry.Kinetic geometry) {
        return (int) Math.max(level.getMinBuildHeight(), (long) target.getY() - geometry.craterDepth());
    }

    private static long segmentSize(int height, int offsetCount) {
        return Math.multiplyExact((long) Math.max(height, 0), offsetCount);
    }

    private static int columnHeight(
                                    ServerLevel level,
                                    BlockPos target,
                                    OrbitalAttackGeometry.Kinetic geometry) {
        int top = level.getMaxBuildHeight() - 1;
        int bottom = columnBottom(level, target, geometry);
        return Math.max(0, top - bottom + 1);
    }

    private static int columnBottom(ServerLevel level, BlockPos target, OrbitalAttackGeometry.Kinetic geometry) {
        return (int) Math.max(level.getMinBuildHeight(), (long) target.getY() - geometry.columnDepth());
    }

    private static int craterHeight(
                                    ServerLevel level,
                                    BlockPos target,
                                    OrbitalAttackGeometry.Kinetic geometry) {
        int top = craterTopY(level, target, geometry);
        int bottom = craterBottom(level, target, geometry);
        return Math.max(0, top - bottom + 1);
    }

    private static long totalWork(
                                  ServerLevel level,
                                  BlockPos target,
                                  OrbitalAttackGeometry.Kinetic geometry,
                                  DiskGeometry column,
                                  DiskGeometry crater) {
        return Math.addExact(
                segmentSize(columnHeight(level, target, geometry), column.coordinateCount()),
                segmentSize(craterHeight(level, target, geometry), crater.coordinateCount()));
    }

    private static BlockPos positionAt(
                                       ServerLevel level,
                                       BlockPos target,
                                       OrbitalAttackGeometry.Kinetic geometry,
                                       DiskGeometry column,
                                       DiskGeometry crater,
                                       long index) {
        long columnCount = segmentSize(
                columnHeight(level, target, geometry),
                column.coordinateCount());
        if (index < columnCount) {
            return segmentPosition(
                    target,
                    index,
                    column,
                    level.getMaxBuildHeight() - 1);
        }
        return segmentPosition(
                target,
                index - columnCount,
                crater,
                craterTopY(level, target, geometry));
    }

    private static BlockPos segmentPosition(
                                            BlockPos target,
                                            long index,
                                            DiskGeometry disk,
                                            int topY) {
        int offsetIndex = (int) (index % disk.coordinateCount());
        int y = topY - (int) (index / disk.coordinateCount());
        Offset offset = disk.offsetAt(offsetIndex);
        return target.offset(offset.x(), y - target.getY(), offset.z());
    }

    private static synchronized DiskGeometry disk(int radius) {
        return DISK_GEOMETRIES.computeIfAbsent(radius, DiskGeometry::create);
    }

    private record Offset(int x, int z) {}

    /**
     * Compact deterministic disk index. Its row table preserves the old x-then-z circle enumeration without caching
     * every block offset for every configured radius.
     */
    private record DiskGeometry(int radius, int[] rowStarts) {

        private static DiskGeometry create(int radius) {
            int[] rowStarts = new int[Math.addExact(Math.multiplyExact(radius, 2), 2)];
            long radiusSquared = (long) radius * radius;
            for (int row = 0; row <= radius * 2; row++) {
                int x = row - radius;
                int zLimit = (int) Math.floor(Math.sqrt(radiusSquared - (long) x * x));
                rowStarts[row + 1] = Math.addExact(rowStarts[row], Math.addExact(zLimit * 2, 1));
            }
            return new DiskGeometry(radius, rowStarts);
        }

        private int coordinateCount() {
            return this.rowStarts[this.rowStarts.length - 1];
        }

        private Offset offsetAt(int index) {
            if (index < 0 || index >= coordinateCount()) {
                throw new IllegalArgumentException("Kinetic disk offset is outside its geometry");
            }
            int low = 0;
            int high = this.rowStarts.length - 2;
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                if (this.rowStarts[middle] <= index) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            int x = low - this.radius;
            int zLimit = (this.rowStarts[low + 1] - this.rowStarts[low] - 1) / 2;
            int z = -zLimit + index - this.rowStarts[low];
            return new Offset(x, z);
        }
    }

    /**
     * Result of one bounded geometry slice.
     */
    public record WorkSlice(long nextCursor, long totalWork, boolean complete, boolean waitingForChunk) {

        public WorkSlice {
            if (nextCursor < 0L || totalWork < 0L || nextCursor > totalWork) {
                throw new IllegalArgumentException("Invalid kinetic work slice");
            }
            if (complete && waitingForChunk) {
                throw new IllegalArgumentException("A complete kinetic slice cannot wait for a chunk");
            }
        }
    }
}
