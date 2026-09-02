package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewEdge;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewNode;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Deterministic SCC-DAG layering; cyclic components have a local perimeter instead of an unrolled stage chain. */
public final class CraftingPlanGraphLayout {

    private static final double PADDING = 16;
    private static final double CELL_GAP = 24;
    private static final double GROUP_GAP = 32;
    private static final double LAYER_GAP = 48;

    private CraftingPlanGraphLayout() {}

    public static Layout layout(ViewGraph graph, boolean compact) {
        if (graph.nodes().isEmpty()) {
            return new Layout(List.of(), List.of(), new Bounds(0, 0, 0, 0));
        }
        double cellWidth = compact ? 88 : 92;
        double cellHeight = compact ? 42 : 46;
        Int2ObjectMap<Group> groups = new Int2ObjectAVLTreeMap<>();
        Int2ObjectMap<ViewNode> nodeById = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<IntList> outgoing = new Int2ObjectOpenHashMap<>();
        for (ViewNode node : graph.nodes()) {
            nodeById.put(node.id(), node);
            outgoing.put(node.id(), new IntArrayList());
            groups.computeIfAbsent(node.componentId(), Group::new).nodes.add(node);
        }
        int rootComponent = nodeById.get(graph.rootId()).componentId();
        for (ViewEdge edge : graph.edges()) {
            outgoing.get(edge.source()).add(edge.target());
            int source = nodeById.get(edge.source()).componentId();
            int target = nodeById.get(edge.target()).componentId();
            // A co-product's other producer can consume the target; keep that edge as an exterior back edge.
            if (source != target && target != rootComponent) {
                groups.get(source).children.add(target);
                groups.get(target).parents.add(source);
            }
        }
        outgoing.values().forEach(neighbors -> neighbors.sort(IntComparators.NATURAL_COMPARATOR));
        for (Group group : groups.values()) {
            group.nodes.sort(Comparator.comparingInt((ViewNode node) -> node.id() == graph.rootId() ? 0 : 1)
                    .thenComparingInt(ViewNode::id));
            group.cyclic = group.nodes.getFirst().cyclic();
            if (group.cyclic) {
                orderCycle(group, nodeById, outgoing);
            }
            configureSlots(group, cellWidth, cellHeight);
        }
        IntHeapPriorityQueue ready = new IntHeapPriorityQueue();
        for (Group group : groups.values()) {
            group.remainingParents = group.parents.size();
            if (group.parents.isEmpty()) {
                ready.enqueue(group.id);
            }
        }
        while (!ready.isEmpty()) {
            Group source = groups.get(ready.dequeueInt());
            for (int targetId : source.children) {
                Group target = groups.get(targetId);
                target.rank = Math.max(target.rank, source.rank + 1);
                if (--target.remainingParents == 0) {
                    ready.enqueue(targetId);
                }
            }
        }
        Int2ObjectMap<List<Group>> layers = new Int2ObjectAVLTreeMap<>();
        for (Group group : groups.values()) {
            layers.computeIfAbsent(group.rank, unused -> new ObjectArrayList<>()).add(group);
        }
        IntList ranks = new IntArrayList(layers.keySet());
        for (int sweep = 0; sweep < 4; sweep++) {
            Int2DoubleMap positions = positions(layers);
            boolean downward = sweep % 2 == 0;
            for (int step = 0; step < ranks.size(); step++) {
                int rank = ranks.getInt(downward ? step : ranks.size() - step - 1);
                Int2DoubleMap centers = new Int2DoubleOpenHashMap();
                for (Group group : layers.get(rank)) {
                    centers.put(group.id, barycenter(group, positions, downward));
                }
                layers.get(rank).sort(Comparator.comparingDouble((Group group) -> centers.get(group.id))
                        .thenComparingInt(group -> group.id));
                double nextX = 0;
                for (Group group : layers.get(rank)) {
                    positions.put(group.id, nextX + group.width / 2);
                    nextX += group.width + GROUP_GAP;
                }
            }
        }
        double totalWidth = layers.values().stream().mapToDouble(CraftingPlanGraphLayout::rowWidth).max().orElseThrow();
        Int2DoubleMap rankBottom = new Int2DoubleOpenHashMap();
        double y = PADDING;
        for (int rank : ranks) {
            List<Group> layer = layers.get(rank);
            double x = PADDING + (totalWidth - rowWidth(layer)) / 2;
            double height = layer.stream().mapToDouble(group -> group.height).max().orElseThrow();
            for (Group group : layer) {
                group.x = x;
                group.y = y;
                x += group.width + GROUP_GAP;
            }
            rankBottom.put(rank, y + height);
            y += height + LAYER_GAP;
        }
        Int2ObjectMap<PlacedNode> placed = new Int2ObjectAVLTreeMap<>();
        Int2ObjectMap<Side> outward = new Int2ObjectOpenHashMap<>();
        for (Group group : groups.values()) {
            for (int index = 0; index < group.nodes.size(); index++) {
                ViewNode node = group.nodes.get(index);
                Slot slot = group.slots.get(index);
                double width = node.sourceNode() instanceof Process ? cellWidth : compact ? 76 : 80;
                double height = node.sourceNode() instanceof Process ? compact ? 38 : 40
                        : node.embeddedProcessId() != null ? cellHeight : compact ? 30 : 32;
                double nodeX = group.x + PADDING + slot.column() * (cellWidth + CELL_GAP) + (cellWidth - width) / 2;
                double nodeY = group.y + PADDING + slot.row() * (cellHeight + CELL_GAP);
                placed.put(node.id(), new PlacedNode(node, nodeX, nodeY, width, height));
                outward.put(node.id(), slot.side());
            }
        }
        List<RoutedEdge> routed = new ObjectArrayList<>();
        int channel = 0;
        double maxX = totalWidth + 2 * PADDING;
        double maxY = y - LAYER_GAP;
        for (ViewEdge edge : graph.edges()) {
            PlacedNode source = placed.get(edge.source());
            PlacedNode target = placed.get(edge.target());
            Group sourceGroup = groups.get(source.viewNode().componentId());
            Group targetGroup = groups.get(target.viewNode().componentId());
            List<Point> points = new ObjectArrayList<>();
            if (edge.cyclic()) {
                Bounds perimeter = perimeter(sourceGroup, 4 + channel % 3 * 3);
                Gate from = gate(source, outward.get(source.id()), perimeter);
                Gate to = gate(target, outward.get(target.id()), perimeter);
                points.add(from.port());
                appendBoundary(points, perimeter, from, to, source.id() == target.id());
                points.add(to.port());
            } else if (targetGroup.rank == sourceGroup.rank + 1 && !sourceGroup.cyclic && !targetGroup.cyclic) {
                double sourceX = source.x() + source.width() / 2;
                double targetX = target.x() + target.width() / 2;
                double bandY = rankBottom.get(sourceGroup.rank) + 12 + channel % 9 * 3;
                points.add(new Point(sourceX, source.y() + source.height()));
                points.add(new Point(sourceX, bandY));
                points.add(new Point(targetX, bandY));
                points.add(new Point(targetX, target.y()));
            } else {
                Bounds sourceBoundary = perimeter(sourceGroup, 4);
                Bounds targetBoundary = perimeter(targetGroup, 4);
                Gate from = gate(source, sourceGroup.cyclic ? outward.get(source.id()) : Side.BOTTOM, sourceBoundary);
                Gate to = gate(target, targetGroup.cyclic ? outward.get(target.id()) : Side.TOP, targetBoundary);
                Point bottom = new Point(sourceGroup.x + sourceGroup.width / 2,
                        sourceBoundary.y() + sourceBoundary.height());
                Point top = new Point(targetGroup.x + targetGroup.width / 2, targetBoundary.y());
                points.add(from.port());
                appendBoundary(points, sourceBoundary, from, new Gate(bottom, bottom, Side.BOTTOM), false);
                double sourceBand = rankBottom.get(sourceGroup.rank) + 12 + channel % 9 * 3;
                double targetBand = targetGroup.y - 12;
                double channelX = totalWidth + 2 * PADDING + 20 + channel % 32 * 6;
                // Exit through the group's own empty margin before crossing a layer-wide empty band.
                points.add(new Point(bottom.x(), sourceBand));
                points.add(new Point(channelX, sourceBand));
                points.add(new Point(channelX, targetBand));
                points.add(new Point(top.x(), targetBand));
                appendBoundary(points, targetBoundary, new Gate(top, top, Side.TOP), to, false);
                points.add(to.port());
            }
            for (Point point : points) {
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }
            routed.add(new RoutedEdge(edge.source(), edge.target(), points, edge.cyclic(), edge.originalEdgeIds()));
            channel++;
        }
        return new Layout(new ObjectArrayList<>(placed.values()), routed, new Bounds(0, 0, maxX + PADDING, maxY + PADDING));
    }

