package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Side;

import it.unimi.dsi.fastutil.doubles.DoubleAVLTreeSet;
import it.unimi.dsi.fastutil.doubles.DoubleSortedSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Sparse obstacle visibility guides. Search adds only local/goal projections, never an X/Y grid. */
final class OrthogonalRoutingGraph {

    static final double CLEARANCE = 4;
    private static final double LANE = 4;
    private static final Side[] SIDES = Side.values();

    final OrthogonalRoutingAxis x;
    final OrthogonalRoutingAxis y;
    private final OrthogonalNodeIndex obstacles;
    private final Int2ObjectMap<int[]> rows = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<int[]> columns = new Int2ObjectOpenHashMap<>();

    OrthogonalRoutingGraph(List<PlacedNode> nodes, List<Port> ports) {
        obstacles = new OrthogonalNodeIndex(nodes, CLEARANCE);
        Set<Point> seeds = new ObjectOpenHashSet<>();
        DoubleSortedSet xs = new DoubleAVLTreeSet();
        DoubleSortedSet ys = new DoubleAVLTreeSet();
        for (PlacedNode node : nodes) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            double left = clean(node.x() - CLEARANCE);
            double right = clean(node.x() + node.width() + CLEARANCE);
            double top = clean(node.y() - CLEARANCE);
            double bottom = clean(node.y() + node.height() + CLEARANCE);
            // Port projections already provide one track per fan-in/fan-out. Two local rings are enough to
            // turn around the card; growing rings with degree made guide construction quadratic on hubs.
            for (int lane = 0; lane < 2; lane++) {
                double offset = lane * LANE;
                for (double px : new double[] { left - offset, right + offset }) {
                    for (double py : new double[] { top - offset, bottom + offset }) add(seeds, px, py);
                    add(seeds, px, top);
                    add(seeds, px, bottom);
                }
                for (double py : new double[] { top - offset, bottom + offset }) {
                    add(seeds, left, py);
                    add(seeds, right, py);
                }
            }
        }
        for (Port port : ports) {
            seeds.add(port.stub());
            xs.add(port.anchor().x());
            ys.add(port.anchor().y());
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Point point : seeds) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        minX = clean(minX - LANE);
        minY = clean(minY - LANE);
        maxX = clean(maxX + LANE);
        maxY = clean(maxY + LANE);
        Set<Point> guides = new ObjectOpenHashSet<>();
        for (Point point : seeds) {
            if (obstacles.contains(point)) continue;
            guides.add(point);
            for (Point end : List.of(new Point(minX, point.y()), new Point(maxX, point.y()),
                    new Point(point.x(), minY), new Point(point.x(), maxY))) {
                var hit = obstacles.firstHit(point, end, -1);
                guides.add(hit == null ? end : hit.point());
            }
        }
        add(guides, minX, minY);
        add(guides, minX, maxY);
        add(guides, maxX, minY);
        add(guides, maxX, maxY);
        for (Point point : seeds) {
            xs.add(point.x());
            ys.add(point.y());
        }
        for (Point point : guides) {
            xs.add(point.x());
            ys.add(point.y());
        }
        addInterColumnTracks(nodes, xs);
        x = new OrthogonalRoutingAxis(xs);
        y = new OrthogonalRoutingAxis(ys);
        Int2ObjectMap<IntList> rowPoints = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<IntList> columnPoints = new Int2ObjectOpenHashMap<>();
        for (Point point : guides) {
            int px = x.index(point.x());
            int py = y.index(point.y());
            rowPoints.computeIfAbsent(py, unused -> new IntArrayList()).add(px);
            columnPoints.computeIfAbsent(px, unused -> new IntArrayList()).add(py);
        }
        freeze(rowPoints, rows);
        freeze(columnPoints, columns);
    }

    boolean terminalClear(Port port) {
        return !obstacles.contains(port.stub()) && obstacles.firstHit(port.anchor(), port.stub(), port.nodeId()) == null;
    }

    boolean clear(Point from, Point to) {
        return obstacles.firstHit(from, to, -1) == null;
    }

    long key(Point point) {
        return OrthogonalRoutingAxis.point(x.index(point.x()), y.index(point.y()));
    }

    Point point(long key) {
        return new Point(x.value(OrthogonalRoutingAxis.x(key)), y.value(OrthogonalRoutingAxis.y(key)));
    }

    /** Bounded-degree visibility expansion; projections stop at the first obstacle face. */
    LongSet neighbors(long key, List<Port> goals, OrthogonalSegmentReservations reservations,
                      CraftingPlanRouteGroup group, boolean respectReservations) {
        int px = OrthogonalRoutingAxis.x(key);
        int py = OrthogonalRoutingAxis.y(key);
        LongSet candidates = new LongOpenHashSet(16);
        adjacent(rows.get(py), px, py, true, candidates);
        adjacent(columns.get(px), py, px, false, candidates);
        for (Port goal : goals) {
            candidates.add(OrthogonalRoutingAxis.point(x.index(goal.stub().x()), py));
            candidates.add(OrthogonalRoutingAxis.point(px, y.index(goal.stub().y())));
        }
        Point from = point(key);
        LongSet result = new LongOpenHashSet(16);
        boolean conflict = false;
        for (long next : candidates) {
            if (key == next) continue;
            Point to = point(next);
            var hit = obstacles.firstHit(from, to, -1);
            if (hit != null) {
                to = hit.point();
                next = key(to);
            }
            if (next == key) continue;
            if (!respectReservations || reservations.available(from, to, group)) {
                result.add(next);
            } else {
                conflict = true;
            }
        }
        if (conflict) {
            // Only conflicted corridors get local bypass projections, rather than flooding all coordinate pairs.
            for (long next : new long[] {
                    OrthogonalRoutingAxis.point(x.floor(from.x() - LANE), py),
                    OrthogonalRoutingAxis.point(x.ceiling(from.x() + LANE), py),
                    OrthogonalRoutingAxis.point(px, y.floor(from.y() - LANE)),
                    OrthogonalRoutingAxis.point(px, y.ceiling(from.y() + LANE)) }) {
                Point to = point(next);
                if (next != key && !result.contains(next) && clear(from, to) && (!respectReservations || reservations.available(from, to, group))) {
                    result.add(next);
                }
            }
        }
        return result;
    }

    List<List<Point>> quickPaths(Port from, Port to) {
        Point start = from.stub();
        Point end = to.stub();
        List<List<Point>> paths = new ObjectArrayList<>(8);
        paths.add(List.of(start, new Point(end.x(), start.y()), end));
        paths.add(List.of(start, new Point(start.x(), end.y()), end));
        double centerX = (start.x() + end.x()) / 2;
        double centerY = (start.y() + end.y()) / 2;
        for (int index : new int[] { x.floor(centerX), x.ceiling(centerX) }) {
            double lane = x.value(index);
            paths.add(List.of(start, new Point(lane, start.y()), new Point(lane, end.y()), end));
        }
        for (int index : new int[] { y.floor(centerY), y.ceiling(centerY) }) {
            double lane = y.value(index);
            paths.add(List.of(start, new Point(start.x(), lane), new Point(end.x(), lane), end));
        }
        return paths;
    }

    /** Existing compressed tracks between facing ports; no dense X/Y product is generated. */
    List<List<Point>> localChannelPaths(Port from, Port to) {
        Point start = from.stub();
        Point end = to.stub();
        List<List<Point>> paths = new ObjectArrayList<>();
        boolean horizontal = from.side() == Side.RIGHT && to.side() == Side.LEFT && start.x() < end.x() || from.side() == Side.LEFT && to.side() == Side.RIGHT && start.x() > end.x();
        if (horizontal) {
            int first = x.ceiling(Math.min(start.x(), end.x()) + OrthogonalSegmentReservations.EPSILON);
            int last = x.floor(Math.max(start.x(), end.x()) - OrthogonalSegmentReservations.EPSILON);
            for (int index = first; index <= last; index++) {
                double lane = x.value(index);
                paths.add(List.of(start, new Point(lane, start.y()), new Point(lane, end.y()), end));
            }
            return paths;
        }
        boolean vertical = from.side() == Side.BOTTOM && to.side() == Side.TOP && start.y() < end.y() || from.side() == Side.TOP && to.side() == Side.BOTTOM && start.y() > end.y();
        if (vertical) {
            int first = y.ceiling(Math.min(start.y(), end.y()) + OrthogonalSegmentReservations.EPSILON);
            int last = y.floor(Math.max(start.y(), end.y()) - OrthogonalSegmentReservations.EPSILON);
            for (int index = first; index <= last; index++) {
                double lane = y.value(index);
                paths.add(List.of(start, new Point(start.x(), lane), new Point(end.x(), lane), end));
            }
        }
        return paths;
    }

    static Port port(PlacedNode node, Side side, double fraction) {
        double tangentX = clean(node.x() + 6 + fraction * (node.width() - 12));
        double tangentY = clean(node.y() + 6 + fraction * (node.height() - 12));
        Point anchor = switch (side) {
            case TOP -> new Point(tangentX, clean(node.y()));
            case RIGHT -> new Point(clean(node.x() + node.width()), tangentY);
            case BOTTOM -> new Point(tangentX, clean(node.y() + node.height()));
            case LEFT -> new Point(clean(node.x()), tangentY);
        };
        Point stub = switch (side) {
            case TOP -> new Point(anchor.x(), clean(anchor.y() - CLEARANCE));
            case RIGHT -> new Point(clean(anchor.x() + CLEARANCE), anchor.y());
            case BOTTOM -> new Point(anchor.x(), clean(anchor.y() + CLEARANCE));
            case LEFT -> new Point(clean(anchor.x() - CLEARANCE), anchor.y());
        };
        return new Port(node.id(), side, anchor, stub);
    }

    static Side[] sides() {
        return SIDES;
    }

    private static void addInterColumnTracks(List<PlacedNode> nodes, DoubleSortedSet coordinates) {
        List<Column> columns = new ObjectArrayList<>(nodes.size());
        for (PlacedNode node : nodes) {
            columns.add(new Column(clean(node.x() - CLEARANCE), clean(node.x() + node.width() + CLEARANCE)));
        }
        columns.sort(Comparator.comparingDouble(Column::left).thenComparingDouble(Column::right));
        double right = columns.getFirst().right();
        for (int index = 1; index < columns.size(); index++) {
            Column column = columns.get(index);
            if (column.left() <= right) {
                right = Math.max(right, column.right());
                continue;
            }
            for (int lane = 1; lane <= 64; lane++) {
                coordinates.add(clean(right + (column.left() - right) * lane / 65));
            }
            right = column.right();
        }
    }

    private static void adjacent(int @Nullable [] coordinates, int position, int fixed, boolean horizontal, LongSet result) {
        if (coordinates == null) return;
        int found = Arrays.binarySearch(coordinates, position);
        int next = found >= 0 ? found + 1 : -found - 1;
        int previous = found >= 0 ? found - 1 : next - 1;
        if (previous >= 0) result.add(horizontal ? OrthogonalRoutingAxis.point(coordinates[previous], fixed) : OrthogonalRoutingAxis.point(fixed, coordinates[previous]));
        if (next < coordinates.length) result.add(horizontal ? OrthogonalRoutingAxis.point(coordinates[next], fixed) : OrthogonalRoutingAxis.point(fixed, coordinates[next]));
    }

    private static void freeze(Int2ObjectMap<IntList> source, Int2ObjectMap<int[]> destination) {
        for (var entry : source.int2ObjectEntrySet()) {
            entry.getValue().sort(IntComparators.NATURAL_COMPARATOR);
            destination.put(entry.getIntKey(), entry.getValue().toIntArray());
        }
    }

    private static void add(Set<Point> points, double x, double y) {
        points.add(new Point(clean(x), clean(y)));
    }

    private static double clean(double coordinate) {
        return OrthogonalRoutingAxis.normalize(coordinate);
    }

    record Port(int nodeId, Side side, Point anchor, Point stub) {}

    private record Column(double left, double right) {}
}
