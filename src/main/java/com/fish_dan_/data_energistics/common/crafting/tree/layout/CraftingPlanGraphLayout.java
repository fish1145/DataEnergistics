package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanEdgeRouter.Component;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.SegmentRange;
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
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Left-to-right SCC-DAG layering; cyclic components retain a local perimeter instead of an unrolled stage chain. */
public final class CraftingPlanGraphLayout {

    private CraftingPlanGraphLayout() {}

    public static Layout layout(ViewGraph graph, boolean compact) {
        if (graph.nodes().isEmpty()) {
            return new Layout(List.of(), List.of(), new Bounds(0, 0, 0, 0), CraftingPlanRouteGeometry.EMPTY);
        }
        Spacing spacing = compact ? Spacing.COMPACT : Spacing.RELAXED;
        // The router works in layer coordinates. Transpose card footprints now, then publish upright cards
        // horizontally.
        double cellWidth = compact ? 40 : 46;
        double cellHeight = compact ? 84 : 92;
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
            configureSlots(group, cellWidth, cellHeight, spacing);
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
            Int2DoubleMap positions = positions(layers, spacing.groupGap());
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
                    nextX += group.width + spacing.groupGap();
                }
            }
        }
        List<List<Component>> components = new ObjectArrayList<>();
        for (List<Group> layer : layers.values()) {
            List<Component> row = new ObjectArrayList<>();
            for (Group group : layer) {
                placeNodes(group, compact, cellWidth, cellHeight, spacing);
                Int2ObjectMap<Side> outward = new Int2ObjectOpenHashMap<>();
                for (int index = 0; index < group.nodes.size(); index++) {
                    outward.put(group.nodes.get(index).id(), group.slots.get(index).side());
                }
                row.add(new Component(group.id, new ObjectArrayList<>(group.placed.values()), outward,
                        group.width, group.height));
            }
            components.add(row);
        }
        return CraftingPlanEdgeRouter.route(graph, components, spacing);
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

    private static void configureSlots(Group group, double cellWidth, double cellHeight, Spacing spacing) {
        int count = group.nodes.size();
        int columns;
        int rows;
        if (!group.cyclic || count <= 2) {
            columns = count;
            rows = 1;
        } else {
            // Allocate perimeter slots dynamically, balancing physical width/height rather than assuming a cycle size.
            columns = Math.max(2, (int) Math.ceil((count + 4) * (cellHeight + spacing.cellGap()) / (2 * (cellWidth + cellHeight + 2 * spacing.cellGap()))));
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
        group.width = 2 * spacing.componentPadding() + columns * cellWidth + (columns - 1) * spacing.cellGap();
        group.height = 2 * spacing.componentPadding() + rows * cellHeight + (rows - 1) * spacing.cellGap();
    }

    private static void placeNodes(Group group, boolean compact, double cellWidth, double cellHeight, Spacing spacing) {
        for (int index = 0; index < group.nodes.size(); index++) {
            ViewNode node = group.nodes.get(index);
            Slot slot = group.slots.get(index);
            double width = node.sourceNode() instanceof Process ? compact ? 36 : 40 : node.embeddedProcessId() != null ? cellWidth : compact ? 30 : 32;
            double height = node.sourceNode() instanceof Process ? cellHeight : compact ? 72 : 80;
            double nodeX = spacing.componentPadding() + slot.column() * (cellWidth + spacing.cellGap()) + (cellWidth - width) / 2;
            double nodeY = spacing.componentPadding() + slot.row() * (cellHeight + spacing.cellGap());
            group.placed.put(node.id(), new PlacedNode(node, nodeX, nodeY, width, height));
        }
    }

    private static Int2DoubleMap positions(Int2ObjectMap<List<Group>> layers, double groupGap) {
        Int2DoubleMap result = new Int2DoubleOpenHashMap();
        for (List<Group> layer : layers.values()) {
            double x = 0;
            for (Group group : layer) {
                result.put(group.id, x + group.width / 2);
                x += group.width + groupGap;
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

    public record RoutedEdge(int source, int target, boolean cyclic, IntList originalEdgeIds,
                             CraftingPlanRouteGroup group, List<SegmentRange> segmentRanges) {}

    public record Layout(List<PlacedNode> nodes, List<RoutedEdge> edges, Bounds bounds, CraftingPlanRouteGeometry geometry) {

        public Layout {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
        }
    }

    enum Side {
        TOP,
        RIGHT,
        BOTTOM,
        LEFT
    }

    record Spacing(double componentPadding, double cellGap, double groupGap, double routingPadding, double boundaryPadding) {

        private static final Spacing COMPACT = new Spacing(6, 10, 12, 8, 6);
        private static final Spacing RELAXED = new Spacing(16, 24, 32, 16, 8);
    }

    private record Slot(int row, int column, Side side) {}

    private static final class Group {

        private final int id;
        private final List<ViewNode> nodes = new ObjectArrayList<>();
        private final List<Slot> slots = new ObjectArrayList<>();
        private final Int2ObjectMap<PlacedNode> placed = new Int2ObjectAVLTreeMap<>();
        private final IntSet parents = new IntAVLTreeSet();
        private final IntSet children = new IntAVLTreeSet();
        private int remainingParents;
        private int rank;
        private boolean cyclic;
        private double width;
        private double height;

        private Group(int id) {
            this.id = id;
        }
    }
}
