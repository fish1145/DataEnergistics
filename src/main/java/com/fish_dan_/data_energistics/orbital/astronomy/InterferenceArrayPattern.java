package com.fish_dan_.data_energistics.orbital.astronomy;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Validates the fixed 5x5x3 high-tier core base and discovers mirrors through bounded waveguide paths.
 */
public final class InterferenceArrayPattern {

    private static final int CORE_RADIUS = 2;
    private static final int CORE_HEIGHT = 3;
    private static final int PORT_HEIGHT = 1;
    private static final List<BlockPos> PORT_OFFSETS = List.of(
            new BlockPos(CORE_RADIUS, PORT_HEIGHT, 0),
            new BlockPos(-CORE_RADIUS, PORT_HEIGHT, 0),
            new BlockPos(0, PORT_HEIGHT, CORE_RADIUS),
            new BlockPos(0, PORT_HEIGHT, -CORE_RADIUS));
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX);

    private InterferenceArrayPattern() {}

    /**
     * Returns whether the controller occupies the bottom center of a solid 5x5x3 Data Framework base with four
     * waveguide ports at the middle of the second-layer sides.
     */
    public static boolean hasValidCoreBase(ServerLevel level, BlockPos corePos) {
        for (int y = 0; y < CORE_HEIGHT; y++) {
            for (int x = -CORE_RADIUS; x <= CORE_RADIUS; x++) {
                for (int z = -CORE_RADIUS; z <= CORE_RADIUS; z++) {
                    BlockPos current = corePos.offset(x, y, z);
                    if (x == 0 && y == 0 && z == 0) {
                        if (!level.getBlockState(current).is(DEBlocks.INTERFERENCE_ARRAY_CORE.get())) {
                            return false;
                        }
                    } else if (isPortOffset(x, y, z)) {
                        if (!level.getBlockState(current).is(DEBlocks.CELESTIAL_WAVEGUIDE.get())) {
                            return false;
                        }
                    } else if (!level.getBlockState(current).is(DEBlocks.DATA_FRAMEWORK.get())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns structurally valid mirror centers reachable from one of the four core ports within the configured path
     * length. Results are nearest-first and then position-stable so claim order is deterministic.
     */
    public static List<BlockPos> findConnectedMirrors(
                                                      ServerLevel level,
                                                      BlockPos corePos,
                                                      DataEnergisticsConfiguration.AstronomySchema settings) {
        if (!hasValidCoreBase(level, corePos)) {
            return List.of();
        }

        Queue<WaveguideStep> pending = new ArrayDeque<>();
        Set<BlockPos> visitedWaveguides = new HashSet<>();
        for (BlockPos offset : PORT_OFFSETS) {
            BlockPos port = corePos.offset(offset).immutable();
            pending.add(new WaveguideStep(port, 0));
            visitedWaveguides.add(port);
        }

        Map<BlockPos, Integer> mirrorDistances = new HashMap<>();
        int maximumLength = settings.highTierWaveguidePathLength;
        while (!pending.isEmpty()) {
            WaveguideStep step = pending.remove();
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = step.position().relative(direction);
                if (level.getBlockState(adjacent).is(DEBlocks.ASTRONOMICAL_MIRROR.get()) &&
                        isValidMirror(level, corePos, adjacent, settings)) {
                    mirrorDistances.merge(adjacent.immutable(), step.distance(), Math::min);
                }
                if (step.distance() >= maximumLength ||
                        !level.getBlockState(adjacent).is(DEBlocks.CELESTIAL_WAVEGUIDE.get())) {
                    continue;
                }
                BlockPos immutableAdjacent = adjacent.immutable();
                if (visitedWaveguides.add(immutableAdjacent)) {
                    pending.add(new WaveguideStep(immutableAdjacent, step.distance() + 1));
                }
            }
        }

        List<BlockPos> mirrors = new ArrayList<>(mirrorDistances.keySet());
        mirrors.sort(Comparator.comparingInt((BlockPos pos) -> mirrorDistances.get(pos)).thenComparing(POSITION_ORDER));
        return List.copyOf(mirrors);
    }

    private static boolean isPortOffset(int x, int y, int z) {
        return y == PORT_HEIGHT &&
                ((Math.abs(x) == CORE_RADIUS && z == 0) || (x == 0 && Math.abs(z) == CORE_RADIUS));
    }

    private static boolean isValidMirror(
                                         ServerLevel level,
                                         BlockPos corePos,
                                         BlockPos mirrorCenter,
                                         DataEnergisticsConfiguration.AstronomySchema settings) {
        int deltaX = mirrorCenter.getX() - corePos.getX();
        int deltaZ = mirrorCenter.getZ() - corePos.getZ();
        long horizontalDistanceSquared = (long) deltaX * deltaX + (long) deltaZ * deltaZ;
        long maximumHorizontalDistance = settings.highTierMirrorHorizontalRange;
        if (horizontalDistanceSquared > maximumHorizontalDistance * maximumHorizontalDistance ||
                Math.abs(mirrorCenter.getY() - corePos.getY()) > settings.highTierMirrorVerticalRange) {
            return false;
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos aperture = mirrorCenter.offset(x, 0, z);
                boolean center = x == 0 && z == 0;
                if (center) {
                    if (!level.getBlockState(aperture).is(DEBlocks.ASTRONOMICAL_MIRROR.get())) {
                        return false;
                    }
                } else if (!level.getBlockState(aperture).is(DEBlocks.ASTRONOMICAL_MIRROR_PANEL.get())) {
                    return false;
                }
                if (!level.canSeeSky(aperture.above())) {
                    return false;
                }
            }
        }
        return true;
    }

    private record WaveguideStep(BlockPos position, int distance) {}
}
