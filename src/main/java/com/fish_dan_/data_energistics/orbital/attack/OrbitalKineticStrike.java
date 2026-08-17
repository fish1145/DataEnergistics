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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Deterministic, bounded work geometry for the first orbital kinetic strike.
 *
 * <p>
 * The column and shallow impact crater are enumerated without allocating a complete world-sized block list. A
 * persisted cursor lets the attack scheduler process at most {@link #MUTATION_BUDGET_PER_TICK} positions per server
 * tick and resume the same geometry after a restart.
 * </p>
 */
public final class OrbitalKineticStrike {

    public static final int COLUMN_RADIUS = 8;
    public static final int COLUMN_DEPTH = 192;
    public static final int CRATER_RADIUS = 24;
    public static final int CRATER_DEPTH = 16;
    public static final int SHOCKWAVE_RADIUS = 64;
    public static final int MUTATION_BUDGET_PER_TICK = 8_192;

    private static final List<Offset> COLUMN_OFFSETS = offsets(COLUMN_RADIUS);
    private static final List<Offset> CRATER_OFFSETS = offsets(CRATER_RADIUS);

    private OrbitalKineticStrike() {}

    /**
     * Returns the total deterministic position count for a target in the supplied world.
     */
    public static long totalWork(ServerLevel level, BlockPos target) {
        return segmentSize(columnHeight(level, target), COLUMN_OFFSETS.size()) + segmentSize(craterHeight(level, target), CRATER_OFFSETS.size());
    }

    /** Returns the exact server geometry position represented by a persisted public work cursor. */
    public static BlockPos workPosition(ServerLevel level, BlockPos target, long cursor) {
        long total = totalWork(level, target);
        if (cursor < 0L || cursor > total) {
            throw new IllegalArgumentException("Kinetic strike cursor is outside its geometry");
        }
        return total == 0L ? target.immutable() : positionAt(level, target, cursor == total ? total - 1L : cursor);
    }

    /**
     * Processes one bounded slice and returns the next cursor. Positions that are already air still consume a cursor
     * slot so that the persisted geometry remains deterministic and does not depend on prior attacks.
     */
    public static WorkSlice applyBudget(ServerLevel level, BlockPos target, long cursor) {
        return applyBudget(
                level,
                target,
                cursor,
                MUTATION_BUDGET_PER_TICK,
                chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null);
    }

    /**
     * Processes a caller-governed slice and stops before the first position whose FULL chunk is not ready. The stopped
     * position does not consume the persisted cursor or mutation allowance.
     */
    public static WorkSlice applyBudget(
                                        ServerLevel level,
                                        BlockPos target,
                                        long cursor,
                                        int mutationBudget,
                                        Predicate<ChunkPos> chunkReady) {
        long total = totalWork(level, target);
        if (cursor < 0L || cursor > total) {
            throw new IllegalArgumentException("Kinetic strike cursor is outside its geometry");
        }
        if (mutationBudget <= 0) {
            throw new IllegalArgumentException("Kinetic strike mutation budget must be positive");
        }
        long next = cursor;
        int visited = 0;
        while (next < total && visited < mutationBudget) {
            BlockPos position = positionAt(level, target, next);
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
    public static void applyImpactDamage(ServerLevel level, BlockPos target, Set<UUID> exemptions) {
        Vec3 center = Vec3.atCenterOf(target);
        AABB area = new AABB(center, center).inflate(SHOCKWAVE_RADIUS);
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
            if (direction.lengthSqr() > (long) SHOCKWAVE_RADIUS * SHOCKWAVE_RADIUS) {
                continue;
            }
            entity.hurt(level.damageSources().generic(), 500.0F);
            if (direction.lengthSqr() < 1.0E-6D) {
                direction = new Vec3(1.0D, 0.0D, 0.0D);
            }
            entity.knockback(4.0D, -direction.x, -direction.z);
            entity.hurtMarked = true;
        }
    }

    private static long segmentSize(int height, int offsetCount) {
        return Math.multiplyExact((long) Math.max(height, 0), offsetCount);
    }

    private static int columnHeight(ServerLevel level, BlockPos target) {
        int top = level.getMaxBuildHeight() - 1;
        int bottom = Math.max(level.getMinBuildHeight(), target.getY() - COLUMN_DEPTH);
        return Math.max(0, top - bottom + 1);
    }

    private static int craterHeight(ServerLevel level, BlockPos target) {
        int top = target.getY() - 1;
        int bottom = Math.max(level.getMinBuildHeight(), target.getY() - CRATER_DEPTH);
        return Math.max(0, top - bottom + 1);
    }

    private static BlockPos positionAt(ServerLevel level, BlockPos target, long index) {
        long columnCount = segmentSize(columnHeight(level, target), COLUMN_OFFSETS.size());
        if (index < columnCount) {
            return segmentPosition(
                    target,
                    index,
                    COLUMN_OFFSETS,
                    level.getMaxBuildHeight() - 1,
                    COLUMN_OFFSETS.size());
        }
        return segmentPosition(
                target,
                index - columnCount,
                CRATER_OFFSETS,
                target.getY() - 1,
                CRATER_OFFSETS.size());
    }

    private static BlockPos segmentPosition(
                                            BlockPos target,
                                            long index,
                                            List<Offset> offsets,
                                            int topY,
                                            int offsetCount) {
        int offsetIndex = (int) (index % offsetCount);
        int y = topY - (int) (index / offsetCount);
        Offset offset = offsets.get(offsetIndex);
        return target.offset(offset.x(), y - target.getY(), offset.z());
    }

    private static List<Offset> offsets(int radius) {
        ArrayList<Offset> result = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if ((long) x * x + (long) z * z <= (long) radius * radius) {
                    result.add(new Offset(x, z));
                }
            }
        }
        return List.copyOf(result);
    }

    private record Offset(int x, int z) {}

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