    private static void orderCycle(Group group, Int2ObjectMap<ViewNode> nodes, Int2ObjectMap<IntList> outgoing) {
        IntArrayList pending = new IntArrayList();
        IntSet visited = new IntOpenHashSet();
        List<ViewNode> ordered = new ObjectArrayList<>();
        pending.push(group.nodes.getFirst().id());
        while (!pending.isEmpty()) {
            int id = pending.popInt();
            if (!visited.add(id)) {
                continue;
            }
            ordered.add(nodes.get(id));
            IntList children = outgoing.get(id);
            for (int index = children.size() - 1; index >= 0; index--) {
                int child = children.getInt(index);
                if (nodes.get(child).componentId() == group.id && !visited.contains(child)) {
                    pending.push(child);
                }
            }
        }
        group.nodes.clear();
        group.nodes.addAll(ordered);
    }

    private static void configureSlots(Group group, double cellWidth, double cellHeight) {
        int count = group.nodes.size();
        int columns;
        int rows;
        if (!group.cyclic || count <= 2) {
            columns = count;
            rows = 1;
        } else {
            // Allocate perimeter slots dynamically, balancing physical width/height rather than assuming a cycle size.
            columns = Math.max(2, (int) Math.ceil((count + 4) * (cellHeight + CELL_GAP)
                    / (2 * (cellWidth + cellHeight + 2 * CELL_GAP))));
            rows = Math.max(2, (count - 2 * columns + 5) / 2);
        }
        for (int column = 0; column < columns && group.slots.size() < count; column++) {
            group.slots.add(new Slot(0, column, Side.TOP));
        }
        for (int row = 1; row < rows && group.slots.size() < count; row++) {
            group.slots.add(new Slot(row, columns - 1, Side.RIGHT));
        }
        for (int column = columns - 2; column >= 0 && group.slots.size() < count; column--) {
            group.slots.add(new Slot(rows - 1, column, Side.BOTTOM));
        }
        for (int row = rows - 2; row > 0 && group.slots.size() < count; row--) {
            group.slots.add(new Slot(row, 0, Side.LEFT));
        }
        group.width = 2 * PADDING + columns * cellWidth + (columns - 1) * CELL_GAP;
        group.height = 2 * PADDING + rows * cellHeight + (rows - 1) * CELL_GAP;
    }

