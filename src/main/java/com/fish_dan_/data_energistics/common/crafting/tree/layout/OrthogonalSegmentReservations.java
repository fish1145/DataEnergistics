package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;

import it.unimi.dsi.fastutil.doubles.Double2ObjectAVLTreeMap;

import java.util.List;

/** Directed interval occupancy: compatible runs share space; other groups and reverse flows retain separate lanes. */
final class OrthogonalSegmentReservations {

    static final double EPSILON = 0.000001;

    private final Double2ObjectAVLTreeMap<Double2ObjectAVLTreeMap<Span>> horizontal = new Double2ObjectAVLTreeMap<>();
    private final Double2ObjectAVLTreeMap<Double2ObjectAVLTreeMap<Span>> vertical = new Double2ObjectAVLTreeMap<>();
    private final double clearance;
    private int segmentCount;

    OrthogonalSegmentReservations() {
        this(0);
    }

    OrthogonalSegmentReservations(double clearance) {
        this.clearance = clearance;
    }

    int segmentCount() {
        return segmentCount;
    }

    boolean available(List<Point> points, CraftingPlanRouteGroup group, boolean reverse) {
        return evaluate(points, group, reverse, false) >= 0;
    }

    double addedLength(List<Point> points, CraftingPlanRouteGroup group, boolean reverse) {
        double result = evaluate(points, group, reverse, true);
        return result < 0 ? Double.POSITIVE_INFINITY : result;
    }

    private double evaluate(List<Point> points, CraftingPlanRouteGroup group, boolean reverse, boolean measure) {
        double result = 0;
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
            int direction = Double.compare(alongX ? to.x() : to.y(), alongX ? from.x() : from.y()) * (reverse ? -1 : 1);
            var occupied = alongX ? horizontal : vertical;
            double covered = 0;
            for (var spans : occupied.subMap(coordinate - EPSILON, coordinate + EPSILON).values()) {
                double lower = start - clearance + EPSILON;
                var before = spans.headMap(lower);
                if (!before.isEmpty()) {
                    double key = before.lastDoubleKey();
                    double overlap = overlap(start, end, key, before.get(key), group, direction);
                    if (overlap < 0) return -1;
                    if (measure) covered += overlap;
                }
                for (var entry : spans.subMap(lower, end + clearance).double2ObjectEntrySet()) {
                    double overlap = overlap(start, end, entry.getDoubleKey(), entry.getValue(), group, direction);
                    if (overlap < 0) return -1;
                    if (measure) covered += overlap;
                }
            }
            if (measure) result += Math.max(0, end - start - covered);
        }
        return result;
    }

    private double overlap(double start, double end, double occupiedStart, Span span,
                           CraftingPlanRouteGroup group, int direction) {
        double overlap = Math.min(end, span.end()) - Math.max(start, occupiedStart);
        if (overlap > -clearance + EPSILON && !span.compatible(group, direction)) return -1;
        return Math.max(0, overlap);
    }

    void reserve(List<Point> points, CraftingPlanRouteGroup group, boolean reverse) {
        for (int index = 1; index < points.size(); index++) {
            Point from = points.get(index - 1);
            Point to = points.get(index);
            boolean alongX = Math.abs(from.y() - to.y()) < EPSILON;
            double start = alongX ? Math.min(from.x(), to.x()) : Math.min(from.y(), to.y());
            double end = alongX ? Math.max(from.x(), to.x()) : Math.max(from.y(), to.y());
            if (end - start >= EPSILON) {
                double coordinate = alongX ? from.y() : from.x();
                int direction = Double.compare(alongX ? to.x() : to.y(), alongX ? from.x() : from.y()) * (reverse ? -1 : 1);
                var occupied = alongX ? horizontal : vertical;
                var spans = occupied.computeIfAbsent(coordinate, unused -> new Double2ObjectAVLTreeMap<>());
                var before = spans.headMap(start + EPSILON);
                if (!before.isEmpty()) {
                    double key = before.lastDoubleKey();
                    Span span = before.get(key);
                    if (span.end() >= start - EPSILON && span.compatible(group, direction)) {
                        start = key;
                        end = Math.max(end, span.end());
                        spans.remove(key);
                        segmentCount--;
                    }
                }
                var next = spans.tailMap(start).double2ObjectEntrySet().iterator();
                while (next.hasNext()) {
                    var entry = next.next();
                    if (entry.getDoubleKey() > end + EPSILON) break;
                    Span span = entry.getValue();
                    if (span.compatible(group, direction)) {
                        end = Math.max(end, span.end());
                        next.remove();
                        segmentCount--;
                    }
                }
                spans.put(start, new Span(end, group, direction));
                segmentCount++;
            }
        }
    }

    private record Span(double end, CraftingPlanRouteGroup group, int direction) {

        private boolean compatible(CraftingPlanRouteGroup candidate, int candidateDirection) {
            return direction == candidateDirection && group.equals(candidate);
        }
    }
}
