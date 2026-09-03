package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Bounds;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Side;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Spacing;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Path;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGroup.Style;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.OrthogonalRouteSearch.Choice;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.OrthogonalRoutingGraph.Port;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewEdge;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewGraph;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Ports follow final card positions; only chosen finite paths occupy the shared routing scene. */
final class CraftingPlanEdgeRouter {

    private CraftingPlanEdgeRouter() {}

    static Layout route(ViewGraph graph, List<PlacedNode> nodes, Spacing spacing) {
        Int2ObjectMap<PlacedNode> nodeById = new Int2ObjectOpenHashMap<>();
        for (PlacedNode node : nodes) nodeById.put(node.id(), node);
        Object2ObjectMap<PortKey, PortIntent> intents = new Object2ObjectLinkedOpenHashMap<>();
        List<Request> requests = requests(graph, nodeById, intents);
        Int2IntMap degrees = new Int2IntOpenHashMap();
        List<Port> ports = ports(intents, degrees);
        if (requests.isEmpty()) return assemble(nodes, List.of(), spacing.boundaryPadding());
        var scene = new OrthogonalRoutingGraph(nodes, ports, degrees);
        var reservations = new OrthogonalSegmentReservations(scene.x, scene.y);
        var search = new OrthogonalRouteSearch(scene, reservations, spacing.cellGap());
        List<Request> localFirst = new ObjectArrayList<>(requests);
        localFirst.sort(Comparator.comparingDouble(Request::distance).thenComparingInt(Request::id));
        Choice[] choices = new Choice[requests.size()];
        for (Request request : localFirst) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Choice choice = search.route(request.source().ports, request.target().ports, request.group(), null);
            choices[request.id()] = choice;
            reservations.reserve(choice.points(), request.group());
        }
        for (int pass = 0; pass < 2; pass++) {
            boolean changed = false;
            for (Request request : localFirst) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                Choice previous = choices[request.id()];
                // Recompute against current neighbours: later short routes may have introduced a crossing.
                reservations.release(previous.points(), request.group());
                var previousMetrics = reservations.measure(previous.points(), request.group());
                if (previousMetrics == null) throw new IllegalStateException("Registered crafting-tree route lost its corridor");
                double previousLimit = previous.baselineLength() + Math.min(previous.baselineLength() * 0.25, 2 * spacing.cellGap());
                if (previousMetrics.crossings() == 0 && previousMetrics.length() <= previousLimit) {
                    reservations.reserve(previous.points(), request.group());
                    continue;
                }
                Choice candidate = search.route(request.source().ports, request.target().ports, request.group(),
                        new Choice(previous.points(), previousMetrics, previous.baselineLength()));
                double limit = candidate.baselineLength() + Math.min(candidate.baselineLength() * 0.25, 2 * spacing.cellGap());
                if (previousMetrics.length() > limit + OrthogonalSegmentReservations.EPSILON || OrthogonalRouteSearch.compare(candidate.metrics(), previousMetrics) < 0) {
                    choices[request.id()] = candidate;
                    changed = true;
                }
                reservations.reserve(choices[request.id()].points(), request.group());
            }
            if (!changed) break;
        }
        List<Path> paths = new ObjectArrayList<>(requests.size());
        for (Request request : requests) {
            paths.add(new Path(request.edge().source(), request.edge().target(), choices[request.id()].points(),
                    request.edge().cyclic(), request.originals(), request.group()));
        }
        return assemble(nodes, paths, spacing.boundaryPadding());
    }

    private static List<Request> requests(ViewGraph graph, Int2ObjectMap<PlacedNode> nodes,
                                          Object2ObjectMap<PortKey, PortIntent> intents) {
        var styles = CraftingPlanRouteGroup.indexStyles(graph.source());
        List<Request> result = new ObjectArrayList<>();
        for (ViewEdge edge : graph.edges()) {
            Object2ObjectMap<Style, IntList> split = new Object2ObjectLinkedOpenHashMap<>();
            for (int original : edge.originalEdgeIds()) {
                split.computeIfAbsent(styles.get(original), unused -> new IntArrayList()).add(original);
            }
            for (var entry : split.object2ObjectEntrySet()) {
                Style style = entry.getKey();
                // Layout edges express demand; material arrows travel in the opposite direction.
                int destination = style.materialFlow() ? edge.source() : edge.target();
                var group = new CraftingPlanRouteGroup(style, destination);
                PlacedNode source = nodes.get(edge.source());
                PlacedNode target = nodes.get(edge.target());
                PortIntent sourceIntent = intent(intents, source, target, true, group);
                PortIntent targetIntent = intent(intents, target, source, false, group);
                double distance = Math.abs(source.x() + source.width() / 2 - target.x() - target.width() / 2) + Math.abs(source.y() + source.height() / 2 - target.y() - target.height() / 2);
                result.add(new Request(result.size(), edge, IntLists.unmodifiable(entry.getValue()), group, sourceIntent, targetIntent, distance));
            }
        }
        return result;
    }

    private static PortIntent intent(Object2ObjectMap<PortKey, PortIntent> intents, PlacedNode node, PlacedNode other,
                                     boolean source, CraftingPlanRouteGroup group) {
        var key = new PortKey(node.id(), source, group);
        PortIntent intent = intents.computeIfAbsent(key, unused -> new PortIntent(node, intents.size()));
        intent.x += other.x() + other.width() / 2;
        intent.y += other.y() + other.height() / 2;
        intent.count++;
        return intent;
    }

    private static List<Port> ports(Object2ObjectMap<PortKey, PortIntent> intents, Int2IntMap degrees) {
        Int2ObjectMap<List<PortIntent>> byNode = new Int2ObjectOpenHashMap<>();
        for (PortIntent intent : intents.values()) {
            byNode.computeIfAbsent(intent.node.id(), unused -> new ObjectArrayList<>()).add(intent);
        }
        List<Port> result = new ObjectArrayList<>(intents.size() * 4);
        for (var entry : byNode.int2ObjectEntrySet()) {
            List<PortIntent> nodePorts = entry.getValue();
            degrees.put(entry.getIntKey(), nodePorts.size());
            for (Side side : OrthogonalRoutingGraph.sides()) {
                boolean horizontal = side == Side.TOP || side == Side.BOTTOM;
                nodePorts.sort(Comparator.comparingDouble((PortIntent intent) -> (horizontal ? intent.x : intent.y) / intent.count)
                        .thenComparingInt(intent -> intent.ordinal));
                for (int index = 0; index < nodePorts.size(); index++) {
                    PortIntent intent = nodePorts.get(index);
                    Port port = OrthogonalRoutingGraph.port(intent.node, side, (index + 1.0) / (nodePorts.size() + 1.0));
                    intent.ports.add(port);
                    result.add(port);
                }
            }
        }
        return result;
    }

    private static Layout assemble(List<PlacedNode> nodes, List<Path> paths, double padding) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (PlacedNode node : nodes) {
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.x() + node.width());
            maxY = Math.max(maxY, node.y() + node.height());
        }
        for (Path path : paths) {
            for (Point point : path.points()) {
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }
        }
        double dx = padding - minX;
        double dy = padding - minY;
        List<PlacedNode> shiftedNodes = new ObjectArrayList<>(nodes.size());
        for (PlacedNode node : nodes) {
            shiftedNodes.add(new PlacedNode(node.viewNode(), node.x() + dx, node.y() + dy, node.width(), node.height()));
        }
        List<Path> shiftedPaths = new ObjectArrayList<>(paths.size());
        for (Path path : paths) {
            List<Point> points = new ObjectArrayList<>(path.points().size());
            for (Point point : path.points()) points.add(new Point(point.x() + dx, point.y() + dy));
            shiftedPaths.add(new Path(path.source(), path.target(), points, path.cyclic(), path.originalEdgeIds(), path.group()));
        }
        return CraftingPlanRouteGeometry.assemble(shiftedNodes, shiftedPaths,
                new Bounds(0, 0, maxX - minX + 2 * padding, maxY - minY + 2 * padding));
    }

    private record PortKey(int node, boolean source, CraftingPlanRouteGroup group) {}

    private record Request(int id, ViewEdge edge, IntList originals, CraftingPlanRouteGroup group,
                           PortIntent source, PortIntent target, double distance) {}

    private static final class PortIntent {

        private final PlacedNode node;
        private final int ordinal;
        private final List<Port> ports = new ObjectArrayList<>(4);
        private double x;
        private double y;
        private int count;

        private PortIntent(PlacedNode node, int ordinal) {
            this.node = node;
            this.ordinal = ordinal;
        }
    }
}
