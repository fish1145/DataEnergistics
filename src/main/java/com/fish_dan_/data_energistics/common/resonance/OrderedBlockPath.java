package com.fish_dan_.data_energistics.common.resonance;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Predicate;

/**
 * Visits the block cells crossed by a finite line segment in deterministic propagation order.
 */
@ApiStatus.Internal
public final class OrderedBlockPath {

    private static final double BOUNDARY_EPSILON = 1.0E-12D;

    private OrderedBlockPath() {}

    /**
     * Walks a segment with a three-dimensional DDA. Returning {@code false} from the visitor stops the walk.
     *
     * @param start        exact segment origin
     * @param end          exact segment endpoint
     * @param includeStart whether to visit the cell containing {@code start}
     * @param includeEnd   whether to visit the cell containing {@code end}
     * @param visitor      ordered cell visitor; {@code true} continues and {@code false} stops
     * @return {@code true} when the complete path was visited, or {@code false} when the visitor stopped it
     */
    public static boolean visit(Vec3 start, Vec3 end, boolean includeStart, boolean includeEnd,
                                Predicate<BlockPos> visitor) {
        validateFinite(start, "start");
        validateFinite(end, "end");

        int x = Mth.floor(start.x);
        int y = Mth.floor(start.y);
        int z = Mth.floor(start.z);
        int endX = Mth.floor(end.x);
        int endY = Mth.floor(end.y);
        int endZ = Mth.floor(end.z);

        if (x == endX && y == endY && z == endZ) {
            return !(includeStart || includeEnd) || visitor.test(new BlockPos(x, y, z));
        }
        if (includeStart && !visitor.test(new BlockPos(x, y, z))) {
            return false;
        }

        double deltaX = end.x - start.x;
        double deltaY = end.y - start.y;
        double deltaZ = end.z - start.z;
        int stepX = Integer.signum(endX - x);
        int stepY = Integer.signum(endY - y);
        int stepZ = Integer.signum(endZ - z);
        double stepDistanceX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(deltaX);
        double stepDistanceY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(deltaY);
        double stepDistanceZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(deltaZ);
        double nextBoundaryX = boundaryDistance(start.x, x, stepX, deltaX);
        double nextBoundaryY = boundaryDistance(start.y, y, stepY, deltaY);
        double nextBoundaryZ = boundaryDistance(start.z, z, stepZ, deltaZ);

        while (x != endX || y != endY || z != endZ) {
            double nextBoundary = Math.min(nextBoundaryX, Math.min(nextBoundaryY, nextBoundaryZ));
            if (sameBoundary(nextBoundaryX, nextBoundary)) {
                x += stepX;
                nextBoundaryX += stepDistanceX;
            }
            if (sameBoundary(nextBoundaryY, nextBoundary)) {
                y += stepY;
                nextBoundaryY += stepDistanceY;
            }
            if (sameBoundary(nextBoundaryZ, nextBoundary)) {
                z += stepZ;
                nextBoundaryZ += stepDistanceZ;
            }

            boolean atEnd = x == endX && y == endY && z == endZ;
            if ((!atEnd || includeEnd) && !visitor.test(new BlockPos(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameBoundary(double first, double second) {
        return Math.abs(first - second) <= BOUNDARY_EPSILON;
    }

    private static double boundaryDistance(double coordinate, int blockCoordinate, int step, double delta) {
        if (step > 0) {
            return (blockCoordinate + 1.0D - coordinate) / delta;
        }
        if (step < 0) {
            return (coordinate - blockCoordinate) / -delta;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static void validateFinite(Vec3 position, String name) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            throw new IllegalArgumentException("Ordered block path " + name + " must be finite: " + position);
        }
    }
}
