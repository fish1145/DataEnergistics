package com.fish_dan_.data_energistics.orbital.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final Map<Integer, DiskGeometry> DISK_GEOMETRIES = new ConcurrentHashMap<>();

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
        long next = cursor;
        int visited = 0;
        while (next < total && visited < mutationBudget) {
            BlockPos position = positionAt(level, target, geometry, column, crater, next);
            if (!chunkReady.test(new ChunkPos(position))) {
                return new WorkSlice(next, total, false, true);
            }
            if (!level.getBlockState(position).isAir()) {
                level.setBlock(
                        position,
                        Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
            next++;
            visited++;
        }
        return new WorkSlice(next, total, next == total, false);
    }

    /**
     * Applies the instantaneous impact damage and knockback on the commit tick before budgeted terrain work.
     */
    public static void applyImpactDamage(
                                         ServerLevel level,
                                         BlockPos target,
                                         OrbitalAttackGeometry.Kinetic geometry,
                                         Set<UUID> exemptions) {
        Vec3 center = Vec3.atCenterOf(target);
        AABB area = new AABB(center, center).inflate(geometry.shockwaveRadius());
        for (LivingEntity entity : level.getEntities(
                EntityTypeTest.forClass(LivingEntity.class),
                area,
                LivingEntity::isAlive)) {
            if (exemptions.contains(entity.getUUID())) {
                continue;
            }
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            if (entity instanceof ServerPlayer player && player.hasPermissions(2)) {
                continue;
            }
            Vec3 direction = entity.position().subtract(center);
            if (direction.lengthSqr() > (long) geometry.shockwaveRadius() * geometry.shockwaveRadius()) {
                continue;
            }
            entity.hurt(level.damageSources().generic(), (float) geometry.entityDamage());
            if (direction.lengthSqr() < 1.0E-6D) {
                direction = new Vec3(1.0D, 0.0D, 0.0D);
            }
            entity.knockback(geometry.knockbackStrength(), -direction.x, -direction.z);
            entity.hurtMarked = true;
        }
    }

    private static long segmentSize(int height, int offsetCount) {
        return Math.multiplyExact((long) Math.max(height, 0), offsetCount);
    }

    private static int columnHeight(
                                    ServerLevel level,
                                    BlockPos target,
                                    OrbitalAttackGeometry.Kinetic geometry) {
        int top = level.getMaxBuildHeight() - 1;
        int bottom = (int) Math.max(
                level.getMinBuildHeight(),
                (long) target.getY() - geometry.columnDepth());
        return Math.max(0, top - bottom + 1);
    }

    private static int craterHeight(
                                    ServerLevel level,
                                    BlockPos target,
                                    OrbitalAttackGeometry.Kinetic geometry) {
        int top = target.getY() - 1;
        int bottom = (int) Math.max(
                level.getMinBuildHeight(),
                (long) target.getY() - geometry.craterDepth());
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
                target.getY() - 1);
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

    private static DiskGeometry disk(int radius) {
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
