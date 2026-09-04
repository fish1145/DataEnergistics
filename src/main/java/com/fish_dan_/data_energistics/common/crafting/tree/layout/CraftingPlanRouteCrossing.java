package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.RoutedEdge;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Run;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Segment;

import it.unimi.dsi.fastutil.doubles.Double2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleComparators;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Layout-worker bridge candidates: a vertical route detours over a deliberately omitted horizontal underpass. */
public record CraftingPlanRouteCrossing(int bridgeSegmentId, double x, double y,
                                        double bend, double radius, List<Underpass> underpasses) {

    public static final double MAX_RADIUS = 4;
    private static final double[] SPANS = { MAX_RADIUS, 3, 2 };
    private static final double GAP_HALF_WIDTH = 1.5;
    private static final double DENSE_CLUSTER_GAP = 24;
    private static final double DENSE_BRIDGE_BEND = 2 * MAX_RADIUS;

    public CraftingPlanRouteCrossing {
        underpasses = List.copyOf(underpasses);
    }

    public record Underpass(int segmentId, double x, double gapHalfWidth) {}

    static List<CraftingPlanRouteCrossing> find(List<PlacedNode> nodes, List<RoutedEdge> routes,
                                                List<Segment> segments, List<Run> runs) {
        int[] runBySegment = new int[segments.size()];
        var verticalByX = new Double2ObjectAVLTreeMap<RunIntervals>();
        var horizontalByY = new Double2ObjectAVLTreeMap<RunIntervals>();
        List<Event> events = new ObjectArrayList<>();
        for (int runId = 0; runId < runs.size(); runId++) {
            Run run = runs.get(runId);
            for (int segmentId : run.segmentIds()) runBySegment[segmentId] = runId;
            if (horizontal(run)) {
                horizontalByY.computeIfAbsent(run.from().y(), unused -> new RunIntervals()).add(runId,
                        Math.min(run.from().x(), run.to().x()), Math.max(run.from().x(), run.to().x()));
                events.add(new Event(Math.min(run.from().x(), run.to().x()), 0, runId));
                events.add(new Event(Math.max(run.from().x(), run.to().x()), 2, runId));
            } else {
                events.add(new Event(run.from().x(), 1, runId));
                verticalByX.computeIfAbsent(run.from().x(), unused -> new RunIntervals()).add(runId,
                        Math.min(run.from().y(), run.to().y()), Math.max(run.from().y(), run.to().y()));
            }
        }
        verticalByX.values().forEach(RunIntervals::build);
        horizontalByY.values().forEach(RunIntervals::build);
        var junctions = junctions(routes, segments, runBySegment);
        NodeIndex nodeIndex = new NodeIndex(nodes);
        CrossingIndex accepted = new CrossingIndex();
        events.sort(Comparator.comparingDouble(Event::x).thenComparingInt(Event::kind));
        var active = new Double2ObjectAVLTreeMap<IntArrayList>();
        List<CraftingPlanRouteCrossing> result = new ObjectArrayList<>();
        for (Event event : events) {
            Run run = runs.get(event.runId());
            if (event.kind() == 0) {
                active.computeIfAbsent(run.from().y(), unused -> new IntArrayList()).add(event.runId());
            } else if (event.kind() == 2) {
                IntArrayList ids = active.get(run.from().y());
                ids.rem(event.runId());
                if (ids.isEmpty()) active.remove(run.from().y());
            } else {
                double minY = Math.min(run.from().y(), run.to().y());
                double maxY = Math.max(run.from().y(), run.to().y());
                for (var entry : active.tailMap(minY).double2ObjectEntrySet()) {
                    double y = entry.getDoubleKey();
                    if (y > maxY) break;
                    for (int horizontalRun : entry.getValue()) {
                        CraftingPlanRouteCrossing crossing = bridge(nodeIndex, runs, verticalByX, horizontalByY, junctions,
                                segments, accepted, event.runId(), horizontalRun, event.x(), y);
                        if (crossing != null) {
                            result.add(crossing);
                            if (crossing.radius() > 0) accepted.add(crossing);
                        }
                    }
                }
            }
        }
        result = mergeDenseCrossings(result, segments, runs, runBySegment, nodeIndex,
                verticalByX, horizontalByY, junctions, accepted);
        result.sort(Comparator.comparingInt(CraftingPlanRouteCrossing::bridgeSegmentId)
                .thenComparingDouble(CraftingPlanRouteCrossing::y));
        return List.copyOf(result);
    }

    private static List<CraftingPlanRouteCrossing> mergeDenseCrossings(
                                                                       List<CraftingPlanRouteCrossing> crossings, List<Segment> segments, List<Run> runs, int[] runBySegment,
                                                                       NodeIndex nodes, Double2ObjectAVLTreeMap<RunIntervals> verticalByX,
                                                                       Double2ObjectAVLTreeMap<RunIntervals> horizontalByY, JunctionIndex junctions,
                                                                       CrossingIndex accepted) {
        var fallbackByBridge = new Int2ObjectOpenHashMap<ObjectArrayList<CraftingPlanRouteCrossing>>();
        List<CraftingPlanRouteCrossing> result = new ObjectArrayList<>();
        for (CraftingPlanRouteCrossing crossing : crossings) {
            if (crossing.radius() > 0) result.add(crossing);
            else fallbackByBridge.computeIfAbsent(crossing.bridgeSegmentId(), unused -> new ObjectArrayList<>()).add(crossing);
        }
        for (var entry : fallbackByBridge.int2ObjectEntrySet()) {
            ObjectArrayList<CraftingPlanRouteCrossing> candidates = entry.getValue();
            candidates.sort(Comparator.comparingDouble(CraftingPlanRouteCrossing::y));
            for (int first = 0; first < candidates.size();) {
                int end = first + 1;
                while (end < candidates.size() && candidates.get(end).y() - candidates.get(end - 1).y() <= DENSE_CLUSTER_GAP) end++;
                CraftingPlanRouteCrossing merged = end - first > 1 ? denseBridge(candidates.subList(first, end), entry.getIntKey(), segments, runs,
                        runBySegment, nodes, verticalByX, horizontalByY, junctions, accepted) : null;
                if (merged == null) result.addAll(candidates.subList(first, end));
                else {
                    result.add(merged);
                    accepted.add(merged);
                }
                first = end;
            }
        }
        return result;
    }

    private static @Nullable CraftingPlanRouteCrossing denseBridge(
                                                                   List<CraftingPlanRouteCrossing> cluster, int bridgeSegmentId, List<Segment> segments, List<Run> runs,
                                                                   int[] runBySegment, NodeIndex nodes, Double2ObjectAVLTreeMap<RunIntervals> verticalByX,
                                                                   Double2ObjectAVLTreeMap<RunIntervals> horizontalByY, JunctionIndex junctions,
                                                                   CrossingIndex accepted) {
        CraftingPlanRouteCrossing first = cluster.getFirst();
        CraftingPlanRouteCrossing last = cluster.getLast();
        double centerY = (first.y() + last.y()) / 2;
        double radius = (last.y() - first.y()) / 2 + MAX_RADIUS;
        Segment bridgeSegment = segments.get(bridgeSegmentId);
        if (!bridgeFits(bridgeSegment, centerY, radius)) return null;
        int verticalRun = runBySegment[bridgeSegmentId];
        if (junctions.near(verticalRun, centerY, radius + GAP_HALF_WIDTH)) return null;
        for (int side = 1; side >= -1; side -= 2) {
            double bend = side * DENSE_BRIDGE_BEND;
            List<Underpass> underpasses = new ObjectArrayList<>(cluster.size());
            IntSet allowedHorizontalRuns = new IntOpenHashSet();
            boolean fits = true;
            for (CraftingPlanRouteCrossing crossing : cluster) {
                double gapX = bridgeX(first.x(), centerY, bend, radius, crossing.y());
                for (Underpass underpass : crossing.underpasses()) {
                    int horizontalRun = runBySegment[underpass.segmentId()];
                    int underSegment = segmentAt(runs.get(horizontalRun), segments, gapX, true);
                    if (underSegment < 0 || !gapFits(segments.get(underSegment), gapX)) {
                        fits = false;
                        break;
                    }
                    allowedHorizontalRuns.add(horizontalRun);
                    underpasses.add(new Underpass(underSegment, gapX, underpass.gapHalfWidth()));
                }
                if (!fits) break;
            }
            if (!fits || denseBridgeBlocked(first.x(), centerY, bend, radius, verticalRun,
                    allowedHorizontalRuns, nodes, verticalByX, horizontalByY, accepted))
                continue;
            return new CraftingPlanRouteCrossing(bridgeSegmentId, first.x(), centerY, bend, radius, underpasses);
        }
        return null;
    }

    private static boolean denseBridgeBlocked(
                                              double x, double y, double bend, double radius, int verticalRun, IntSet allowedHorizontalRuns,
                                              NodeIndex nodes, Double2ObjectAVLTreeMap<RunIntervals> verticalByX,
                                              Double2ObjectAVLTreeMap<RunIntervals> horizontalByY, CrossingIndex accepted) {
        double minX = Math.min(x, x + bend) - GAP_HALF_WIDTH;
        double maxX = Math.max(x, x + bend) + GAP_HALF_WIDTH;
        double minY = y - radius - GAP_HALF_WIDTH;
        double maxY = y + radius + GAP_HALF_WIDTH;
        if (nodes.intersects(minX, minY, maxX, maxY) || accepted.intersects(minX, minY, maxX, maxY)) return true;
        for (var entry : verticalByX.tailMap(minX).double2ObjectEntrySet()) {
            if (entry.getDoubleKey() > maxX) break;
            if (entry.getValue().intersects(minY, maxY, verticalRun)) return true;
        }
        for (var entry : horizontalByY.tailMap(minY).double2ObjectEntrySet()) {
            if (entry.getDoubleKey() > maxY) break;
            if (entry.getValue().intersects(minX, maxX, allowedHorizontalRuns)) return true;
        }
        return false;
    }

    private static double bridgeX(double x, double y, double bend, double radius, double crossingY) {
        if (crossingY <= y) {
            double local = Math.sqrt(Math.clamp((crossingY - (y - radius)) / radius, 0, 1));
            return x + bend * (2 * local - local * local);
        }
        double local = 1 - Math.sqrt(1 - Math.clamp((crossingY - y) / radius, 0, 1));
        return x + bend * (1 - local * local);
    }

    private static JunctionIndex junctions(List<RoutedEdge> routes, List<Segment> segments, int[] runBySegment) {
        LongSet pairs = new LongOpenHashSet();
        var verticalYs = new Int2ObjectOpenHashMap<DoubleList>();
        for (RoutedEdge route : routes) {
            for (int index = 1; index < route.segmentRanges().size(); index++) {
                IntList previous = route.segmentRanges().get(index - 1);
                IntList next = route.segmentRanges().get(index);
                int previousId = previous.getInt(previous.size() - 1);
                int nextId = next.getInt(0);
                Segment from = segments.get(previousId);
                Segment to = segments.get(nextId);
                if (horizontal(from) == horizontal(to)) continue;
                int horizontal = horizontal(from) ? runBySegment[previousId] : runBySegment[nextId];
                int vertical = horizontal(from) ? runBySegment[nextId] : runBySegment[previousId];
                pairs.add(pair(horizontal, vertical));
                verticalYs.computeIfAbsent(vertical, unused -> new DoubleArrayList()).add(from.to().y());
            }
        }
        verticalYs.values().forEach(markers -> markers.sort(DoubleComparators.NATURAL_COMPARATOR));
        return new JunctionIndex(pairs, verticalYs);
    }

    @Nullable
    private static CraftingPlanRouteCrossing bridge(NodeIndex nodes, List<Run> runs,
                                                    Double2ObjectAVLTreeMap<RunIntervals> verticalByX,
                                                    Double2ObjectAVLTreeMap<RunIntervals> horizontalByY, JunctionIndex junctions,
                                                    List<Segment> segments, CrossingIndex accepted,
                                                    int verticalRun, int horizontalRun, double x, double y) {
        Run vertical = runs.get(verticalRun);
        Run horizontal = runs.get(horizontalRun);
        if (junctions.pairs().contains(pair(horizontalRun, verticalRun))) return null;
        for (double span : SPANS) {
            if (!interior(vertical, y, false, span) || !interior(horizontal, x, true, span)) continue;
            int bridgeSegment = segmentAt(vertical, segments, y, false);
            // A shared-membership boundary is not safely inside one drawable segment; leave it unbridged.
            if (bridgeSegment < 0 || !bridgeFits(segments.get(bridgeSegment), y, span)) continue;
            for (int side = 1; side >= -1; side -= 2) {
                double bend = side * span;
                double center = x + bend;
                int underSegment = segmentAt(horizontal, segments, center, true);
                if (underSegment < 0 || !gapFits(segments.get(underSegment), center)) continue;
                if (blocked(nodes, verticalByX, horizontalByY, junctions, accepted, horizontalRun, verticalRun, x, y, bend, span)) continue;
                return new CraftingPlanRouteCrossing(bridgeSegment, x, y, bend, span,
                        List.of(new Underpass(underSegment, center, GAP_HALF_WIDTH)));
            }
        }
        // Dense channels may not have enough clearance for an arch. A zero-radius bridge still gives
        // the horizontal underpass a finite gap, so the crossing cannot be mistaken for a junction.
        int bridgeSegment = segmentAt(vertical, segments, y, false);
        int underSegment = segmentAt(horizontal, segments, x, true);
        if (bridgeSegment >= 0 && underSegment >= 0 && gapFits(segments.get(underSegment), x) && !junctions.near(verticalRun, y, GAP_HALF_WIDTH)) {
            return new CraftingPlanRouteCrossing(bridgeSegment, x, y, 0, 0,
                    List.of(new Underpass(underSegment, x, GAP_HALF_WIDTH)));
        }
        return null;
    }

    private static boolean blocked(NodeIndex nodes, Double2ObjectAVLTreeMap<RunIntervals> verticalByX,
                                   Double2ObjectAVLTreeMap<RunIntervals> horizontalByY, JunctionIndex junctions,
                                   CrossingIndex accepted,
                                   int horizontalRun, int verticalRun, double x, double y, double bend, double radius) {
        double minX = Math.min(x, x + bend) - GAP_HALF_WIDTH;
        double maxX = Math.max(x, x + bend) + GAP_HALF_WIDTH;
        double minY = y - radius - GAP_HALF_WIDTH;
        double maxY = y + radius + GAP_HALF_WIDTH;
        if (nodes.intersects(minX, minY, maxX, maxY)) return true;
        if (junctions.near(verticalRun, y, radius + GAP_HALF_WIDTH)) return true;
        if (accepted.intersects(minX, minY, maxX, maxY)) return true;
        for (var entry : horizontalByY.tailMap(minY).double2ObjectEntrySet()) {
            if (entry.getDoubleKey() > maxY) break;
            if (entry.getValue().intersects(minX, maxX, horizontalRun)) return true;
        }
        for (var entry : verticalByX.tailMap(minX).double2ObjectEntrySet()) {
            if (entry.getDoubleKey() > maxX) break;
            if (entry.getValue().intersects(minY, maxY, verticalRun)) return true;
        }
        return false;
    }

    private static int segmentAt(Run run, List<Segment> segments, double coordinate, boolean horizontal) {
        double origin = horizontal ? run.from().x() : run.from().y();
        double distance = Math.abs(coordinate - origin);
        int first = 0;
        int last = run.segmentIds().size() - 1;
        while (first < last) {
            int middle = (first + last + 1) >>> 1;
            Segment segment = segments.get(run.segmentIds().getInt(middle));
            double start = Math.abs((horizontal ? segment.from().x() : segment.from().y()) - origin);
            if (start <= distance) first = middle;
            else last = middle - 1;
        }
        int id = run.segmentIds().getInt(first);
        Segment segment = segments.get(id);
        double start = horizontal ? Math.min(segment.from().x(), segment.to().x()) : Math.min(segment.from().y(), segment.to().y());
        double end = horizontal ? Math.max(segment.from().x(), segment.to().x()) : Math.max(segment.from().y(), segment.to().y());
        return coordinate > start && coordinate < end ? id : -1;
    }

    private static boolean gapFits(Segment segment, double center) {
        return center - GAP_HALF_WIDTH > Math.min(segment.from().x(), segment.to().x()) && center + GAP_HALF_WIDTH < Math.max(segment.from().x(), segment.to().x());
    }

    private static boolean bridgeFits(Segment segment, double y, double radius) {
        return y - radius > Math.min(segment.from().y(), segment.to().y()) && y + radius < Math.max(segment.from().y(), segment.to().y());
    }

    private static boolean interior(Run run, double coordinate, boolean horizontal, double span) {
        double start = horizontal ? Math.min(run.from().x(), run.to().x()) : Math.min(run.from().y(), run.to().y());
        double end = horizontal ? Math.max(run.from().x(), run.to().x()) : Math.max(run.from().y(), run.to().y());
        return coordinate - start >= span + GAP_HALF_WIDTH && end - coordinate >= span + GAP_HALF_WIDTH;
    }

    private static boolean horizontal(Run run) {
        return run.from().y() == run.to().y();
    }

    private static boolean horizontal(Segment segment) {
        return segment.from().y() == segment.to().y();
    }

    private static long pair(int horizontal, int vertical) {
        return (long) horizontal << 32 | vertical & 0xFFFFFFFFL;
    }

    private record Event(double x, int kind, int runId) {}

    /** Interval tree within one coordinate bucket; disjoint collinear runs are pruned instead of all scanned. */
    private static final class RunIntervals {

        private final List<Span> spans = new ObjectArrayList<>();
        private double[] maximumEnds = new double[0];

        private void add(int run, double start, double end) {
            spans.add(new Span(run, start, end));
        }

        private void build() {
            spans.sort(Comparator.comparingDouble(Span::start).thenComparingInt(Span::run));
            maximumEnds = new double[spans.size()];
            build(0, spans.size());
        }

        private double build(int first, int end) {
            if (first == end) return Double.NEGATIVE_INFINITY;
            int middle = (first + end) >>> 1;
            return maximumEnds[middle] = Math.max(spans.get(middle).end(),
                    Math.max(build(first, middle), build(middle + 1, end)));
        }

        private boolean intersects(double start, double end, int excluded) {
            return intersects(start, end, excluded, 0, spans.size());
        }

        private boolean intersects(double start, double end, IntSet allowed) {
            return intersects(start, end, allowed, 0, spans.size());
        }

        private boolean intersects(double start, double end, int excluded, int first, int last) {
            if (first == last) return false;
            int middle = (first + last) >>> 1;
            if (maximumEnds[middle] <= start || spans.get(first).start() >= end) return false;
            Span span = spans.get(middle);
            return span.run() != excluded && span.start() < end && span.end() > start || intersects(start, end, excluded, first, middle) || intersects(start, end, excluded, middle + 1, last);
        }

        private boolean intersects(double start, double end, IntSet allowed, int first, int last) {
            if (first == last) return false;
            int middle = (first + last) >>> 1;
            if (maximumEnds[middle] <= start || spans.get(first).start() >= end) return false;
            Span span = spans.get(middle);
            return !allowed.contains(span.run()) && span.start() < end && span.end() > start || intersects(start, end, allowed, first, middle) || intersects(start, end, allowed, middle + 1, last);
        }

        private record Span(int run, double start, double end) {}
    }

    private record JunctionIndex(LongSet pairs, Int2ObjectOpenHashMap<DoubleList> verticalYs) {

        private boolean near(int verticalRun, double y, double radius) {
            DoubleList markers = verticalYs.get(verticalRun);
            if (markers == null) return false;
            int low = 0;
            int high = markers.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (markers.getDouble(middle) < y) low = middle + 1;
                else high = middle;
            }
            return low < markers.size() && markers.getDouble(low) - y <= radius || low > 0 && y - markers.getDouble(low - 1) <= radius;
        }
    }

    private static final class CrossingIndex {

        private static final int CELL = 16;
        private final Long2ObjectOpenHashMap<ObjectArrayList<CraftingPlanRouteCrossing>> cells = new Long2ObjectOpenHashMap<>();

        private void add(CraftingPlanRouteCrossing crossing) {
            double minX = Math.min(crossing.x(), crossing.x() + crossing.bend()) - GAP_HALF_WIDTH;
            double maxX = Math.max(crossing.x(), crossing.x() + crossing.bend()) + GAP_HALF_WIDTH;
            double minY = crossing.y() - crossing.radius() - GAP_HALF_WIDTH;
            double maxY = crossing.y() + crossing.radius() + GAP_HALF_WIDTH;
            for (int x = (int) Math.floor(minX / CELL); x <= (int) Math.floor(maxX / CELL); x++) {
                for (int y = (int) Math.floor(minY / CELL); y <= (int) Math.floor(maxY / CELL); y++) {
                    cells.computeIfAbsent(NodeIndex.cell(x, y), unused -> new ObjectArrayList<>()).add(crossing);
                }
            }
        }

        private boolean intersects(double minX, double minY, double maxX, double maxY) {
            for (int x = (int) Math.floor(minX / CELL); x <= (int) Math.floor(maxX / CELL); x++) {
                for (int y = (int) Math.floor(minY / CELL); y <= (int) Math.floor(maxY / CELL); y++) {
                    List<CraftingPlanRouteCrossing> crossings = cells.get(NodeIndex.cell(x, y));
                    if (crossings == null) continue;
                    for (CraftingPlanRouteCrossing crossing : crossings) {
                        double crossingMinX = Math.min(crossing.x(), crossing.x() + crossing.bend()) - GAP_HALF_WIDTH;
                        double crossingMaxX = Math.max(crossing.x(), crossing.x() + crossing.bend()) + GAP_HALF_WIDTH;
                        double crossingMinY = crossing.y() - crossing.radius() - GAP_HALF_WIDTH;
                        double crossingMaxY = crossing.y() + crossing.radius() + GAP_HALF_WIDTH;
                        if (crossingMinX < maxX && crossingMaxX > minX && crossingMinY < maxY && crossingMaxY > minY) return true;
                    }
                }
            }
            return false;
        }
    }

    private static final class NodeIndex {

        private static final int CELL = 16;
        private final Long2ObjectOpenHashMap<ObjectArrayList<PlacedNode>> cells = new Long2ObjectOpenHashMap<>();

        private NodeIndex(List<PlacedNode> nodes) {
            for (PlacedNode node : nodes) {
                int firstX = (int) Math.floor(node.x() / CELL);
                int lastX = (int) Math.floor((node.x() + node.width()) / CELL);
                int firstY = (int) Math.floor(node.y() / CELL);
                int lastY = (int) Math.floor((node.y() + node.height()) / CELL);
                for (int cellX = firstX; cellX <= lastX; cellX++) for (int cellY = firstY; cellY <= lastY; cellY++) {
                    cells.computeIfAbsent(cell(cellX, cellY), unused -> new ObjectArrayList<>()).add(node);
                }
            }
        }

        private boolean intersects(double minX, double minY, double maxX, double maxY) {
            int firstX = (int) Math.floor(minX / CELL);
            int lastX = (int) Math.floor(maxX / CELL);
            int firstY = (int) Math.floor(minY / CELL);
            int lastY = (int) Math.floor(maxY / CELL);
            for (int cellX = firstX; cellX <= lastX; cellX++) for (int cellY = firstY; cellY <= lastY; cellY++) {
                List<PlacedNode> nodes = cells.get(cell(cellX, cellY));
                if (nodes == null) continue;
                for (PlacedNode node : nodes) {
                    if (node.x() < maxX && node.x() + node.width() > minX && node.y() < maxY && node.y() + node.height() > minY) return true;
                }
            }
            return false;
        }

        private static long cell(int x, int y) {
            return (long) x << 32 | y & 0xFFFFFFFFL;
        }
    }
}
