package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.SegmentRange;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGroup.Style;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewEdge;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewNode;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
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

/** Left-to-right SCC-DAG layering; cyclic components retain a local perimeter instead of an unrolled stage chain. */
public final class CraftingPlanGraphLayout {

    private CraftingPlanGraphLayout() {}

    public static Layout layout(ViewGraph graph, boolean compact) {
        if (graph.nodes().isEmpty()) {
            return new Layout(List.of(), List.of(), new Bounds(0, 0, 0, 0), CraftingPlanRouteGeometry.EMPTY);
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
        Int2ObjectMap<PlacedNode> placed = new Int2ObjectAVLTreeMap<>();
        Int2IntMap nodeRanks = new Int2IntOpenHashMap();
        positionGroups(layers, spacing);
        for (Group group : groups.values()) {
            for (ViewNode node : group.nodes) nodeRanks.put(node.id(), group.rank);
            placeNodes(group, compact, cellWidth, cellHeight, spacing, placed);
        }
        var score = new CraftingPlanLayoutOrderScore(graph.edges(), placed, nodeRanks);
        improveLayers(layers, score, placed, compact, cellWidth, cellHeight, spacing);
        var attachments = externalAttachments(graph.edges(), nodeById, placed);
        for (Group group : groups.values()) {
            if (group.cyclic && group.nodes.size() > 1) {
                improveCycle(group, graph.rootId(), attachments, score, placed, compact, cellWidth, cellHeight, spacing);
            }
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
        group.width = 2 * spacing.componentPadding() + columns * cellWidth + (columns - 1) * spacing.cellGap();
        group.height = 2 * spacing.componentPadding() + rows * cellHeight + (rows - 1) * spacing.cellGap();
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
        double width = node.sourceNode() instanceof Process ? compact ? 36 : 40 : node.embeddedProcessId() != null ? cellWidth : compact ? 30 : 32;
        double height = node.sourceNode() instanceof Process ? cellHeight : compact ? 72 : 80;
        double nodeX = spacing.componentPadding() + slot.column() * (cellWidth + spacing.cellGap()) + (cellWidth - width) / 2;
        double nodeY = spacing.componentPadding() + slot.row() * (cellHeight + spacing.cellGap());
        placed.put(node.id(), new PlacedNode(node, group.depth + nodeY, group.cross + nodeX, height, width));
    }

    /** Packing depends only on card envelopes and density, never on the number or shape of routed edges. */
    private static void positionGroups(Int2ObjectMap<List<Group>> layers, Spacing spacing) {
        double maximumWidth = 0;
        for (List<Group> layer : layers.values()) maximumWidth = Math.max(maximumWidth, layerWidth(layer, spacing));
        double depth = spacing.routingPadding();
        for (List<Group> layer : layers.values()) {
            double cross = spacing.routingPadding() + (maximumWidth - layerWidth(layer, spacing)) / 2;
            double height = 0;
            for (Group group : layer) {
                group.cross = cross;
                group.depth = depth;
                cross += group.width + spacing.groupGap();
                height = Math.max(height, group.height);
            }
            depth += height + 2 * spacing.routingPadding();
        }
    }

    private static double layerWidth(List<Group> layer, Spacing spacing) {
        double width = Math.max(0, layer.size() - 1) * spacing.groupGap();
        for (Group group : layer) width += group.width;
        return width;
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

    private static void improveLayers(Int2ObjectMap<List<Group>> layers, CraftingPlanLayoutOrderScore score,
                                      Int2ObjectMap<PlacedNode> placed, boolean compact, double cellWidth,
                                      double cellHeight, Spacing spacing) {
        for (int pass = 0; pass < 2; pass++) {
            for (List<Group> layer : layers.values()) {
                for (int step = 0; step + 1 < layer.size(); step++) {
                    int index = pass == 0 ? step : layer.size() - step - 2;
                    Group first = layer.get(index);
                    Group second = layer.get(index + 1);
                    IntList moved = nodeIds(first);
                    for (ViewNode node : second.nodes) moved.add(node.id());
                    double firstCross = first.cross;
                    double secondCross = second.cross;
                    if (score.improve(moved, () -> {
                        second.cross = firstCross;
                        first.cross = firstCross + second.width + spacing.groupGap();
                        placeNodes(first, compact, cellWidth, cellHeight, spacing, placed);
                        placeNodes(second, compact, cellWidth, cellHeight, spacing, placed);
                    }, () -> {
                        first.cross = firstCross;
                        second.cross = secondCross;
                        placeNodes(first, compact, cellWidth, cellHeight, spacing, placed);
                        placeNodes(second, compact, cellWidth, cellHeight, spacing, placed);
                    })) {
                        layer.set(index, second);
                        layer.set(index + 1, first);
                    }
                }
            }
        }
    }

    private static Int2ObjectMap<Attachment> externalAttachments(List<ViewEdge> edges,
                                                                 Int2ObjectMap<ViewNode> nodes,
                                                                 Int2ObjectMap<PlacedNode> placed) {
        Int2ObjectMap<Attachment> result = new Int2ObjectOpenHashMap<>();
        for (ViewEdge edge : edges) {
            if (nodes.get(edge.source()).componentId() == nodes.get(edge.target()).componentId()) continue;
            result.computeIfAbsent(edge.source(), unused -> new Attachment()).add(placed.get(edge.target()));
            result.computeIfAbsent(edge.target(), unused -> new Attachment()).add(placed.get(edge.source()));
        }
        return result;
    }

    private static void improveCycle(Group group, int rootId, Int2ObjectMap<Attachment> attachments,
                                     CraftingPlanLayoutOrderScore score, Int2ObjectMap<PlacedNode> placed,
                                     boolean compact, double cellWidth, double cellHeight, Spacing spacing) {
        List<ViewNode> original = new ObjectArrayList<>(group.nodes);
        IntList moved = nodeIds(group);
        IntList guides = new IntArrayList(4);
        for (int index = 0; index < original.size(); index++) {
            Attachment attachment = attachments.get(original.get(index).id());
            if (attachment == null) continue;
            int insert = 0;
            while (insert < guides.size()) {
                ViewNode previous = original.get(guides.getInt(insert));
                int previousCount = attachments.get(previous.id()).count;
                if (attachment.count > previousCount || attachment.count == previousCount && original.get(index).id() < previous.id()) break;
                insert++;
            }
            if (insert < 4) {
                guides.add(insert, index);
                if (guides.size() > 4) guides.removeInt(4);
            }
        }
        IntSet tried = new IntOpenHashSet();
        for (int guide : guides) {
            Attachment attachment = attachments.get(original.get(guide).id());
            int desired = closestSlot(group, attachment.x / attachment.count, attachment.y / attachment.count,
                    cellWidth, cellHeight, spacing);
            for (int direction : new int[] { 1, -1 }) {
                int shift = Math.floorMod(desired - direction * guide, original.size());
                int key = shift * 2 + (direction == 1 ? 0 : 1);
                if (!tried.add(key)) continue;
                List<ViewNode> candidate = new ObjectArrayList<>(original);
                for (int index = 0; index < original.size(); index++) {
                    candidate.set(Math.floorMod(shift + direction * index, original.size()), original.get(index));
                }
                if (!rootOnLeft(group, candidate, rootId)) continue;
                List<ViewNode> previous = new ObjectArrayList<>(group.nodes);
                score.improve(moved, () -> {
                    replaceOrder(group, candidate);
                    placeNodes(group, compact, cellWidth, cellHeight, spacing, placed);
                }, () -> {
                    replaceOrder(group, previous);
                    placeNodes(group, compact, cellWidth, cellHeight, spacing, placed);
                });
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int step = 0; step + 1 < group.nodes.size(); step++) {
                int first = pass == 0 ? step : group.nodes.size() - step - 2;
                int second = first + 1;
                ViewNode a = group.nodes.get(first);
                ViewNode b = group.nodes.get(second);
                if (a.id() == rootId && group.slots.get(second).row() != 0 || b.id() == rootId && group.slots.get(first).row() != 0) continue;
                IntList pair = IntArrayList.of(a.id(), b.id());
                score.improve(pair, () -> {
                    group.nodes.set(first, b);
                    group.nodes.set(second, a);
                    placeNode(group, first, compact, cellWidth, cellHeight, spacing, placed);
                    placeNode(group, second, compact, cellWidth, cellHeight, spacing, placed);
                }, () -> {
                    group.nodes.set(first, a);
                    group.nodes.set(second, b);
                    placeNode(group, first, compact, cellWidth, cellHeight, spacing, placed);
                    placeNode(group, second, compact, cellWidth, cellHeight, spacing, placed);
                });
            }
        }
    }

    private static int closestSlot(Group group, double x, double y, double cellWidth, double cellHeight, Spacing spacing) {
        int best = 0;
        double distance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < group.slots.size(); index++) {
            Slot slot = group.slots.get(index);
            double centerX = group.depth + spacing.componentPadding() + slot.row() * (cellHeight + spacing.cellGap()) + cellHeight / 2;
            double centerY = group.cross + spacing.componentPadding() + slot.column() * (cellWidth + spacing.cellGap()) + cellWidth / 2;
            double candidate = Math.abs(centerX - x) + Math.abs(centerY - y);
            if (candidate < distance) {
                best = index;
                distance = candidate;
            }
        }
        return best;
    }

    private static boolean rootOnLeft(Group group, List<ViewNode> order, int rootId) {
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index).id() == rootId) return group.slots.get(index).row() == 0;
        }
        return true;
    }

    private static void replaceOrder(Group group, List<ViewNode> nodes) {
        for (int index = 0; index < nodes.size(); index++) group.nodes.set(index, nodes.get(index));
    }

    private static IntList nodeIds(Group group) {
        IntList ids = new IntArrayList(group.nodes.size());
        for (ViewNode node : group.nodes) ids.add(node.id());
        return ids;
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

    private record Slot(int row, int column) {}

    private record ChannelEvent(double coordinate, int delta) {}

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
        private double cross;
        private double depth;

        private Group(int id) {
            this.id = id;
        }
    }

    private static final class Attachment {

        private int count;
        private double x;
        private double y;

        private void add(PlacedNode node) {
            count++;
            x += node.x() + node.width() / 2;
            y += node.y() + node.height() / 2;
        }
    }
}
