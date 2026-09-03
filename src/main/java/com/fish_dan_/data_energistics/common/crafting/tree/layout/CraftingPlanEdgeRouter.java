package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Bounds;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Side;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Path;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewEdge;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewGraph;

import it.unimi.dsi.fastutil.doubles.Double2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/** Orthogonal routing within components, through layer gaps, and across interval-packed inter-layer tracks. */
final class CraftingPlanEdgeRouter {

    private static final double GAP = 4;
    private static final double PADDING = 16;
    private static final double EPSILON = OrthogonalSegmentReservations.EPSILON;

    private final Int2ObjectMap<RoutingComponent> components = new Int2ObjectAVLTreeMap<>();
    private final Int2ObjectMap<RoutingComponent> componentByNode = new Int2ObjectOpenHashMap<>();
    private final Int2IntOpenHashMap degree = new Int2IntOpenHashMap();
    private final List<Layer> layers = new ObjectArrayList<>();
    private final List<EdgePorts> edges = new ObjectArrayList<>();
    private final List<ExternalRoute> external = new ObjectArrayList<>();
    private final Int2ObjectMap<Band> bands = new Int2ObjectAVLTreeMap<>();

    private CraftingPlanEdgeRouter(ViewGraph graph, List<List<Component>> rows) {
        for (int rank = 0; rank < rows.size(); rank++) {
            List<RoutingComponent> row = new ObjectArrayList<>();
            for (Component input : rows.get(rank)) {
                RoutingComponent component = new RoutingComponent(input, rank);
                components.put(input.id(), component);
                input.nodes().forEach(node -> componentByNode.put(node.id(), component));
                row.add(component);
            }
            layers.add(new Layer(row));
            bands.put(rank, new Band());
        }
        var groups = CraftingPlanRouteGroup.index(graph.source());
        Object2IntMap<PortKey> ports = new Object2IntLinkedOpenHashMap<>();
        ToIntFunction<PortKey> nextPort = key -> degree.addTo(key.node(), 1);
        for (ViewEdge edge : graph.edges()) {
            Object2ObjectMap<CraftingPlanRouteGroup, IntList> split = new Object2ObjectLinkedOpenHashMap<>();
            for (int original : edge.originalEdgeIds()) {
                split.computeIfAbsent(groups.get(original), unused -> new IntArrayList()).add(original);
            }
            for (var entry : split.object2ObjectEntrySet()) {
                CraftingPlanRouteGroup group = entry.getKey();
                int sourcePort = ports.computeIfAbsent(new PortKey(edge.source(), true, group), nextPort);
                int targetPort = ports.computeIfAbsent(new PortKey(edge.target(), false, group), nextPort);
                edges.add(new EdgePorts(edges.size(), edge, sourcePort, targetPort, group, IntLists.unmodifiable(entry.getValue())));
            }
        }
    }

    static Layout route(ViewGraph graph, List<List<Component>> rows) {
        return new CraftingPlanEdgeRouter(graph, rows).route();
    }

    private Layout route() {
        for (EdgePorts edge : edges) {
            if (edge.view().cyclic()) {
                RoutingComponent component = componentByNode.get(edge.view().source());
                List<Point> points = cycleRoute(component, edge);
                component.occupied.reserve(points, edge.group(), false);
                component.include(points);
                component.internal.add(new LocalRoute(edge, points));
            }
        }
        for (EdgePorts edge : edges) {
            if (!edge.view().cyclic()) {
                RoutingComponent source = componentByNode.get(edge.view().source());
                RoutingComponent target = componentByNode.get(edge.view().target());
                boolean sourceBottom = source.rank <= target.rank;
                boolean targetBottom = source.rank >= target.rank;
                Terminal from = source.terminals.computeIfAbsent(new TerminalKey(edge.view().source(), true, sourceBottom, edge.group()),
                        unused -> terminal(source, edge, true, sourceBottom));
                Terminal to = target.terminals.computeIfAbsent(new TerminalKey(edge.view().target(), false, targetBottom, edge.group()),
                        unused -> terminal(target, edge, false, targetBottom));
                external.add(new ExternalRoute(edge, from, to));
            }
        }
        // Local routing finishes before packing: its occupied envelope belongs exclusively to this component.
        for (RoutingComponent component : components.values()) {
            component.left -= GAP;
            component.top -= GAP;
            component.right += GAP;
            component.bottom += GAP;
        }
        placeColumns();
        allocatePassages();
        placeColumns();
        for (ExternalRoute route : external) {
            connectBands(route);
        }
        bands.values().forEach(Band::allocate);
        double y = PADDING;
        if (bands.containsKey(-1)) {
            bands.get(-1).y = y;
            y += bands.get(-1).height;
        }
        for (int rank = 0; rank < layers.size(); rank++) {
            Layer layer = layers.get(rank);
            for (RoutingComponent component : layer.groups) {
                component.y = y;
            }
            y += layer.groups.stream().mapToDouble(RoutingComponent::height).max().orElseThrow();
            Band band = bands.get(rank);
            band.y = y;
            y += band.height;
        }
        return assemble();
    }