    private static Bounds perimeter(Group group, double inset) {
        return new Bounds(group.x + inset, group.y + inset, group.width - 2 * inset, group.height - 2 * inset);
    }

    private static Gate gate(PlacedNode node, Side side, Bounds perimeter) {
        double centerX = node.x() + node.width() / 2;
        double centerY = node.y() + node.height() / 2;
        return switch (side) {
            case TOP -> new Gate(new Point(centerX, node.y()), new Point(centerX, perimeter.y()), side);
            case RIGHT -> new Gate(new Point(node.x() + node.width(), centerY),
                    new Point(perimeter.x() + perimeter.width(), centerY), side);
            case BOTTOM -> new Gate(new Point(centerX, node.y() + node.height()),
                    new Point(centerX, perimeter.y() + perimeter.height()), side);
            case LEFT -> new Gate(new Point(node.x(), centerY), new Point(perimeter.x(), centerY), side);
        };
    }

    private static void appendBoundary(List<Point> points, Bounds boundary, Gate from, Gate to, boolean fullLoop) {
        points.add(from.lane());
        double width = boundary.width();
        double height = boundary.height();
        double length = 2 * (width + height);
        double start = perimeterPosition(boundary, from);
        double finish = perimeterPosition(boundary, to);
        if (finish < start || fullLoop) {
            finish += length;
        }
        double[] corners = { width, width + height, 2 * width + height, length };
        Point[] locations = {
                new Point(boundary.x() + width, boundary.y()),
                new Point(boundary.x() + width, boundary.y() + height),
                new Point(boundary.x(), boundary.y() + height),
                new Point(boundary.x(), boundary.y())
        };
        for (int lap = 0; lap < 2; lap++) {
            for (int corner = 0; corner < corners.length; corner++) {
                double position = corners[corner] + lap * length;
                if (position > start && position < finish) {
                    points.add(locations[corner]);
                }
            }
        }
        points.add(to.lane());
    }

