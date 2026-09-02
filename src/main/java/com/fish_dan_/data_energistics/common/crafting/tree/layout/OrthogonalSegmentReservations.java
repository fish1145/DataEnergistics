package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;

import it.unimi.dsi.fastutil.doubles.Double2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Component-local occupancy: perpendicular crossings are allowed, shared positive-length segments are not. */
final class OrthogonalSegmentReservations {

    static final double EPSILON = 0.000001;

    private final Double2ObjectAVLTreeMap<List<Span>> horizontal = new Double2ObjectAVLTreeMap<>();
    private final Double2ObjectAVLTreeMap<List<Span>> vertical = new Double2ObjectAVLTreeMap<>();
    private int segmentCount;

    int segmentCount() {
        return segmentCount;
    }

    boolean available(List<Point> points) {
        for (int index = 1; index < points.size(); index++) {
            Point from = points.get(index - 1);
            Point to = points.get(index);
            boolean alongX = Math.abs(from.y() - to.y()) < EPSILON;
            double start = alongX ? Math.min(from.x(), to.x()) : Math.min(from.y(), to.y());
            double end = alongX ? Math.max(from.x(), to.x()) : Math.max(from.y(), to.y());
            if (end - start < EPSILON) {
                continue;
            }
            double coordinate = alongX ? from.y() : from.x();
            var occupied = alongX ? horizontal : vertical;
            for (List<Span> spans : occupied.subMap(coordinate - EPSILON, coordinate + EPSILON).values()) {
                for (Span span : spans) {
                    if (Math.min(end, span.end()) - Math.max(start, span.start()) > EPSILON) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    void reserve(List<Point> points) {
        for (int index = 1; index < points.size(); index++) {
            Point from = points.get(index - 1);
            Point to = points.get(index);
            boolean alongX = Math.abs(from.y() - to.y()) < EPSILON;
            double start = alongX ? Math.min(from.x(), to.x()) : Math.min(from.y(), to.y());
            double end = alongX ? Math.max(from.x(), to.x()) : Math.max(from.y(), to.y());
            if (end - start >= EPSILON) {
                double coordinate = alongX ? from.y() : from.x();
                var occupied = alongX ? horizontal : vertical;
                occupied.computeIfAbsent(coordinate, unused -> new ObjectArrayList<>()).add(new Span(start, end));
                segmentCount++;
            }
        }
    }

    private record Span(double start, double end) {}
}