    private List<Point> cycleRoute(RoutingComponent component, EdgePorts edge) {
        PlacedNode source = component.nodes.get(edge.view().source());
        PlacedNode target = component.nodes.get(edge.view().target());
        if (source.id() != target.id()) {
            List<Point> horizontal = directRoute(component, source, target, edge, true);
            List<Point> vertical = directRoute(component, source, target, edge, false);
            if (!horizontal.isEmpty() || !vertical.isEmpty()) {
                return horizontal.isEmpty() || (!vertical.isEmpty() && better(component, vertical, horizontal, edge.group(), false)) ? vertical : horizontal;
            }
        }
        Side fromSide = component.input.outward().get(source.id());
        Side toSide = component.input.outward().get(target.id());
        Point from = port(source, fromSide, edge.sourcePort());
        Point to = port(target, toSide, edge.targetPort());
        int attempts = component.occupied.segmentCount() + 2;
        List<Point> best = List.of();
        double bestAdded = Double.POSITIVE_INFINITY;
        double bestLength = Double.POSITIVE_INFINITY;
        for (int lane = 0; lane < attempts; lane++) {
            List<Point> candidate;
            if (source.id() == target.id()) {
                double distance = 8 + lane * GAP;
                Point fromLane = outward(from, fromSide, distance);
                Point toLane = outward(to, toSide, distance);
                candidate = List.of(from, fromLane, toLane, to);
            } else {
                candidate = boundaryRoute(component.boundary(lane), from, fromSide, to, toSide);
            }
            if (!clearOfNodes(candidate, component.nodes.values())) continue;
            double added = component.occupied.addedLength(candidate, edge.group(), false);
            double candidateLength = length(candidate);
            if (added < bestAdded || (Math.abs(added - bestAdded) < EPSILON && candidateLength < bestLength)) {
                best = candidate;
                bestAdded = added;
                bestLength = candidateLength;
                if (added == 0) break;
            }
        }
        if (!best.isEmpty()) return best;
        throw new IllegalStateException("Cannot reserve a component route for " + edge.view().source() + " -> " + edge.view().target());
    }

