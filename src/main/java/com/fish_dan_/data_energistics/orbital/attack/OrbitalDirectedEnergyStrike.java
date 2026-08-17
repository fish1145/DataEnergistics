package com.fish_dan_.data_energistics.orbital.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic, budgeted work geometry for the spiral directed-energy attack.
 *
 * <p>
 * The ordered disk columns are generated once per radius and then addressed by a persisted cursor. A column is
 * visited exactly once, while each Y position in that column consumes one bounded work slot. No block access occurs
 * until the caller has confirmed that every touched chunk is already loaded.
 * </p>
 */
public final class OrbitalDirectedEnergyStrike {

    public static final int MIN_RADIUS = 16;
    public static final int MAX_RADIUS = 256;
    public static final int RADIUS_STEP = 16;
    public static final int MUTATION_BUDGET_PER_TICK = 8_192;

    private static final Map<Integer, List<Offset>> DISK_OFFSETS = new ConcurrentHashMap<>();

    private OrbitalDirectedEnergyStrike() {}

    /**
     * Returns the number of scheduled disk coordinates, which is also the per-coordinate billing multiplier count.
     */
    public static long scheduledCoordinateCount(int radius) {
        return offsetsFor(radius).size();
    }

    /**
     * Returns the total deterministic block positions in one captured scan geometry.
     */
    public static long totalWork(ServerLevel level, BlockPos target, OrbitalAttackGeometry.DirectedEnergy geometry) {
        int bottomY = geometry.bottomY(level, target.getY());
        int topY = level.getMaxBuildHeight() - 1;
        int height = Math.max(0, topY - bottomY + 1);
        if (height <= 0) {
            throw new IllegalArgumentException("Directed-energy geometry has no vertical work range");
        }
        return Math.multiplyExact(scheduledCoordinateCount(geometry.radius()), height);
    }

    /**
     * Processes one bounded slice and applies each column's entity damage when its top position is visited.
     */
    public static WorkSlice applyBudget(
                                        ServerLevel level,
                                        BlockPos target,
                                        OrbitalAttackGeometry.DirectedEnergy geometry,
                                        long cursor,
                                        Set<UUID> exemptions,
                                        float entityDamage) {
        List<Offset> offsets = offsetsFor(geometry.radius());
        int bottomY = geometry.bottomY(level, target.getY());
        int topY = level.getMaxBuildHeight() - 1;
        int height = Math.max(0, topY - bottomY + 1);
        if (height <= 0) {
            throw new IllegalArgumentException("Directed-energy geometry has no vertical work range");
        }
        long total = Math.multiplyExact((long) offsets.size(), height);
        if (cursor < 0L || cursor > total) {
            throw new IllegalArgumentException("Directed-energy work cursor is outside its geometry");
        }

        long next = Math.min(total, cursor + MUTATION_BUDGET_PER_TICK);
        for (long index = cursor; index < next; index++) {
            int offsetIndex = (int) (index / height);
            int yOffset = (int) (index % height);
            Offset offset = offsets.get(offsetIndex);
            int y = topY - yOffset;
            BlockPos position = target.offset(offset.x(), y - target.getY(), offset.z());
            if (yOffset == 0) {
                applyBeamDamage(level, position, bottomY, topY, exemptions, entityDamage);
            }
            if (!level.getBlockState(position).isAir()) {
                level.setBlock(
                        position,
                        Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return new WorkSlice(next, total, next == total);
    }

    /**
     * Returns whether every chunk touched by the scan disk is currently loaded.
     *
     * <p>
     * This guard deliberately does not request chunks synchronously. The future-backed generation and ticket
     * governor will be added around this worker; until then an unloaded target pauses the persisted attack safely.
     * </p>
     */
    public static boolean areTerrainChunksLoaded(ServerLevel level, BlockPos target, int radius) {
        validateRadius(radius);
        int minChunkX = Math.floorDiv(target.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(target.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(target.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(target.getZ() + radius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void validateRadius(int radius) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS || radius % RADIUS_STEP != 0) {
            throw new IllegalArgumentException("Directed-energy radius must be a 16-grid value from 16 to 256");
        }
    }

    private static List<Offset> offsetsFor(int radius) {
        validateRadius(radius);
        return DISK_OFFSETS.computeIfAbsent(radius, OrbitalDirectedEnergyStrike::buildOffsets);
    }

    private static List<Offset> buildOffsets(int radius) {
        int side = radius * 2 + 1;
        int total = Math.multiplyExact(side, side);
        ArrayList<Offset> result = new ArrayList<>();
        int x = 0;
        int z = 0;
        int directionX = 1;
        int directionZ = 0;
        int segmentLength = 1;
        int segmentProgress = 0;
        int segmentCount = 0;
        for (int emitted = 0; emitted < total; emitted++) {
            if ((long) x * x + (long) z * z <= (long) radius * radius) {
                result.add(new Offset(x, z));
            }
            x += directionX;
            z += directionZ;
            if (++segmentProgress == segmentLength) {
                segmentProgress = 0;
                int rotatedX = -directionZ;
                directionZ = directionX;
                directionX = rotatedX;
                if (++segmentCount % 2 == 0) {
                    segmentLength++;
                }
            }
        }
        return List.copyOf(result);
    }

    private static void applyBeamDamage(
                                        ServerLevel level,
                                        BlockPos column,
                                        int bottomY,
                                        int topY,
                                        Set<UUID> exemptions,
                                        float damage) {
        AABB beam = new AABB(
                column.getX(),
                bottomY,
                column.getZ(),
                column.getX() + 1.0D,
                topY + 1.0D,
                column.getZ() + 1.0D);
        for (LivingEntity entity : level.getEntities(
                EntityTypeTest.forClass(LivingEntity.class),
                beam,
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
            entity.hurt(level.damageSources().generic(), damage);
        }
    }

    private record Offset(int x, int z) {}

    /** Result of one bounded directed-energy geometry slice. */
    public record WorkSlice(long nextCursor, long totalWork, boolean complete) {

        public WorkSlice {
            if (nextCursor < 0L || totalWork < 0L || nextCursor > totalWork) {
                throw new IllegalArgumentException("Invalid directed-energy work slice");
            }
        }
    }
}
