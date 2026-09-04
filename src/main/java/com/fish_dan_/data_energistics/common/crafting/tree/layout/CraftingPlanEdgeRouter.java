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
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;

/** Ports follow final card positions; only chosen finite paths occupy the shared routing scene. */
final class CraftingPlanEdgeRouter {

    private static final int MAXIMUM_REFINEMENTS = 32;

    private CraftingPlanEdgeRouter() {}

    static Layout route(ViewGraph graph, List<PlacedNode> nodes, Spacing spacing) {
        Int2ObjectMap<PlacedNode> nodeById = new Int2ObjectOpenHashMap<>();
        for (PlacedNode node : nodes) nodeById.put(node.id(), node);
        Object2ObjectMap<PortKey, PortIntent> intents = new Object2ObjectLinkedOpenHashMap<>();
        List<Request> requests = requests(graph, nodeById, intents);
        List<Port> ports = ports(intents);
        if (requests.isEmpty()) return assemble(nodes, List.of(), spacing.boundaryPadding());
        var scene = new OrthogonalRoutingGraph(nodes, ports);
        var reservations = new OrthogonalSegmentReservations(scene.x, scene.y);
        var search = new OrthogonalRouteSearch(scene, reservations, spacing.cellGap());
        Int2IntMap depths = routingDepths(graph);
        double[] channelLanes = channelLanes(requests, depths, scene.x);
        List<Request> ordered = new ObjectArrayList<>(requests);
        ordered.sort(Comparator.comparingInt((Request request) -> depths.get(request.edge().source()))
                .thenComparingInt(request -> depths.get(request.edge().target()))
                .thenComparingDouble(Request::distance).thenComparingInt(Request::id));
        Choice[] choices = new Choice[requests.size()];
        for (Request request : ordered) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Choice choice = channelChoice(search, request, channelLanes[request.id()]);
            if (choice == null) {
                choice = search.route(request.source().ports, request.target().ports, request.group(), null, false);
            }
            choices[request.id()] = choice;
            if (choice.reserved()) reservations.reserve(choice.points(), request.group());
        }
        int remainingRefinements = MAXIMUM_REFINEMENTS;
        for (int pass = 0; pass < 2 && remainingRefinements > 0; pass++) {
            boolean changed = false;
            List<Request> refinements = new ObjectArrayList<>();
            for (Request request : ordered) {
                if (choices[request.id()].reserved() && choices[request.id()].metrics().crossings() > 0) refinements.add(request);
            }
            refinements.sort(Comparator.comparingInt((Request request) -> choices[request.id()].metrics().crossings()).reversed()
                    .thenComparingDouble(Request::distance).thenComparingInt(Request::id));
            int count = Math.min(refinements.size(), remainingRefinements);
            for (int index = 0; index < count; index++) {
                Request request = refinements.get(index);
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
                        new Choice(previous.points(), previousMetrics, previous.baselineLength(), true), true);
                double limit = candidate.baselineLength() + Math.min(candidate.baselineLength() * 0.25, 2 * spacing.cellGap());
                if (previousMetrics.length() > limit + OrthogonalSegmentReservations.EPSILON || OrthogonalRouteSearch.compare(candidate.metrics(), previousMetrics) < 0) {
                    choices[request.id()] = candidate;
                    changed = true;
                }
                if (choices[request.id()].reserved()) reservations.reserve(choices[request.id()].points(), request.group());
            }
            remainingRefinements -= count;
            if (!changed) break;
        }
        List<Path> paths = new ObjectArrayList<>(requests.size());
        for (Request request : requests) {
            paths.add(new Path(request.edge().source(), request.edge().target(), choices[request.id()].points(),
                    request.edge().cyclic(), request.originals(), request.group()));
        }
        return assemble(nodes, paths, spacing.boundaryPadding());
    }

    private static Int2IntMap routingDepths(ViewGraph graph) {
        Int2ObjectMap<IntList> outgoing = new Int2ObjectOpenHashMap<>();
        for (ViewEdge edge : graph.edges()) {
            outgoing.computeIfAbsent(edge.source(), unused -> new IntArrayList()).add(edge.target());
        }
        Int2IntMap depths = new Int2IntOpenHashMap();
        depths.defaultReturnValue(Integer.MAX_VALUE);
        depths.put(graph.rootId(), 0);
        var pending = new IntArrayFIFOQueue();
        pending.enqueue(graph.rootId());
        while (!pending.isEmpty()) {
            int source = pending.dequeueInt();
            IntList targets = outgoing.get(source);
            if (targets == null) continue;
            int depth = depths.get(source) + 1;
            for (int target : targets) {
                if (depth >= depths.get(target)) continue;
                depths.put(target, depth);
                pending.enqueue(target);
            }
        }
        return depths;
    }

    private static @Nullable Choice channelChoice(OrthogonalRouteSearch search, Request request, double lane) {
        if (Double.isNaN(lane)) return null;
        PlacedNode sourceNode = request.source().node;
        PlacedNode targetNode = request.target().node;
        boolean forward = sourceNode.x() < targetNode.x();
        Port source = request.source().port(forward ? Side.RIGHT : Side.LEFT);
        Port target = request.target().port(forward ? Side.LEFT : Side.RIGHT);
        return search.fixedChannel(source, target, lane, request.group());
    }

    private static double[] channelLanes(List<Request> requests, Int2IntMap depths, OrthogonalRoutingAxis xAxis) {
        double[] lanes = new double[requests.size()];
        Arrays.fill(lanes, Double.NaN);
        Int2ObjectMap<List<Request>> byBoundary = new Int2ObjectOpenHashMap<>();
        for (Request request : requests) {
            int sourceDepth = depths.get(request.edge().source());
            if (sourceDepth == Integer.MAX_VALUE || depths.get(request.edge().target()) != sourceDepth + 1) continue;
            if (request.source().node.x() == request.target().node.x()) continue;
            byBoundary.computeIfAbsent(sourceDepth, unused -> new ObjectArrayList<>()).add(request);
        }
        int[] trackByRequest = new int[requests.size()];
        for (List<Request> boundary : byBoundary.values()) {
            boundary.sort(Comparator.comparingDouble(CraftingPlanEdgeRouter::intervalStart)
                    .thenComparingDouble(CraftingPlanEdgeRouter::intervalEnd).thenComparingInt(Request::id));
            var active = new PriorityQueue<ActiveTrack>(Comparator.comparingDouble(ActiveTrack::end)
                    .thenComparingInt(ActiveTrack::track));
            var free = new IntHeapPriorityQueue();
            int trackCount = 0;
            for (Request request : boundary) {
                double start = intervalStart(request);
                while (!active.isEmpty() && active.peek().end() <= start) free.enqueue(active.remove().track());
                int track = free.isEmpty() ? trackCount++ : free.dequeueInt();
                trackByRequest[request.id()] = track;
                active.add(new ActiveTrack(track, intervalEnd(request)));
            }
            for (Request request : boundary) {
                PlacedNode source = request.source().node;
                PlacedNode target = request.target().node;
                double low = Math.min(source.x() + source.width(), target.x() + target.width()) + OrthogonalRoutingGraph.CLEARANCE;
                double high = Math.max(source.x(), target.x()) - OrthogonalRoutingGraph.CLEARANCE;
                if (low >= high) continue;
                int first = xAxis.ceiling(low + OrthogonalSegmentReservations.EPSILON);
                int last = xAxis.floor(high - OrthogonalSegmentReservations.EPSILON);
                int available = last - first + 1;
                if (available <= 0 || trackByRequest[request.id()] >= available) continue;
                int index = trackCount <= available ? first + (int) ((long) (trackByRequest[request.id()] + 1) * available / (trackCount + 1)) : first + trackByRequest[request.id()];
                lanes[request.id()] = xAxis.value(Math.min(last, index));
            }
        }
        return lanes;
    }

    private static double intervalStart(Request request) {
        return Math.min(request.source().node.y() + request.source().node.height() / 2,
                request.target().node.y() + request.target().node.height() / 2);
    }

    private static double intervalEnd(Request request) {
        return Math.max(request.source().node.y() + request.source().node.height() / 2,
                request.target().node.y() + request.target().node.height() / 2);
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

    private static List<Port> ports(Object2ObjectMap<PortKey, PortIntent> intents) {
        Int2ObjectMap<List<PortIntent>> byNode = new Int2ObjectOpenHashMap<>();
        for (PortIntent intent : intents.values()) {
            byNode.computeIfAbsent(intent.node.id(), unused -> new ObjectArrayList<>()).add(intent);
        }
        List<Port> result = new ObjectArrayList<>(intents.size() * 4);
        for (var entry : byNode.int2ObjectEntrySet()) {
            List<PortIntent> nodePorts = entry.getValue();
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

    private record ActiveTrack(int track, double end) {}

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

        private Port port(Side side) {
            return ports.get(side.ordinal());
        }
    }
}