    private List<Point> directRoute(RoutingComponent component, PlacedNode source, PlacedNode target,
                                    EdgePorts edge, boolean horizontal) {
        double sourceStart = horizontal ? source.x() : source.y();
        double targetStart = horizontal ? target.x() : target.y();
        double sourceEnd = sourceStart + (horizontal ? source.width() : source.height());
        double targetEnd = targetStart + (horizontal ? target.width() : target.height());
        if (sourceEnd >= targetStart && targetEnd >= sourceStart) {
            return List.of();
        }
        boolean forward = sourceStart < targetStart;
        Side sourceSide = horizontal ? forward ? Side.RIGHT : Side.LEFT : forward ? Side.BOTTOM : Side.TOP;
        Side targetSide = horizontal ? forward ? Side.LEFT : Side.RIGHT : forward ? Side.TOP : Side.BOTTOM;
        Point from = port(source, sourceSide, edge.sourcePort());
        Point to = port(target, targetSide, edge.targetPort());
        double start = (forward ? sourceEnd : targetEnd) + GAP;
        double end = (forward ? targetStart : sourceStart) - GAP;
        double center = (start + end) / 2;
        int steps = Math.min((int) Math.ceil((end - start) / GAP) + 2, component.occupied.segmentCount() * 2 + 3);
        List<Point> best = List.of();
        double bestAdded = Double.POSITIVE_INFINITY;
        double bestLength = Double.POSITIVE_INFINITY;
        for (int step = 0; step < steps; step++) {
            double lane = center + alternatingOffset(step);
            if (lane < start || lane > end) {
                continue;
            }
            List<Point> candidate = horizontal ? List.of(from, new Point(lane, from.y()), new Point(lane, to.y()), to) : List.of(from, new Point(from.x(), lane), new Point(to.x(), lane), to);
            if (!clearOfNodes(candidate, component.nodes.values())) continue;
            double added = component.occupied.addedLength(candidate, edge.group(), false);
            double candidateLength = length(candidate);
            if (added < bestAdded || (Math.abs(added - bestAdded) < EPSILON && candidateLength < bestLength)) {
                best = candidate;
                bestAdded = added;
                bestLength = candidateLength;
                if (added == 0) break;
            }
        }
        return best;
    }

    private static boolean better(RoutingComponent component, List<Point> candidate, List<Point> previous,
                                  CraftingPlanRouteGroup group, boolean reverse) {
        double cost = component.occupied.addedLength(candidate, group, reverse);
        double previousCost = component.occupied.addedLength(previous, group, reverse);
        if (Math.abs(cost - previousCost) > EPSILON) return cost < previousCost;
        double candidateLength = length(candidate);
        double previousLength = length(previous);
        return Math.abs(candidateLength - previousLength) > EPSILON ? candidateLength < previousLength : candidate.size() < previous.size();
    }

    private Terminal terminal(RoutingComponent component, EdgePorts edge, boolean source, boolean bottom) {
        PlacedNode node = component.nodes.get(source ? edge.view().source() : edge.view().target());
        Side exitSide = bottom ? Side.BOTTOM : Side.TOP;
        Side nodeSide = node.viewNode().cyclic() ? component.input.outward().get(node.id()) : exitSide;
        Point from = port(node, nodeSide, source ? edge.sourcePort() : edge.targetPort());
        int attempts = component.occupied.segmentCount() + 2;
        for (int lane = 0; lane < attempts; lane++) {
            Bounds boundary = component.boundary(lane);
            Point fromLane = project(from, nodeSide, boundary);
            double exitX = fromLane.x();
            if ((nodeSide == Side.TOP || nodeSide == Side.BOTTOM) && nodeSide != exitSide) {
                exitX = exitX - boundary.x() <= boundary.width() / 2 ? boundary.x() : boundary.x() + boundary.width();
            }
            Point exit = new Point(exitX, bottom ? boundary.y() + boundary.height() : boundary.y());
            List<Point> candidate = boundaryRoute(boundary, from, nodeSide, exit, exitSide);
            List<Point> withRay = new ObjectArrayList<>(candidate);
            // Reserve the eventual escape in the route's traversal direction, including target-side reversal.
            withRay.add(new Point(exitX, bottom ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY));
            if (component.occupied.available(withRay, edge.group(), !source) && clearOfNodes(candidate, component.nodes.values())) {
                component.occupied.reserve(withRay, edge.group(), !source);
                component.include(candidate);
                return new Terminal(component, candidate, bottom);
            }
        }
        throw new IllegalStateException("Cannot reserve a layer exit for node " + node.id());
    }

    private Point port(PlacedNode node, Side side, int index) {
        double fraction = (index + 1.0) / (degree.get(node.id()) + 1.0);
        double x = node.x() + 6 + fraction * (node.width() - 12);
        double y = node.y() + 6 + fraction * (node.height() - 12);
        return switch (side) {
            case TOP -> new Point(x, node.y());
            case BOTTOM -> new Point(x, node.y() + node.height());
            case LEFT -> new Point(node.x(), y);
            case RIGHT -> new Point(node.x() + node.width(), y);
        };
    }

