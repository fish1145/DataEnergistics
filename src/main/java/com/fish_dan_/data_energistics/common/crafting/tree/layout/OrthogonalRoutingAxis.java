package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import it.unimi.dsi.fastutil.doubles.Double2IntOpenHashMap;
import it.unimi.dsi.fastutil.doubles.DoubleSortedSet;

import java.util.Arrays;

/** Coordinate compression for sparse guide points; it does not allocate an X/Y grid. */
final class OrthogonalRoutingAxis {

    private final double[] values;
    private final Double2IntOpenHashMap indices;

    OrthogonalRoutingAxis(DoubleSortedSet coordinates) {
        values = coordinates.toDoubleArray();
        indices = new Double2IntOpenHashMap(values.length);
        indices.defaultReturnValue(-1);
        for (int index = 0; index < values.length; index++) indices.put(values[index], index);
    }

    int size() {
        return values.length;
    }

    double value(int index) {
        return values[index];
    }

    int index(double value) {
        int index = indices.get(normalize(value));
        if (index < 0) throw new IllegalArgumentException("Unindexed routing coordinate: " + value);
        return index;
    }

    int floor(double value) {
        int found = Arrays.binarySearch(values, value);
        return found >= 0 ? found : Math.max(0, -found - 2);
    }

    int ceiling(double value) {
        int found = Arrays.binarySearch(values, value);
        return found >= 0 ? found : Math.min(values.length - 1, -found - 1);
    }

    static double normalize(double coordinate) {
        double normalized = Math.rint(coordinate / OrthogonalSegmentReservations.EPSILON) * OrthogonalSegmentReservations.EPSILON;
        return normalized == 0 ? 0 : normalized;
    }

    static long point(int x, int y) {
        return (long) x << 32 | y & 0xFFFFFFFFL;
    }

    static int x(long point) {
        return (int) (point >>> 32);
    }

    static int y(long point) {
        return (int) point;
    }
}
