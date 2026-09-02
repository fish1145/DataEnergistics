package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewEdge;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewNode;

/** Deterministic SCC-DAG layering with barycenter sweeps and orthogonal, obstacle-free routing bands. */
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
        Map<Integer, Group> groups = new TreeMap<>();
        Map<Integer, ViewNode> nodeById = new TreeMap<>();
        for (ViewNode node : graph.nodes()) {
            nodeById.put(node.id(), node);
            groups.computeIfAbsent(node.componentId(), Group::new).nodes.add(node);
        }
        int rootComponent = nodeById.get(graph.rootId()).componentId();
        for (Group group : groups.values()) {
            group.nodes.sort(Comparator.comparingInt((ViewNode node) -> node.id() == graph.rootId() ? 0 : 1)
                    .thenComparingInt(ViewNode::id));
            group.columns = (int) Math.ceil(Math.sqrt(group.nodes.size()));
            int rows = (group.nodes.size() + group.columns - 1) / group.columns;
            group.width = 2 * PADDING + group.columns * cellWidth + (group.columns - 1) * CELL_GAP;
            group.height = 2 * PADDING + rows * cellHeight + (rows - 1) * CELL_GAP;
        }
        for (ViewEdge edge : graph.edges()) {
            int source = nodeById.get(edge.source()).componentId();
            int target = nodeById.get(edge.target()).componentId();
            // Co-product display attachments may expose another process that consumes the requested target.
            // Keep the target at the top; those incoming edges remain visible and use the exterior back-edge lanes.
            if (source != target && target != rootComponent) {
                groups.get(source).children.add(target);
                groups.get(target).parents.add(source);
            }
        }
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (Group group : groups.values()) {
            group.remainingParents = group.parents.size();
            if (group.parents.isEmpty()) {
                ready.add(group.id);
            }
        }
        int processed = 0;
        while (!ready.isEmpty()) {
            Group source = groups.get(ready.remove());
            processed++;
            for (int targetId : source.children) {
                Group target = groups.get(targetId);
                target.rank = Math.max(target.rank, source.rank + 1);
                if (--target.remainingParents == 0) {
                    ready.add(targetId);
                }
            }
        }
        if (processed != groups.size()) {
            throw new IllegalArgumentException("View components must form an acyclic condensation graph");
        }
        Map<Integer, List<Group>> layers = new TreeMap<>();
        for (Group group : groups.values()) {
            layers.computeIfAbsent(group.rank, unused -> new ArrayList<>()).add(group);
        }
        for (int sweep = 0; sweep < 4; sweep++) {
            List<Integer> ranks = new ArrayList<>(layers.keySet());
            Map<Integer, Double> positions = positions(layers);
            if (sweep % 2 != 0) {
                ranks = ranks.reversed();
            }
            for (int rank : ranks) {
                boolean downward = sweep % 2 == 0;
                Map<Integer, Double> centers = new HashMap<>();
                for (Group group : layers.get(rank)) {
                    centers.put(group.id, barycenter(group, positions, downward));
                }
                layers.get(rank).sort(Comparator
                        .comparingDouble((Group group) -> centers.get(group.id))
                        .thenComparingInt(group -> group.id));
                double nextX = 0;
                for (Group group : layers.get(rank)) {
                    positions.put(group.id, nextX + group.width / 2);
                    nextX += group.width + GROUP_GAP;
                }
            }
        }
        double totalWidth = layers.values().stream().mapToDouble(CraftingPlanGraphLayout::rowWidth).max().orElseThrow();
        Map<Integer, Double> rankBottom = new HashMap<>();
        double y = PADDING;
        for (var layer : layers.entrySet()) {
            double x = PADDING + (totalWidth - rowWidth(layer.getValue())) / 2;
            double height = layer.getValue().stream().mapToDouble(group -> group.height).max().orElseThrow();
            for (Group group : layer.getValue()) {
                group.x = x;
                group.y = y;
                x += group.width + GROUP_GAP;
            }
            rankBottom.put(layer.getKey(), y + height);
            y += height + LAYER_GAP;
        }
        Map<Integer, PlacedNode> placed = new TreeMap<>();
        Map<Integer, Escape> escapes = new HashMap<>();
        for (Group group : groups.values()) {
            for (int index = 0; index < group.nodes.size(); index++) {
                ViewNode node = group.nodes.get(index);
                int row = index / group.columns;
                int column = index % group.columns;
                double width = node.sourceNode() instanceof Process ? cellWidth : compact ? 76 : 80;
                double height = node.sourceNode() instanceof Process ? compact ? 38 : 40
                        : node.embeddedProcessId() != null ? cellHeight : compact ? 30 : 32;
                double nodeX = group.x + PADDING + column * (cellWidth + CELL_GAP) + (cellWidth - width) / 2;
                double nodeY = group.y + PADDING + row * (cellHeight + CELL_GAP);
                placed.put(node.id(), new PlacedNode(node, nodeX, nodeY, width, height));
                escapes.put(node.id(), new Escape(nodeY - CELL_GAP / 2, nodeY + cellHeight + CELL_GAP / 2));
            }
        }
        List<RoutedEdge> routed = new ArrayList<>();
        int externalChannel = 0;
        int bandChannel = 0;
        double maxX = totalWidth + 2 * PADDING;
        for (ViewEdge edge : graph.edges()) {
            PlacedNode source = placed.get(edge.source());
            PlacedNode target = placed.get(edge.target());
            Group sourceGroup = groups.get(source.viewNode().componentId());
            Group targetGroup = groups.get(target.viewNode().componentId());
            double sourceX = source.x() + source.width() / 2;
            double targetX = target.x() + target.width() / 2;
            List<Point> points = new ArrayList<>();
            points.add(new Point(sourceX, source.y() + source.height()));
            if (!edge.cyclic() && targetGroup.rank == sourceGroup.rank + 1
                    && sourceGroup.nodes.size() == 1 && targetGroup.nodes.size() == 1) {
                double bandY = rankBottom.get(sourceGroup.rank) + 12 + bandChannel++ % 9 * 3;
                points.add(new Point(sourceX, bandY));
                points.add(new Point(targetX, bandY));
            } else {
                // A common cell pitch aligns these horizontal escape lanes across all SCC rectangles in a layer.
                double channelX = totalWidth + 2 * PADDING + 20 + externalChannel++ % 32 * 6;
                double sourceY = escapes.get(source.id()).below();
                double targetY = escapes.get(target.id()).above();
                points.add(new Point(sourceX, sourceY));
                points.add(new Point(channelX, sourceY));
                points.add(new Point(channelX, targetY));
                points.add(new Point(targetX, targetY));
                maxX = Math.max(maxX, channelX);
            }
            points.add(new Point(targetX, target.y()));
            routed.add(new RoutedEdge(edge.source(), edge.target(), points, edge.cyclic(), edge.originalEdgeIds()));
        }
        return new Layout(new ArrayList<>(placed.values()), routed,
                new Bounds(0, 0, maxX + PADDING, y - LAYER_GAP + PADDING));
    }

    private static Map<Integer, Double> positions(Map<Integer, List<Group>> layers) {
        Map<Integer, Double> result = new HashMap<>();
        for (List<Group> layer : layers.values()) {
            double x = 0;
            for (Group group : layer) {
                result.put(group.id, x + group.width / 2);
                x += group.width + GROUP_GAP;
            }
        }
        return result;
    }

    private static double barycenter(Group group, Map<Integer, Double> positions, boolean downward) {
        TreeSet<Integer> neighbors = downward ? group.parents : group.children;
        return neighbors.stream().mapToDouble(positions::get).average().orElse(positions.get(group.id));
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

    public record RoutedEdge(int source, int target, List<Point> points, boolean cyclic,
            List<Integer> originalEdgeIds) {

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

    private record Escape(double above, double below) {}

    private static final class Group {

        private final int id;
        private final List<ViewNode> nodes = new ArrayList<>();
        private final TreeSet<Integer> parents = new TreeSet<>();
        private final TreeSet<Integer> children = new TreeSet<>();
        private int remainingParents;
        private int rank;
        private int columns;
        private double width;
        private double height;
        private double x;
        private double y;

        private Group(int id) {
            this.id = id;
        }
    }
}