    private void placeColumns() {
        double width = layers.stream().mapToDouble(Layer::width).max().orElseThrow();
        for (Layer layer : layers) {
            double x = PADDING + (width - layer.width()) / 2;
            for (int gap = 0; gap <= layer.groups.size(); gap++) {
                double gapWidth = layer.gapWidth(gap);
                layer.centers[gap] = x + gapWidth / 2;
                x += gapWidth;
                if (gap < layer.groups.size()) {
                    RoutingComponent component = layer.groups.get(gap);
                    component.x = x;
                    x += component.width();
                }
            }
        }
    }

    private void allocatePassages() {
        for (ExternalRoute route : external) {
            int sourceRank = route.source.component().rank;
            int targetRank = route.target.component().rank;
            int direction = Integer.compare(targetRank, sourceRank);
            LaneKey laneKey = new LaneKey(route.edge.group(), direction);
            double sourceX = route.source.x();
            double currentX = sourceX;
            double targetX = route.target.x();
            for (int rank = sourceRank + direction; direction != 0 && rank != targetRank; rank += direction) {
                Layer layer = layers.get(rank);
                int chosen = 0;
                double best = Double.POSITIVE_INFINITY;
                double preferredX = sourceX + (targetX - sourceX) * (rank - sourceRank) / (targetRank - sourceRank);
                double bestDeviation = Double.POSITIVE_INFINITY;
                for (int gap = 0; gap < layer.centers.length; gap++) {
                    double x = layer.centers[gap];
                    double cost = Math.abs(currentX - x) + Math.abs(targetX - x) +
                            (layer.lanes.get(gap).containsKey(laneKey) ? 0 : (layer.used[gap] + 2) * GAP);
                    double deviation = Math.abs(x - preferredX);
                    if (cost < best || (Math.abs(cost - best) < EPSILON && deviation < bestDeviation)) {
                        chosen = gap;
                        best = cost;
                        bestDeviation = deviation;
                    }
                }
                int gap = chosen;
                int lane = layer.lanes.get(gap).computeIfAbsent(laneKey,
                        (ToIntFunction<LaneKey>) unused -> layer.used[gap]++);
                route.passages.add(new Passage(rank, gap, lane));
                currentX = layer.centers[chosen];
            }
        }
    }

    private void connectBands(ExternalRoute route) {
        int band = route.source.component().rank - (route.source.bottom() ? 0 : 1);
        boolean downward = route.target.component().rank > route.source.component().rank;
        Anchor current = new Anchor(route.source.x(), route.source.bottom());
        for (Passage passage : route.passages) {
            Layer layer = layers.get(passage.rank());
            double x = layer.centers[passage.gap()] + (passage.lane() - (layer.used[passage.gap()] - 1) / 2.0) * GAP;
            addVisit(route, band, current, new Anchor(x, !downward));
            current = new Anchor(x, downward);
            band = passage.rank() - (downward ? 0 : 1);
        }
        addVisit(route, band, current, new Anchor(route.target.x(), route.target.bottom()));
    }

    private void addVisit(ExternalRoute route, int rank, Anchor source, Anchor target) {
        Band band = bands.computeIfAbsent(rank, unused -> new Band());
        BandVisit visit = new BandVisit(band, source, target, route.edge.group());
        band.visits.add(visit);
        route.visits.add(visit);
    }

