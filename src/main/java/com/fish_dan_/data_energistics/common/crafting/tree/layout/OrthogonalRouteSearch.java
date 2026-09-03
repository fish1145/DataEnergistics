package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.OrthogonalRoutingGraph.Port;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.OrthogonalSegmentReservations.Metrics;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Shortest feasible baseline followed by bounded local crossing avoidance on sparse visibility guides. */
final class OrthogonalRouteSearch {

    private static final double EPSILON = OrthogonalSegmentReservations.EPSILON;
    private final OrthogonalRoutingGraph graph;
    private final OrthogonalSegmentReservations reservations;
    private final double maximumDetour;

    OrthogonalRouteSearch(OrthogonalRoutingGraph graph, OrthogonalSegmentReservations reservations, double nodeGap) {
        this.graph = graph;
        this.reservations = reservations;
        maximumDetour = 2 * nodeGap;
    }

    Choice route(List<Port> sourcePorts, List<Port> targetPorts, CraftingPlanRouteGroup group,
                 @Nullable Choice previous) {
        List<Port> sources = terminals(sourcePorts, group, true);
        List<Port> targets = terminals(targetPorts, group, false);
        if (sources.isEmpty() || targets.isEmpty()) throw new IllegalStateException("No safe crafting-tree node port");
        // A reroute retains its already feasible path as an incumbent, not as an error fallback.
        Candidate shortest = previous == null ? null : new Candidate(previous.points(), previous.metrics());
        List<Candidate> quick = new ObjectArrayList<>();
        double lowerBound = Double.POSITIVE_INFINITY;
        for (Port source : sources) {
            for (Port target : targets) {
                lowerBound = Math.min(lowerBound, distance(source.stub(), target.stub()) + 2 * OrthogonalRoutingGraph.CLEARANCE);
                for (List<Point> core : graph.quickPaths(source, target)) {
                    Candidate candidate = candidate(source, target, core, group);
                    if (candidate != null) {
                        quick.add(candidate);
                        if (shortest == null || compareShortest(candidate, shortest) < 0) shortest = candidate;
                    }
                }
            }
        }
        if (shortest == null || shortest.metrics().length() > lowerBound + EPSILON) {
            shortest = search(sources, targets, group, shortest, Double.POSITIVE_INFINITY, false);
        }
        if (shortest == null) throw new IllegalStateException("No obstacle-free crafting-tree connection for " + group);
        double baseline = shortest.metrics().length();
        double limit = baseline + Math.min(baseline * 0.25, maximumDetour);
        Candidate best = shortest;
        if (shortest.metrics().crossings() > 0) {
            // Re-evaluate the cheap alternatives under the same complete-route objective as A* results.
            for (Candidate candidate : quick) {
                if (candidate.metrics().length() <= limit + EPSILON && compare(candidate.metrics(), best.metrics()) < 0) best = candidate;
            }
            Candidate alternative = search(sources, targets, group, best, limit, true);
            if (alternative != null) best = alternative;
        }
        return new Choice(best.points(), best.metrics(), baseline);
    }

    private List<Port> terminals(List<Port> ports, CraftingPlanRouteGroup group, boolean source) {
        List<Port> result = new ObjectArrayList<>(4);
        for (Port port : ports) {
            Point from = source ? port.anchor() : port.stub();
            Point to = source ? port.stub() : port.anchor();
            if (graph.terminalClear(port) && reservations.available(from, to, group)) result.add(port);
        }
        return result;
    }

    private @Nullable Candidate search(List<Port> sources, List<Port> targets, CraftingPlanRouteGroup group,
                                       @Nullable Candidate initial, double limit, boolean avoidCrossings) {
        Candidate best = initial;
        List<Long2ObjectMap<List<Label>>> labels = new ObjectArrayList<>(4);
        for (int heading = 0; heading < 4; heading++) labels.add(new Long2ObjectOpenHashMap<>());
        Comparator<Label> order = Comparator.comparingInt((Label label) -> avoidCrossings ? label.crossings : 0)
                .thenComparingDouble(label -> label.estimate).thenComparingInt(label -> label.bends)
                .thenComparingLong(label -> label.point).thenComparingInt(label -> label.heading)
                .thenComparingLong(label -> label.ordinal);
        var pending = new ObjectHeapPriorityQueue<>(order);
        long ordinal = 0;
        for (Port source : sources) {
            double length = distance(source.anchor(), source.stub());
            Label label = new Label(graph.key(source.stub()), heading(source.anchor(), source.stub()), source,
                    null, length, length + heuristic(source.stub(), targets), 0, 0, ordinal++);
            retain(label, labels, avoidCrossings);
            pending.enqueue(label);
        }
        int completed = 0;
        while (!pending.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Label label = pending.dequeue();
            if (!label.active) continue;
            double bound = avoidCrossings ? limit : best == null ? limit : Math.min(limit, best.metrics().length());
            if (label.estimate > bound + EPSILON) {
                if (!avoidCrossings) break;
                continue;
            }
            Point current = graph.point(label.point);
            for (Port target : targets) {
                if (label.point != graph.key(target.stub())) continue;
                List<Point> core = reconstruct(label);
                Candidate candidate = candidate(label.source, target, core, group);
                if (candidate != null && candidate.metrics().length() <= bound + EPSILON && (best == null || (avoidCrossings ? compare(candidate.metrics(), best.metrics()) : compareShortest(candidate, best)) < 0)) best = candidate;
                // This is a local heuristic, not a global crossing optimiser. Always retain the feasible baseline.
                if (avoidCrossings && ++completed >= 12) return best;
            }
            for (var neighbor : graph.neighbors(label.point, targets, reservations, group).long2IntEntrySet()) {
                long next = neighbor.getLongKey();
                Point to = graph.point(next);
                int direction = heading(current, to);
                if (direction == (label.heading + 2) % 4) continue;
                double length = label.length + distance(current, to);
                double estimate = length + heuristic(to, targets);
                if (estimate > bound + EPSILON) continue;
                int crossings = label.crossings + neighbor.getIntValue();
                Label candidate = new Label(next, direction, label.source, label, length, estimate, crossings,
                        label.bends + (direction == label.heading ? 0 : 1), ordinal++);
                if (retain(candidate, labels, avoidCrossings)) pending.enqueue(candidate);
            }
        }
        return best;
    }

