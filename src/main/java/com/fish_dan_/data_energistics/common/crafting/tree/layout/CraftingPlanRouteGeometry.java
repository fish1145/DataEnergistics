package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Bounds;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.RoutedEdge;

import it.unimi.dsi.fastutil.doubles.Double2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.AbstractIntList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

/** Shared directed geometry built once by the layout worker, not by render frames or export tiles. */
public record CraftingPlanRouteGeometry(List<Segment> segments, List<Run> runs, IntSet terminalSegments,
                                        List<CraftingPlanRouteCrossing> crossings) {

    public static final CraftingPlanRouteGeometry EMPTY = new CraftingPlanRouteGeometry(List.of(), List.of(), IntSets.emptySet(), List.of());

    public CraftingPlanRouteGeometry {
        segments = List.copyOf(segments);
        runs = List.copyOf(runs);
        terminalSegments = IntSets.unmodifiable(terminalSegments);
        crossings = List.copyOf(crossings);
    }

    /** Endpoints follow demand-route traversal; material arrows run from {@code to} toward {@code from}. */
    public record Segment(Point from, Point to, CraftingPlanRouteGroup group, IntIterable routeIds) {}

    /** A maximal straight run, independent of membership changes, for continuous color bands and sparse arrows. */
    public record Run(Point from, Point to, CraftingPlanRouteGroup group, IntList segmentIds) {}

    /** Immutable O(1) list view of consecutive segment ids, retaining the original route traversal direction. */
    public static final class SegmentRange extends AbstractIntList implements RandomAccess {

        private final int start;
        private final int end;
        private final boolean forward;

        private SegmentRange(int start, int end, boolean forward) {
            this.start = start;
            this.end = end;
            this.forward = forward;
        }

        public int startInclusive() {
            return start;
        }

        public int endExclusive() {
            return end;
        }

        @Override
        public int getInt(int index) {
            ensureRestrictedIndex(index);
            return forward ? start + index : end - index - 1;
        }

        @Override
        public int size() {
            return end - start;
        }
    }

    /** Temporary route owned by the worker; its point list is discarded after shared geometry is published. */
    record Path(int source, int target, List<Point> points, boolean cyclic, IntList originalEdgeIds,
                CraftingPlanRouteGroup group) {}

    static Layout assemble(List<PlacedNode> nodes, List<Path> paths, Bounds bounds) {
        var lines = new Object2ObjectLinkedOpenHashMap<LineKey, Sweep>();
        List<List<Use>> routeUses = new ObjectArrayList<>(paths.size());
        for (int routeId = 0; routeId < paths.size(); routeId++) {
            Path path = paths.get(routeId);
            List<Use> uses = new ObjectArrayList<>();
            routeUses.add(uses);
            for (int point = 1; point < path.points().size(); point++) {
                Point from = path.points().get(point - 1);
                Point to = path.points().get(point);
                boolean horizontal = Math.abs(from.y() - to.y()) < OrthogonalSegmentReservations.EPSILON;
                double start = normalize(horizontal ? from.x() : from.y());
                double end = normalize(horizontal ? to.x() : to.y());
                if (start == end) continue;
                LineKey key = new LineKey(horizontal, normalize(horizontal ? from.y() : from.x()),
                        end > start, path.group());
                Sweep sweep = lines.computeIfAbsent(key, Sweep::new);
                Use use = new Use(routeId, key.forward());
                uses.add(use);
                sweep.add(use, Math.min(start, end), Math.max(start, end));
            }
        }
        List<Segment> segments = new ObjectArrayList<>();
        List<Run> runs = new ObjectArrayList<>();
        for (Sweep sweep : lines.values()) sweep.finish(segments, runs);
        List<RoutedEdge> routes = new ObjectArrayList<>(paths.size());
        IntSet terminals = new IntAVLTreeSet();
        for (int routeId = 0; routeId < paths.size(); routeId++) {
            Path path = paths.get(routeId);
            List<SegmentRange> ranges = new ObjectArrayList<>(routeUses.get(routeId).size());
            for (Use use : routeUses.get(routeId)) {
                ranges.add(new SegmentRange(use.firstSegment, use.endSegment, use.forward));
            }
            if (path.group().materialFlow() && !ranges.isEmpty()) terminals.add(ranges.getFirst().getInt(0));
            routes.add(new RoutedEdge(path.source(), path.target(), path.cyclic(), path.originalEdgeIds(),
                    path.group(), List.copyOf(ranges)));
        }
        return new Layout(nodes, routes, bounds, new CraftingPlanRouteGeometry(segments, runs, terminals,
                CraftingPlanRouteCrossing.find(nodes, routes, segments, runs)), List.of());
    }

    private static double normalize(double coordinate) {
        double normalized = Math.rint(coordinate / OrthogonalSegmentReservations.EPSILON) * OrthogonalSegmentReservations.EPSILON;
        return normalized == 0 ? 0 : normalized;
    }

    private record LineKey(boolean horizontal, double coordinate, boolean forward, CraftingPlanRouteGroup group) {

        private Point point(double distance) {
            return horizontal ? new Point(distance, coordinate) : new Point(coordinate, distance);
        }
    }

    private static final class Use {

        private final int route;
        private final boolean forward;
        private int firstSegment;
        private int endSegment;

        private Use(int route, boolean forward) {
            this.route = route;
            this.forward = forward;
        }
    }

    private static final class Event {

        private final IntList added = new IntArrayList();
        private final IntList removed = new IntArrayList();
    }

    private static final class Sweep {

        private final LineKey key;
        private final List<Use> uses = new ObjectArrayList<>();
        private final Double2ObjectAVLTreeMap<Event> events = new Double2ObjectAVLTreeMap<>();

        private Sweep(LineKey key) {
            this.key = key;
        }

        private void add(Use use, double start, double end) {
            int id = uses.size();
            uses.add(use);
            events.computeIfAbsent(start, unused -> new Event()).added.add(id);
            events.computeIfAbsent(end, unused -> new Event()).removed.add(id);
        }

        private void finish(List<Segment> segments, List<Run> runs) {
            int active = 0;
            int runStart = segments.size();
            RangeMembership memberships = new RangeMembership(runStart, events.size() - 1);
            double previous = events.firstDoubleKey();
            for (var event : events.double2ObjectEntrySet()) {
                double position = event.getDoubleKey();
                if (position > previous && active > 0) {
                    int segmentId = segments.size();
                    Point from = key.point(key.forward() ? previous : position);
                    Point to = key.point(key.forward() ? position : previous);
                    segments.add(new Segment(from, to, key.group(), memberships.at(segmentId)));
                } else if (segments.size() > runStart) {
                    finishRun(segments, runs, runStart);
                    runStart = segments.size();
                }
                for (int useId : event.getValue().removed) uses.get(useId).endSegment = segments.size();
                for (int useId : event.getValue().added) uses.get(useId).firstSegment = segments.size();
                active += event.getValue().added.size() - event.getValue().removed.size();
                previous = position;
            }
            if (segments.size() > runStart) finishRun(segments, runs, runStart);
            // A leg stores just its interval. Never expand every active route at every branching coordinate.
            uses.sort(Comparator.comparingInt((Use use) -> use.route).thenComparingInt(use -> use.firstSegment));
            int route = -1;
            int start = -1;
            int end = -1;
            for (Use use : uses) {
                if (use.route != route || use.firstSegment > end) {
                    if (route >= 0) memberships.add(start, end, route);
                    route = use.route;
                    start = use.firstSegment;
                    end = use.endSegment;
                } else {
                    end = Math.max(end, use.endSegment);
                }
            }
            memberships.add(start, end, route);
        }

        private void finishRun(List<Segment> segments, List<Run> runs, int start) {
            IntList ids = new SegmentRange(start, segments.size(), key.forward());
            runs.add(new Run(segments.get(ids.getInt(0)).from(), segments.get(ids.getInt(ids.size() - 1)).to(),
                    key.group(), ids));
        }
    }

    /** Canonical interval cover: O(uses log segments) storage and O(log segments + matches) reverse lookup. */
    private static final class RangeMembership {

        private final int firstSegment;
        private final int leafBase;
        private final Int2ObjectMap<IntList> members = new Int2ObjectOpenHashMap<>();

        private RangeMembership(int firstSegment, int segmentLimit) {
            this.firstSegment = firstSegment;
            int base = 1;
            while (base < segmentLimit) base <<= 1;
            this.leafBase = base;
        }

        private void add(int start, int end, int route) {
            int left = start - firstSegment + leafBase;
            int right = end - firstSegment + leafBase;
            while (left < right) {
                if ((left & 1) != 0) members.computeIfAbsent(left++, unused -> new IntArrayList()).add(route);
                if ((right & 1) != 0) members.computeIfAbsent(--right, unused -> new IntArrayList()).add(route);
                left >>= 1;
                right >>= 1;
            }
        }

        private IntIterable at(int segment) {
            return new Membership(this, segment - firstSegment + leafBase);
        }
    }

    /** A query view, not a copied membership list; the enclosing index is immutable after layout publication. */
    private record Membership(RangeMembership index, int leaf) implements IntIterable {

        @Override
        public IntIterator iterator() {
            return new IntIterator() {

                private int node = leaf;
                private IntIterator entries = IntIterators.EMPTY_ITERATOR;

                @Override
                public boolean hasNext() {
                    while (!entries.hasNext() && node > 0) {
                        entries = index.members.getOrDefault(node, IntLists.emptyList()).iterator();
                        node >>= 1;
                    }
                    return entries.hasNext();
                }

                @Override
                public int nextInt() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return entries.nextInt();
                }
            };
        }
    }
}