    private Layout assemble() {
        Int2ObjectMap<PlacedNode> nodes = new Int2ObjectAVLTreeMap<>();
        Int2ObjectMap<Path> routed = new Int2ObjectAVLTreeMap<>();
        for (RoutingComponent component : components.values()) {
            for (PlacedNode node : component.nodes.values()) {
                Point location = component.global(new Point(node.x(), node.y()));
                nodes.put(node.id(), new PlacedNode(node.viewNode(), location.x(), location.y(), node.width(), node.height()));
            }
            for (LocalRoute route : component.internal) {
                List<Point> points = route.points().stream().map(component::global).toList();
                routed.put(route.edge().ordinal(), routed(route.edge(), points));
            }
        }
        for (ExternalRoute route : external) {
            List<Point> points = new ObjectArrayList<>();
            route.source.points().forEach(point -> points.add(route.source.component().global(point)));
            for (BandVisit visit : route.visits) {
                points.addAll(visit.points());
            }
            for (Point point : route.target.points().reversed()) {
                points.add(route.target.component().global(point));
            }
            routed.put(route.edge.ordinal(), routed(route.edge, points));
        }
        double minX = 0;
        double maxX = 0;
        double maxY = 0;
        for (PlacedNode node : nodes.values()) {
            maxX = Math.max(maxX, node.x() + node.width());
            maxY = Math.max(maxY, node.y() + node.height());
        }
        for (Path edge : routed.values()) {
            for (Point point : edge.points()) {
                minX = Math.min(minX, point.x());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }
        }
        double shift = minX < 0 ? PADDING - minX : 0;
        List<PlacedNode> shiftedNodes = nodes.values().stream()
                .map(node -> new PlacedNode(node.viewNode(), node.x() + shift, node.y(), node.width(), node.height())).toList();
        List<Path> shiftedEdges = routed.values().stream().map(edge -> new Path(edge.source(), edge.target(),
                shift == 0 ? edge.points() : edge.points().stream().map(point -> new Point(point.x() + shift, point.y())).toList(),
                edge.cyclic(), edge.originalEdgeIds(), edge.group())).toList();
        return CraftingPlanRouteGeometry.assemble(shiftedNodes, shiftedEdges, new Bounds(0, 0, maxX + shift + PADDING, maxY + PADDING));
    }

    private static Path routed(EdgePorts edge, List<Point> points) {
        return new Path(edge.view().source(), edge.view().target(), simplify(points), edge.view().cyclic(),
                edge.originalEdgeIds(), edge.group());
    }

    private static List<Point> simplify(List<Point> points) {
        List<Point> result = new ObjectArrayList<>();
        for (Point point : points) {
            if (!result.isEmpty() && point.equals(result.getLast())) {
                continue;
            }
            if (result.size() >= 2) {
                Point before = result.get(result.size() - 2);
                Point last = result.getLast();
                if (((before.x() == last.x() && last.x() == point.x()) || (before.y() == last.y() && last.y() == point.y())) &&
                        (last.x() - before.x()) * (point.x() - last.x()) + (last.y() - before.y()) * (point.y() - last.y()) > 0) {
                    result.removeLast();
                }
            }
            result.add(point);
        }
        return result;
    }