    private static boolean retain(Label candidate, List<Long2ObjectMap<List<Label>>> index, boolean avoidCrossings) {
        List<Label> labels = index.get(candidate.heading).computeIfAbsent(candidate.point, unused -> new ObjectArrayList<>(4));
        for (Label existing : labels) {
            if (existing.length <= candidate.length + EPSILON && (!avoidCrossings || existing.crossings <= candidate.crossings) && (existing.length < candidate.length - EPSILON || existing.crossings < candidate.crossings || existing.bends <= candidate.bends)) return false;
        }
        for (int i = labels.size() - 1; i >= 0; i--) {
            Label existing = labels.get(i);
            if (candidate.length <= existing.length + EPSILON && (!avoidCrossings || candidate.crossings <= existing.crossings) && (candidate.length < existing.length - EPSILON || candidate.crossings < existing.crossings || candidate.bends <= existing.bends)) {
                existing.active = false;
                labels.remove(i);
            }
        }
        if (labels.size() == 4) {
            int worst = 0;
            for (int i = 1; i < labels.size(); i++) if (labels.get(i).length > labels.get(worst).length) worst = i;
            if (candidate.length >= labels.get(worst).length) return false;
            labels.remove(worst).active = false;
        }
        labels.add(candidate);
        return true;
    }

    private List<Point> reconstruct(Label label) {
        List<Point> reversed = new ObjectArrayList<>();
        for (Label step = label; step != null; step = step.previous) reversed.add(graph.point(step.point));
        List<Point> result = new ObjectArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) result.add(reversed.get(i));
        return result;
    }

    private @Nullable Candidate candidate(Port source, Port target, List<Point> core, CraftingPlanRouteGroup group) {
        for (int index = 1; index < core.size(); index++) {
            Point from = core.get(index - 1);
            Point to = core.get(index);
            if (!from.equals(to) && !graph.clear(from, to)) return null;
        }
        List<Point> points = new ObjectArrayList<>(core.size() + 2);
        points.add(source.anchor());
        for (Point point : core) if (!append(points, point)) return null;
        if (!append(points, target.anchor())) return null;
        Metrics metrics = reservations.measure(points, group);
        return metrics == null ? null : new Candidate(points, metrics);
    }

    private static boolean append(List<Point> points, Point point) {
        Point last = points.getLast();
        if (last.equals(point)) return true;
        if (points.size() > 1) {
            Point before = points.get(points.size() - 2);
            int previous = heading(before, last);
            int next = heading(last, point);
            if (next == (previous + 2) % 4) return false;
            if (next == previous) points.removeLast();
        }
        points.add(point);
        return true;
    }

    private static double heuristic(Point point, List<Port> goals) {
        double minimum = Double.POSITIVE_INFINITY;
        for (Port goal : goals) minimum = Math.min(minimum, distance(point, goal.stub()) + OrthogonalRoutingGraph.CLEARANCE);
        return minimum;
    }

    static double distance(Point from, Point to) {
        return Math.abs(to.x() - from.x()) + Math.abs(to.y() - from.y());
    }

    private static int heading(Point from, Point to) {
        return from.y() == to.y() ? to.x() > from.x() ? 0 : 2 : to.y() > from.y() ? 1 : 3;
    }

    static int compare(Metrics left, Metrics right) {
        int value = Integer.compare(left.crossings(), right.crossings());
        if (value == 0) value = Double.compare(left.length(), right.length());
        if (value == 0) value = Integer.compare(left.bends(), right.bends());
        if (value == 0) value = Double.compare(right.sharedLength(), left.sharedLength());
        return value;
    }

    private static int compareShortest(Candidate left, Candidate right) {
        int length = Double.compare(left.metrics().length(), right.metrics().length());
        return length == 0 ? compare(left.metrics(), right.metrics()) : length;
    }

    record Choice(List<Point> points, Metrics metrics, double baselineLength) {}

    private record Candidate(List<Point> points, Metrics metrics) {}

    private static final class Label {

        private final long point;
        private final int heading;
        private final Port source;
        private final @Nullable Label previous;
        private final double length;
        private final double estimate;
        private final int crossings;
        private final int bends;
        private final long ordinal;
        private boolean active = true;

        private Label(long point, int heading, Port source, @Nullable Label previous, double length,
                      double estimate, int crossings, int bends, long ordinal) {
            this.point = point;
            this.heading = heading;
            this.source = source;
            this.previous = previous;
            this.length = length;
            this.estimate = estimate;
            this.crossings = crossings;
            this.bends = bends;
            this.ordinal = ordinal;
        }
    }
}
