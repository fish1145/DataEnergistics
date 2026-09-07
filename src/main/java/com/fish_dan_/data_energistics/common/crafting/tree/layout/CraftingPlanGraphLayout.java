package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanBranchPlacement.Block;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.SegmentRange;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGroup.Style;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewEdge;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewNode;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Rooted dependency branches run left to right; shared materials and cyclic components retain one placement. */
public final class CraftingPlanGraphLayout {

    private CraftingPlanGraphLayout() {}

    public static Layout layout(ViewGraph graph, boolean compact) {
        if (graph.nodes().isEmpty()) {
            return new Layout(List.of(), List.of(), new Bounds(0, 0, 0, 0),
                    CraftingPlanRouteGeometry.EMPTY, List.of());
        }
        Spacing spacing = compact ? Spacing.COMPACT : Spacing.RELAXED;
        // Retain the perimeter calculation's virtual axes, then publish upright cards with rank along X.
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
        Int2IntMap portCounts = portCounts(graph);
        outgoing.values().forEach(neighbors -> neighbors.sort(IntComparators.NATURAL_COMPARATOR));
        for (Group group : groups.values()) {
            group.nodes.sort(Comparator.comparingInt((ViewNode node) -> node.id() == graph.rootId() ? 0 : 1)
                    .thenComparingInt(ViewNode::id));
            group.cyclic = group.nodes.getFirst().cyclic();
            if (group.cyclic) {
                orderCycle(group, nodeById, outgoing);
            }
            configureSlots(group, cellWidth, cellHeight, spacing, portCounts);
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
        List<Block> blocks = new ObjectArrayList<>(groups.size());
        for (Group group : groups.values()) {
            blocks.add(new Block(group.id, group.rank, group.width, new IntArrayList(group.children)));
        }
        Int2DoubleMap centers = CraftingPlanBranchPlacement.centers(blocks, rootComponent, spacing.groupGap());
        for (Group group : groups.values()) {
            group.cross = spacing.routingPadding() + centers.get(group.id) - group.width / 2;
        }
        positionDepth(layers, spacing, new Int2IntOpenHashMap());
        Int2ObjectMap<PlacedNode> placed = new Int2ObjectAVLTreeMap<>();
        Int2IntMap nodeRanks = new Int2IntOpenHashMap();
        for (Group group : groups.values()) {
            for (ViewNode node : group.nodes) nodeRanks.put(node.id(), group.rank);
            placeNodes(group, compact, cellWidth, cellHeight, spacing, placed);
        }
        Int2IntMap channelTracks = channelTracks(graph, placed, nodeRanks);
        positionDepth(layers, spacing, channelTracks);
        for (Group group : groups.values()) placeNodes(group, compact, cellWidth, cellHeight, spacing, placed);
        return CraftingPlanEdgeRouter.route(graph, new ObjectArrayList<>(placed.values()), spacing, nodeRanks);
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

    private static void configureSlots(Group group, double cellWidth, double cellHeight, Spacing spacing,
                                       Int2IntMap portCounts) {
        group.cellWidth = cellWidth;
        for (ViewNode node : group.nodes) {
            group.cellWidth = Math.max(group.cellWidth, 12 + 2D * portCounts.get(node.id()));
        }
        int count = group.nodes.size();
        int columns;
        int rows;
        if (!group.cyclic || count <= 2) {
            columns = count;
            rows = 1;
        } else {
            // Allocate perimeter slots dynamically, balancing physical width/height rather than assuming a cycle size.
            columns = Math.max(2, (int) Math.ceil((count + 4) * (cellHeight + spacing.cellGap()) / (2 * (group.cellWidth + cellHeight + 2 * spacing.cellGap()))));
            rows = Math.max(2, (count - 2 * columns + 5) / 2);
        }
        for (int column = 0; column < columns && group.slots.size() < count; column++) {
            group.slots.add(new Slot(0, column));
        }
        for (int row = 1; row < rows && group.slots.size() < count; row++) {
            group.slots.add(new Slot(row, columns - 1));
        }
        for (int column = columns - 2; column >= 0 && group.slots.size() < count; column--) {
            group.slots.add(new Slot(rows - 1, column));
        }
        for (int row = rows - 2; row > 0 && group.slots.size() < count; row--) {
            group.slots.add(new Slot(row, 0));
        }
        group.width = 2 * spacing.componentPadding() + columns * group.cellWidth + (columns - 1) * spacing.cellGap();
        group.height = 2 * spacing.componentPadding() + rows * cellHeight + (rows - 1) * spacing.cellGap();
    }

    private static Int2IntMap portCounts(ViewGraph graph) {
        var styles = CraftingPlanRouteGroup.indexStyles(graph.source());
        var ports = new ObjectOpenHashSet<NodePort>();
        for (ViewEdge edge : graph.edges()) {
            var edgeStyles = new ObjectOpenHashSet<Style>();
            for (int original : edge.originalEdgeIds()) edgeStyles.add(styles.get(original));
            for (Style style : edgeStyles) {
                int destination = style.materialFlow() ? edge.source() : edge.target();
                ports.add(new NodePort(edge.source(), true, style, destination));
                ports.add(new NodePort(edge.target(), false, style, destination));
            }
        }
        var counts = new Int2IntOpenHashMap();
        for (NodePort port : ports) counts.addTo(port.node(), 1);
        return counts;
    }

    private static void placeNodes(Group group, boolean compact, double cellWidth, double cellHeight, Spacing spacing,
                                   Int2ObjectMap<PlacedNode> placed) {
        for (int index = 0; index < group.nodes.size(); index++) {
            placeNode(group, index, compact, cellWidth, cellHeight, spacing, placed);
        }
    }

    private static void placeNode(Group group, int index, boolean compact, double cellWidth, double cellHeight,
                                  Spacing spacing, Int2ObjectMap<PlacedNode> placed) {
        ViewNode node = group.nodes.get(index);
        Slot slot = group.slots.get(index);
        double slotWidth = Math.max(cellWidth, group.cellWidth);
        double width = slotWidth;
        double height = node.sourceNode() instanceof Process ? cellHeight : compact ? 72 : 80;
        double nodeX = spacing.componentPadding() + slot.column() * (slotWidth + spacing.cellGap());
        double nodeY = spacing.componentPadding() + slot.row() * (cellHeight + spacing.cellGap());
        placed.put(node.id(), new PlacedNode(node, group.depth + nodeY, group.cross + nodeX, height, width));
    }

    private static Int2IntMap channelTracks(ViewGraph graph, Int2ObjectMap<PlacedNode> nodes, Int2IntMap ranks) {
        Int2ObjectMap<List<ChannelEvent>> events = new Int2ObjectOpenHashMap<>();
        var styles = CraftingPlanRouteGroup.indexStyles(graph.source());
        for (ViewEdge edge : graph.edges()) {
            int sourceRank = ranks.get(edge.source());
            int targetRank = ranks.get(edge.target());
            if (Math.abs(sourceRank - targetRank) != 1) continue;
            var distinct = new ObjectOpenHashSet<Style>();
            for (int original : edge.originalEdgeIds()) distinct.add(styles.get(original));
            double sourceY = nodes.get(edge.source()).y() + nodes.get(edge.source()).height() / 2;
            double targetY = nodes.get(edge.target()).y() + nodes.get(edge.target()).height() / 2;
            List<ChannelEvent> boundary = events.computeIfAbsent(Math.min(sourceRank, targetRank),
                    unused -> new ObjectArrayList<>());
            for (int index = 0; index < distinct.size(); index++) {
                boundary.add(new ChannelEvent(Math.min(sourceY, targetY), 1));
                boundary.add(new ChannelEvent(Math.max(sourceY, targetY), -1));
            }
        }
        Int2IntMap result = new Int2IntOpenHashMap();
        for (var entry : events.int2ObjectEntrySet()) {
            entry.getValue().sort(Comparator.comparingDouble(ChannelEvent::coordinate)
                    .thenComparing(Comparator.comparingInt(ChannelEvent::delta).reversed()));
            int active = 0;
            int maximum = 0;
            for (ChannelEvent event : entry.getValue()) {
                active += event.delta();
                maximum = Math.max(maximum, active);
            }
            result.put(entry.getIntKey(), maximum);
        }
        return result;
    }

    private static void positionDepth(Int2ObjectMap<List<Group>> layers, Spacing spacing, Int2IntMap channelTracks) {
        double depth = spacing.routingPadding();
        for (var entry : layers.int2ObjectEntrySet()) {
            double height = 0;
            for (Group group : entry.getValue()) {
                group.depth = depth;
                height = Math.max(height, group.height);
            }
            double gap = Math.max(2 * spacing.routingPadding(), 2D * (channelTracks.get(entry.getIntKey()) + 1));
            depth += height + gap;
        }
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

    public record RoutedCurve(int source, int target, boolean cyclic, IntList originalEdgeIds,
                              CraftingPlanRouteGroup group, Point from, Point firstControl,
                              Point secondControl, Point to) {}

    public record Layout(List<PlacedNode> nodes, List<RoutedEdge> edges, Bounds bounds,
                         CraftingPlanRouteGeometry geometry, List<RoutedCurve> curves) {

        public Layout {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
            curves = List.copyOf(curves);
            if (!curves.isEmpty()) {
                if (edges.size() != curves.size()) throw new IllegalArgumentException("Radial route index is incomplete");
                for (int routeId = 0; routeId < edges.size(); routeId++) {
                    RoutedEdge edge = edges.get(routeId);
                    RoutedCurve curve = curves.get(routeId);
                    if (edge.source() != curve.source() || edge.target() != curve.target() || !edge.group().equals(curve.group())) {
                        throw new IllegalArgumentException("Radial route index mismatch at " + routeId);
                    }
                }
            }
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

    private record Slot(int row, int column) {}

    private record ChannelEvent(double coordinate, int delta) {}

    private record NodePort(int node, boolean source, Style style, int destination) {}

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
        private double cellWidth;
        private double cross;
        private double depth;

        private Group(int id) {
            this.id = id;
        }
    }
}