    private static boolean clearOfNodes(List<Point> points, Iterable<PlacedNode> nodes) {
        for (PlacedNode node : nodes) {
            for (int index = 1; index < points.size(); index++) {
                Point from = points.get(index - 1);
                Point to = points.get(index);
                if (from.y() == to.y()) {
                    if (from.y() > node.y() && from.y() < node.y() + node.height() && Math.max(from.x(), to.x()) > node.x() && Math.min(from.x(), to.x()) < node.x() + node.width()) {
                        return false;
                    }
                } else if (from.x() > node.x() && from.x() < node.x() + node.width() && Math.max(from.y(), to.y()) > node.y() && Math.min(from.y(), to.y()) < node.y() + node.height()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double length(List<Point> points) {
        double length = 0;
        for (int index = 1; index < points.size(); index++) {
            Point from = points.get(index - 1);
            Point to = points.get(index);
            length += Math.abs(to.x() - from.x()) + Math.abs(to.y() - from.y());
        }
        return length;
    }

    private static double alternatingOffset(int index) {
        return (index + 1) / 2 * GAP * (index % 2 == 1 ? 1 : -1);
    }

    private static Point outward(Point point, Side side, double distance) {
        return switch (side) {
            case TOP -> new Point(point.x(), point.y() - distance);
            case RIGHT -> new Point(point.x() + distance, point.y());
            case BOTTOM -> new Point(point.x(), point.y() + distance);
            case LEFT -> new Point(point.x() - distance, point.y());
        };
    }

    private static Point project(Point point, Side side, Bounds boundary) {
        return switch (side) {
            case TOP -> new Point(point.x(), boundary.y());
            case RIGHT -> new Point(boundary.x() + boundary.width(), point.y());
            case BOTTOM -> new Point(point.x(), boundary.y() + boundary.height());
            case LEFT -> new Point(boundary.x(), point.y());
        };
    }

    private static List<Point> boundaryRoute(Bounds boundary, Point from, Side fromSide, Point to, Side toSide) {
        Point fromLane = project(from, fromSide, boundary);
        Point toLane = project(to, toSide, boundary);
        double width = boundary.width();
        double height = boundary.height();
        double perimeter = 2 * (width + height);
        double start = boundaryPosition(boundary, fromLane, fromSide);
        double finish = boundaryPosition(boundary, toLane, toSide);
        boolean clockwise = (finish - start + perimeter) % perimeter <= perimeter / 2;
        if (!clockwise) {
            double swap = start;
            start = finish;
            finish = swap;
        }
        if (finish < start) {
            finish += perimeter;
        }
        double[] positions = { width, width + height, 2 * width + height, perimeter };
        Point[] corners = { new Point(boundary.x() + width, boundary.y()),
                new Point(boundary.x() + width, boundary.y() + height),
                new Point(boundary.x(), boundary.y() + height), new Point(boundary.x(), boundary.y()) };
        List<Point> middle = new ObjectArrayList<>();
        for (int lap = 0; lap < 2; lap++) {
            for (int corner = 0; corner < positions.length; corner++) {
                double position = positions[corner] + lap * perimeter;
                if (position > start && position < finish) {
                    middle.add(corners[corner]);
                }
            }
        }
        List<Point> points = new ObjectArrayList<>();
        points.add(from);
        points.add(fromLane);
        points.addAll(clockwise ? middle : middle.reversed());
        points.add(toLane);
        points.add(to);
        return points;
    }

    private static double boundaryPosition(Bounds boundary, Point point, Side side) {
        return switch (side) {
            case TOP -> point.x() - boundary.x();
            case RIGHT -> boundary.width() + point.y() - boundary.y();
            case BOTTOM -> 2 * boundary.width() + boundary.height() - (point.x() - boundary.x());
            case LEFT -> 2 * (boundary.width() + boundary.height()) - (point.y() - boundary.y());
        };
    }

    record Component(int id, List<PlacedNode> nodes, Int2ObjectMap<Side> outward, double width, double height) {}

    private record EdgePorts(int ordinal, ViewEdge view, int sourcePort, int targetPort, CraftingPlanRouteGroup group,
                             IntList originalEdgeIds) {}

    private record PortKey(int node, boolean source, CraftingPlanRouteGroup group) {}

    private record TerminalKey(int node, boolean source, boolean bottom, CraftingPlanRouteGroup group) {}

    private record LaneKey(CraftingPlanRouteGroup group, int direction) {}

    private record LocalRoute(EdgePorts edge, List<Point> points) {}

    private record Passage(int rank, int gap, int lane) {}

    private record Anchor(double x, boolean top) {}

    private record Terminal(RoutingComponent component, List<Point> points, boolean bottom) {

        private double x() {
            return component.global(points.getLast()).x();
        }
    }

    private static final class RoutingComponent {

        private final Component input;
        private final int rank;
        private final Int2ObjectMap<PlacedNode> nodes = new Int2ObjectAVLTreeMap<>();
        private final OrthogonalSegmentReservations occupied = new OrthogonalSegmentReservations();
        private final Object2ObjectMap<TerminalKey, Terminal> terminals = new Object2ObjectOpenHashMap<>();
        private final List<LocalRoute> internal = new ObjectArrayList<>();
        private double left;
        private double top;
        private double right;
        private double bottom;
        private double x;
        private double y;

        private RoutingComponent(Component input, int rank) {
            this.input = input;
            this.rank = rank;
            right = input.width();
            bottom = input.height();
            input.nodes().forEach(node -> nodes.put(node.id(), node));
        }

        private Bounds boundary(int lane) {
            double inset = -8 - lane * GAP;
            return new Bounds(inset, inset, input.width() - 2 * inset, input.height() - 2 * inset);
        }

        private void include(List<Point> points) {
            for (Point point : points) {
                left = Math.min(left, point.x());
                top = Math.min(top, point.y());
                right = Math.max(right, point.x());
                bottom = Math.max(bottom, point.y());
            }
        }

        private double width() {
            return right - left;
        }

        private double height() {
            return bottom - top;
        }

        private Point global(Point point) {
            return new Point(x + point.x() - left, y + point.y() - top);
        }
    }

    private static final class Layer {

        private final List<RoutingComponent> groups;
        private final int[] used;
        private final double[] centers;
        private final List<Object2IntMap<LaneKey>> lanes = new ObjectArrayList<>();

        private Layer(List<RoutingComponent> groups) {
            this.groups = groups;
            used = new int[groups.size() + 1];
            centers = new double[used.length];
            for (int gap = 0; gap < used.length; gap++) lanes.add(new Object2IntLinkedOpenHashMap<>());
        }

        private double gapWidth(int index) {
            return Math.max(index == 0 || index == groups.size() ? PADDING : 2 * PADDING, (used[index] + 2) * GAP);
        }

        private double width() {
            double width = groups.stream().mapToDouble(RoutingComponent::width).sum();
            for (int gap = 0; gap < used.length; gap++) {
                width += gapWidth(gap);
            }
            return width;
        }
    }

    private static final class ExternalRoute {

        private final EdgePorts edge;
        private final Terminal source;
        private final Terminal target;
        private final List<Passage> passages = new ObjectArrayList<>();
        private final List<BandVisit> visits = new ObjectArrayList<>();

        private ExternalRoute(EdgePorts edge, Terminal source, Terminal target) {
            this.edge = edge;
            this.source = source;
            this.target = target;
        }
    }

    private static final class BandEndpoint {

        private final Anchor anchor;
        private final LaneKey lane;
        private double column;
        private int header = -1;

        private BandEndpoint(Anchor anchor, LaneKey lane) {
            this.anchor = anchor;
            this.lane = lane;
        }
    }

    private static final class BandVisit {

        private final Band band;
        private final BandEndpoint from;
        private final BandEndpoint to;
        private final CraftingPlanRouteGroup group;
        private int track;

        private BandVisit(Band band, Anchor from, Anchor to, CraftingPlanRouteGroup group) {
            this.band = band;
            this.group = group;
            this.from = band.endpoint(from, new LaneKey(group, from.top() ? 1 : -1));
            this.to = band.endpoint(to, new LaneKey(group, to.top() ? -1 : 1));
        }

        private List<Point> points() {
            double laneY = band.y + PADDING + (band.topHeaders + 1 + track) * GAP;
            List<Point> points = new ObjectArrayList<>();
            appendEndpoint(points, from, laneY);
            List<Point> end = new ObjectArrayList<>();
            appendEndpoint(end, to, laneY);
            points.addAll(end.reversed());
            return points;
        }

        private void appendEndpoint(List<Point> points, BandEndpoint endpoint, double laneY) {
            double boundaryY = band.y + (endpoint.anchor.top() ? 0 : band.height);
            points.add(new Point(endpoint.anchor.x(), boundaryY));
            if (endpoint.header >= 0) {
                double headerY = endpoint.anchor.top() ? band.y + PADDING + endpoint.header * GAP : band.y + band.height - PADDING - endpoint.header * GAP;
                points.add(new Point(endpoint.anchor.x(), headerY));
                points.add(new Point(endpoint.column, headerY));
            }
            points.add(new Point(endpoint.column, laneY));
        }
    }

    private static final class Band {

        private final List<BandVisit> visits = new ObjectArrayList<>();
        private final Object2ObjectMap<EndpointKey, BandEndpoint> endpoints = new Object2ObjectLinkedOpenHashMap<>();
        private final Double2ObjectAVLTreeMap<List<BandEndpoint>> originals = new Double2ObjectAVLTreeMap<>();
        private final Double2ObjectAVLTreeMap<LaneKey> columns = new Double2ObjectAVLTreeMap<>();
        private int topHeaders;
        private int bottomHeaders;
        private double y;
        private double height = 3 * PADDING;

        private BandEndpoint endpoint(Anchor anchor, LaneKey lane) {
            return endpoints.computeIfAbsent(new EndpointKey(anchor, lane), unused -> new BandEndpoint(anchor, lane));
        }

        private void allocate() {
            for (BandEndpoint endpoint : endpoints.values()) {
                originals.computeIfAbsent(endpoint.anchor.x(), unused -> new ObjectArrayList<>()).add(endpoint);
            }
            for (BandEndpoint endpoint : endpoints.values()) allocateEndpoint(endpoint);
            // Track occupancy merges compatible overlaps, not unrelated cycles or reverse flows.
            List<BandVisit> ordered = new ObjectArrayList<>(visits);
            ordered.sort(Comparator.comparingDouble(visit -> Math.min(visit.from.column, visit.to.column)));
            List<OrthogonalSegmentReservations> tracks = new ObjectArrayList<>();
            for (BandVisit visit : ordered) {
                if (Math.abs(visit.to.column - visit.from.column) < EPSILON) continue;
                List<Point> interval = List.of(new Point(visit.from.column, 0), new Point(visit.to.column, 0));
                int track = tracks.size();
                double best = Double.POSITIVE_INFINITY;
                for (int candidate = 0; candidate < tracks.size(); candidate++) {
                    double added = tracks.get(candidate).addedLength(interval, visit.group, false);
                    if (added < best) {
                        best = added;
                        track = candidate;
                        if (added == 0) break;
                    }
                }
                if (track == tracks.size()) tracks.add(new OrthogonalSegmentReservations(GAP));
                tracks.get(track).reserve(interval, visit.group, false);
                visit.track = track;
            }
            height = Math.max(height, 2 * PADDING + (topHeaders + bottomHeaders + Math.max(1, tracks.size()) + 2) * GAP);
        }

        private void allocateEndpoint(BandEndpoint endpoint) {
            double reused = Double.NaN;
            double distance = Double.POSITIVE_INFINITY;
            for (var existing : columns.subMap(endpoint.anchor.x() - 2 * PADDING, endpoint.anchor.x() + 2 * PADDING).double2ObjectEntrySet()) {
                double offset = Math.abs(existing.getDoubleKey() - endpoint.anchor.x());
                if (existing.getValue().equals(endpoint.lane) && offset < distance && columnAvailable(existing.getDoubleKey(), endpoint)) {
                    reused = existing.getDoubleKey();
                    distance = offset;
                }
            }
            if (!Double.isNaN(reused)) {
                assignColumn(endpoint, reused);
                return;
            }
            int attempts = 4 * (originals.size() + columns.size() + 1);
            for (int attempt = 0; attempt < attempts; attempt++) {
                double column = endpoint.anchor.x() + alternatingOffset(attempt);
                if (columnAvailable(column, endpoint)) {
                    assignColumn(endpoint, column);
                    return;
                }
            }
            throw new IllegalStateException("Cannot allocate a distinct vertical column in an inter-layer band");
        }

        private void assignColumn(BandEndpoint endpoint, double column) {
            endpoint.column = column;
            columns.put(column, endpoint.lane);
            if (Math.abs(column - endpoint.anchor.x()) > EPSILON) {
                endpoint.header = endpoint.anchor.top() ? topHeaders++ : bottomHeaders++;
            }
        }

        private boolean columnAvailable(double column, BandEndpoint endpoint) {
            // A moved trunk must also avoid the original short entry stems in the top/bottom fan-out regions.
            for (List<BandEndpoint> endpoints : originals.subMap(column - GAP + EPSILON, column + GAP).values()) {
                for (BandEndpoint existing : endpoints) {
                    if (existing != endpoint && !existing.lane.equals(endpoint.lane)) {
                        return false;
                    }
                }
            }
            for (var occupied : columns.subMap(column - GAP + EPSILON, column + GAP).double2ObjectEntrySet()) {
                if (!occupied.getValue().equals(endpoint.lane) || Math.abs(occupied.getDoubleKey() - column) > EPSILON) {
                    return false;
                }
            }
            return true;
        }

        private record EndpointKey(Anchor anchor, LaneKey lane) {}
    }
}
