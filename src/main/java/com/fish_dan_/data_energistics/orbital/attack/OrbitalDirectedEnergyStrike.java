package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Deterministic, budgeted work geometry for the spiral directed-energy attack.
 *
 * <p>
 * The ordered disk columns are generated once per radius and then addressed by a persisted cursor. A column is
 * visited exactly once, while each Y position in that column consumes one bounded work slot. No block access occurs
 * after the caller has acquired the current position's FULL chunk through the asynchronous terrain scheduler.
 * </p>
 */
public final class OrbitalDirectedEnergyStrike {

    private static final int MAX_CACHED_RADII = 16;
    private static final LinkedHashMap<Integer, List<Offset>> DISK_OFFSETS =
            new LinkedHashMap<>(MAX_CACHED_RADII, 0.75F, true);

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
        if (height == 0) {
            throw new IllegalArgumentException("Directed-energy geometry has no vertical work range");
        }
        return Math.multiplyExact(scheduledCoordinateCount(geometry.radius()), height);
    }

    /** Returns the exact beam block position represented by a persisted public work cursor. */
    public static BlockPos workPosition(
                                        ServerLevel level,
                                        BlockPos target,
                                        OrbitalAttackGeometry.DirectedEnergy geometry,
                                        long cursor) {
        List<Offset> offsets = offsetsFor(geometry.radius());
        int bottomY = geometry.bottomY(level, target.getY());
        int topY = level.getMaxBuildHeight() - 1;
        int height = Math.max(0, topY - bottomY + 1);
        long total = Math.multiplyExact((long) offsets.size(), height);
        if (height == 0 || cursor < 0L || cursor > total) {
            throw new IllegalArgumentException("Directed-energy work cursor is outside its geometry");
        }
        long positionCursor = cursor == total ? total - 1L : cursor;
        return positionAt(target, offsets, topY, height, positionCursor);
    }

    /**
     * Processes a caller-governed slice and stops before accessing the first disk column whose FULL chunk is pending.
     * The feet-Y filter makes one entity receive one captured-damage hit per disk column when the beam reaches its
     * occupied level, instead of damaging every entity at the column top.
     */
    public static WorkSlice applyBudget(
                                        ServerLevel level,
                                        BlockPos target,
                                        OrbitalAttackGeometry.DirectedEnergy geometry,
                                        long cursor,
                                        Set<UUID> exemptions,
                                        int mutationBudget,
                                        Predicate<ChunkPos> chunkReady) {
        List<Offset> offsets = offsetsFor(geometry.radius());
        int bottomY = geometry.bottomY(level, target.getY());
        int topY = level.getMaxBuildHeight() - 1;
        int height = Math.max(0, topY - bottomY + 1);
        if (height == 0) {
            throw new IllegalArgumentException("Directed-energy geometry has no vertical work range");
        }
        long total = Math.multiplyExact((long) offsets.size(), height);
        if (cursor < 0L || cursor > total) {
            throw new IllegalArgumentException("Directed-energy work cursor is outside its geometry");
        }
        if (mutationBudget <= 0) {
            throw new IllegalArgumentException("Directed-energy mutation budget must be positive");
        }

        long next = cursor;
        int visited = 0;
        while (next < total && visited < mutationBudget) {
            BlockPos position = positionAt(target, offsets, topY, height, next);
            if (!chunkReady.test(new ChunkPos(position))) {
                return new WorkSlice(next, total, false, true);
            }
            applyBeamDamage(level, position, exemptions, (float) geometry.entityDamage());
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

    private static BlockPos positionAt(
                                       BlockPos target,
                                       List<Offset> offsets,
                                       int topY,
                                       int height,
                                       long cursor) {
        int offsetIndex = (int) (cursor / height);
        int yOffset = (int) (cursor % height);
        Offset offset = offsets.get(offsetIndex);
        int y = topY - yOffset;
        return target.offset(offset.x(), y - target.getY(), offset.z());
    }

    /** Validates a player-selected radius against the current server grid. */
    public static void validateRadius(
                                      int radius,
                                      DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        int minimum = settings.directedEnergyMinimumRadius;
        int maximum = settings.directedEnergyMaximumRadius;
        int step = settings.directedEnergyRadiusStep;
        if (minimum < 1
                || maximum > OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_RADIUS
                || minimum > maximum
                || step < 1
                || step > OrbitalAttackGeometry.DirectedEnergy.MAX_SUPPORTED_RADIUS) {
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

    private static synchronized List<Offset> offsetsFor(int radius) {
        validateSupportedRadius(radius);
        List<Offset> cached = DISK_OFFSETS.get(radius);
        if (cached != null) {
            return cached;
        }
        List<Offset> offsets = buildOffsets(radius);
        DISK_OFFSETS.put(radius, offsets);
        if (DISK_OFFSETS.size() > MAX_CACHED_RADII) {
            DISK_OFFSETS.remove(DISK_OFFSETS.keySet().iterator().next());
        }
        return offsets;
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
                                        Set<UUID> exemptions,
                                        float damage) {
        AABB beam = new AABB(
                column.getX(),
                column.getY(),
                column.getZ(),
                column.getX() + 1.0D,
                column.getY() + 1.0D,
                column.getZ() + 1.0D);
        for (LivingEntity entity : level.getEntities(
                EntityTypeTest.forClass(LivingEntity.class),
                beam,
                LivingEntity::isAlive)) {
            if (entity.blockPosition().getY() != column.getY()) {
                continue;
            }
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
