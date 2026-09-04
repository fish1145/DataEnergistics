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

/** Fast orthogonal routing: cheap shapes first, sparse A* only when every cheap shape is blocked. */
final class OrthogonalRouteSearch {

    private static final double EPSILON = OrthogonalSegmentReservations.EPSILON;
    private static final int MAXIMUM_MEASURED_QUICK_PATHS = 24;
    private final OrthogonalRoutingGraph graph;
    private final OrthogonalSegmentReservations reservations;
    private final double maximumDetour;

    OrthogonalRouteSearch(OrthogonalRoutingGraph graph, OrthogonalSegmentReservations reservations, double nodeGap) {
        this.graph = graph;
        this.reservations = reservations;
        maximumDetour = 2 * nodeGap;
    }

    Choice route(List<Port> sourcePorts, List<Port> targetPorts, CraftingPlanRouteGroup group,
                 @Nullable Choice previous, boolean improveCrossings) {
        List<Port> sources = terminals(sourcePorts, group, true);
        List<Port> targets = terminals(targetPorts, group, false);
        if (sources.isEmpty() || targets.isEmpty()) throw new IllegalStateException("No safe crafting-tree node port");
        Candidate shortest = previous == null ? null : new Candidate(previous.points(), previous.metrics());
        List<RawCandidate> quick = new ObjectArrayList<>();
        for (Port source : sources) {
            for (Port target : targets) {
                for (List<Point> core : graph.quickPaths(source, target)) {
                    RawCandidate candidate = rawCandidate(source, target, core, group, true);
                    if (candidate == null) continue;
                    quick.add(candidate);
                }
            }
        }
        quick.sort(OrthogonalRouteSearch::compareRaw);
        for (RawCandidate candidate : quick) {
            if (shortest != null && compareRaw(candidate, shortest) >= 0) break;
            Candidate measured = measure(candidate, group);
            if (measured != null) {
                shortest = measured;
                break;
            }
        }
        if (shortest == null) shortest = search(sources, targets, group);
        if (shortest == null) throw new IllegalStateException("No obstacle-free crafting-tree connection for " + group);
        double baseline = shortest.metrics().length();
        if (!improveCrossings || shortest.metrics().crossings() == 0) {
            return new Choice(shortest.points(), shortest.metrics(), baseline);
        }
        double limit = baseline + Math.min(baseline * 0.25, maximumDetour);
        Candidate best = shortest;
        int measuredPaths = 0;
        for (RawCandidate candidate : quick) {
            if (candidate.length() > limit + EPSILON || measuredPaths++ == MAXIMUM_MEASURED_QUICK_PATHS) break;
            Candidate measured = measure(candidate, group);
            if (measured != null && compare(measured.metrics(), best.metrics()) < 0) best = measured;
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

    private @Nullable Candidate search(List<Port> sources, List<Port> targets, CraftingPlanRouteGroup group) {
        List<Long2ObjectMap<Label>> labels = new ObjectArrayList<>(4);
        for (int heading = 0; heading < 4; heading++) labels.add(new Long2ObjectOpenHashMap<>());
        Comparator<Label> order = Comparator.comparingDouble((Label label) -> label.estimate)
                .thenComparingInt(label -> label.bends).thenComparingLong(label -> label.point)
                .thenComparingInt(label -> label.heading).thenComparingLong(label -> label.ordinal);
        var pending = new ObjectHeapPriorityQueue<>(order);
        long ordinal = 0;
        for (Port source : sources) {
            double length = distance(source.anchor(), source.stub());
            Label label = new Label(graph.key(source.stub()), heading(source.anchor(), source.stub()), source,
                    null, length, length + heuristic(source.stub(), targets), 0, ordinal++);
            if (retain(label, labels)) pending.enqueue(label);
        }
        while (!pending.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Label label = pending.dequeue();
            if (!label.active) continue;
            Point current = graph.point(label.point);
            for (Port target : targets) {
                if (label.point != graph.key(target.stub())) continue;
                RawCandidate candidate = rawCandidate(label.source, target, reconstruct(label), group, false);
                if (candidate != null) {
                    Candidate measured = measure(candidate, group);
                    if (measured != null) return measured;
                }
            }
            for (long next : graph.neighbors(label.point, targets, reservations, group)) {
                Point to = graph.point(next);
                int direction = heading(current, to);
                if (direction == (label.heading + 2) % 4) continue;
                double length = label.length + distance(current, to);
                Label candidate = new Label(next, direction, label.source, label, length,
                        length + heuristic(to, targets), label.bends + (direction == label.heading ? 0 : 1), ordinal++);
                if (retain(candidate, labels)) pending.enqueue(candidate);
            }
        }
        return null;
    }

    private static boolean retain(Label candidate, List<Long2ObjectMap<Label>> index) {
        Long2ObjectMap<Label> labels = index.get(candidate.heading);
        Label existing = labels.get(candidate.point);
        if (existing != null && (existing.length < candidate.length - EPSILON || Math.abs(existing.length - candidate.length) <= EPSILON && existing.bends <= candidate.bends)) return false;
        if (existing != null) existing.active = false;
        labels.put(candidate.point, candidate);
        return true;
    }

    private List<Point> reconstruct(Label label) {
        List<Point> reversed = new ObjectArrayList<>();
        for (Label step = label; step != null; step = step.previous) reversed.add(graph.point(step.point));
        List<Point> result = new ObjectArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) result.add(reversed.get(index));
        return result;
    }

    private @Nullable RawCandidate rawCandidate(Port source, Port target, List<Point> core,
                                                CraftingPlanRouteGroup group, boolean validateCore) {
        if (validateCore) {
            for (int index = 1; index < core.size(); index++) {
                Point from = core.get(index - 1);
                Point to = core.get(index);
                if (!from.equals(to) && !graph.clear(from, to)) return null;
            }
        }
        List<Point> points = new ObjectArrayList<>(core.size() + 2);
        points.add(source.anchor());
        for (Point point : core) if (!append(points, point)) return null;
        if (!append(points, target.anchor())) return null;
        eraseCollinearLoops(points);
        double length = 0;
        int bends = 0;
        int previousHeading = -1;
        for (int index = 1; index < points.size(); index++) {
            Point from = points.get(index - 1);
            Point to = points.get(index);
            if (validateCore && !reservations.available(from, to, group)) return null;
            int direction = heading(from, to);
            if (previousHeading >= 0 && direction != previousHeading) bends++;
            previousHeading = direction;
            length += distance(from, to);
        }
        return new RawCandidate(points, length, bends);
    }

    /** Removes a detour between two visits to the same finite corridor using only already verified subsegments. */
    private static void eraseCollinearLoops(List<Point> points) {
        boolean changed;
        do {
            changed = false;
            outer:
            for (int first = 0; first + 1 < points.size(); first++) {
                Point firstStart = points.get(first);
                Point firstEnd = points.get(first + 1);
                for (int second = first + 2; second + 1 < points.size(); second++) {
                    Point secondStart = points.get(second);
                    Point secondEnd = points.get(second + 1);
                    Point splice = splicePoint(firstStart, firstEnd, secondStart, secondEnd);
                    if (splice == null) continue;
                    List<Point> simplified = new ObjectArrayList<>(points.size() - (second - first));
                    for (int index = 0; index <= first; index++) simplified.add(points.get(index));
                    if (!simplified.getLast().equals(splice)) simplified.add(splice);
                    for (int index = second + 1; index < points.size(); index++) {
                        Point point = points.get(index);
                        if (!simplified.getLast().equals(point)) simplified.add(point);
                    }
                    points.clear();
                    points.addAll(simplified);
                    changed = true;
                    break outer;
                }
            }
        } while (changed);
    }

    private static @Nullable Point splicePoint(Point firstStart, Point firstEnd,
                                               Point secondStart, Point secondEnd) {
        boolean horizontal = firstStart.y() == firstEnd.y();
        if (horizontal != (secondStart.y() == secondEnd.y())) return null;
        if (horizontal && firstStart.y() != secondStart.y() || !horizontal && firstStart.x() != secondStart.x()) return null;
        double firstLow = horizontal ? Math.min(firstStart.x(), firstEnd.x()) : Math.min(firstStart.y(), firstEnd.y());
        double firstHigh = horizontal ? Math.max(firstStart.x(), firstEnd.x()) : Math.max(firstStart.y(), firstEnd.y());
        double secondLow = horizontal ? Math.min(secondStart.x(), secondEnd.x()) : Math.min(secondStart.y(), secondEnd.y());
        double secondHigh = horizontal ? Math.max(secondStart.x(), secondEnd.x()) : Math.max(secondStart.y(), secondEnd.y());
        double low = Math.max(firstLow, secondLow);
        double high = Math.min(firstHigh, secondHigh);
        if (low >= high) return null;
        double firstDirection = horizontal ? firstEnd.x() - firstStart.x() : firstEnd.y() - firstStart.y();
        double secondDirection = horizontal ? secondEnd.x() - secondStart.x() : secondEnd.y() - secondStart.y();
        boolean sameDirection = Math.signum(firstDirection) == Math.signum(secondDirection);
        double coordinate = secondDirection > 0 == sameDirection ? low : high;
        return horizontal ? new Point(coordinate, firstStart.y()) : new Point(firstStart.x(), coordinate);
    }

    private @Nullable Candidate measure(RawCandidate candidate, CraftingPlanRouteGroup group) {
        Metrics metrics = reservations.measure(candidate.points(), group);
        return metrics == null ? null : new Candidate(candidate.points(), metrics);
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

    private static int compareRaw(RawCandidate left, RawCandidate right) {
        int length = Double.compare(left.length(), right.length());
        return length == 0 ? Integer.compare(left.bends(), right.bends()) : length;
    }

    private static int compareRaw(RawCandidate left, Candidate right) {
        int length = Double.compare(left.length(), right.metrics().length());
        return length == 0 ? Integer.compare(left.bends(), right.metrics().bends()) : length;
    }

    record Choice(List<Point> points, Metrics metrics, double baselineLength) {}

    private record Candidate(List<Point> points, Metrics metrics) {}

    private record RawCandidate(List<Point> points, double length, int bends) {}

    private static final class Label {

        private final long point;
        private final int heading;
        private final Port source;
        private final @Nullable Label previous;
        private final double length;
        private final double estimate;
        private final int bends;
        private final long ordinal;
        private boolean active = true;

        private Label(long point, int heading, Port source, @Nullable Label previous, double length,
                      double estimate, int bends, long ordinal) {
            this.point = point;
            this.heading = heading;
            this.source = source;
            this.previous = previous;
            this.length = length;
            this.estimate = estimate;
            this.bends = bends;
            this.ordinal = ordinal;
        }
    }
}