    private static double perimeterPosition(Bounds boundary, Gate gate) {
        return switch (gate.side()) {
            case TOP -> gate.lane().x() - boundary.x();
            case RIGHT -> boundary.width() + gate.lane().y() - boundary.y();
            case BOTTOM -> 2 * boundary.width() + boundary.height() - (gate.lane().x() - boundary.x());
            case LEFT -> 2 * (boundary.width() + boundary.height()) - (gate.lane().y() - boundary.y());
        };
    }

    private static Int2DoubleMap positions(Int2ObjectMap<List<Group>> layers) {
        Int2DoubleMap result = new Int2DoubleOpenHashMap();
        for (List<Group> layer : layers.values()) {
            double x = 0;
            for (Group group : layer) {
                result.put(group.id, x + group.width / 2);
                x += group.width + GROUP_GAP;
            }
        }
        return result;
    }

    private static double barycenter(Group group, Int2DoubleMap positions, boolean downward) {
        IntSet neighbors = downward ? group.parents : group.children;
        if (neighbors.isEmpty()) {
            return positions.get(group.id);
        }
        double sum = 0;
        for (int id : neighbors) {
            sum += positions.get(id);
        }
        return sum / neighbors.size();
    }

    private static double rowWidth(List<Group> groups) {
        return groups.stream().mapToDouble(group -> group.width).sum() + Math.max(0, groups.size() - 1) * GROUP_GAP;
    }

    public record Point(double x, double y) {}

    public record Bounds(double x, double y, double width, double height) {}

    public record PlacedNode(ViewNode viewNode, double x, double y, double width, double height) {

        public int id() {
            return viewNode.id();
        }

        public @Nullable Integer embeddedProcessId() {
            return viewNode.embeddedProcessId();
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
        }
    }

    public record RoutedEdge(int source, int target, List<Point> points, boolean cyclic, List<Integer> originalEdgeIds) {

        public RoutedEdge {
            points = List.copyOf(points);
            originalEdgeIds = List.copyOf(originalEdgeIds);
        }
    }

    public record Layout(List<PlacedNode> nodes, List<RoutedEdge> edges, Bounds bounds) {

        public Layout {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }
    }

    private enum Side { TOP, RIGHT, BOTTOM, LEFT }

    private record Slot(int row, int column, Side side) {}

    private record Gate(Point port, Point lane, Side side) {}

    private static final class Group {

        private final int id;
        private final List<ViewNode> nodes = new ObjectArrayList<>();
        private final List<Slot> slots = new ObjectArrayList<>();
        private final IntSet parents = new IntAVLTreeSet();
        private final IntSet children = new IntAVLTreeSet();
        private int remainingParents;
        private int rank;
        private boolean cyclic;
        private double width;
        private double height;
        private double x;
        private double y;

        private Group(int id) {
            this.id = id;
        }
    }
}
