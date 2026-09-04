package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Bounds;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.RoutedCurve;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.RoutedEdge;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Side;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGroup.Style;
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
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Root-centered radial placement with direct cubic semantic connections and no orthogonal channel state. */
public final class CraftingPlanRadialLayout {

    private static final double TAU = 2 * Math.PI;

    private CraftingPlanRadialLayout() {}

    public static Layout layout(ViewGraph graph, boolean compact) {
        if (graph.nodes().isEmpty()) {
            return new Layout(List.of(), List.of(), new Bounds(0, 0, 0, 0),
                    CraftingPlanRouteGeometry.EMPTY, List.of());
        }
        double cardWidth = compact ? 72 : 84;
        double baseHeight = compact ? 30 : 36;
        double ringGap = compact ? 28 : 42;
        Int2ObjectMap<ViewNode> nodeById = new Int2ObjectOpenHashMap<>();
        for (ViewNode node : graph.nodes()) nodeById.put(node.id(), node);
        Int2IntMap depths = depths(graph, nodeById);
        Int2IntMap portCounts = portCounts(graph);
        Int2ObjectMap<List<ViewNode>> rings = new Int2ObjectAVLTreeMap<>();
        Int2ObjectMap<IntList> incoming = new Int2ObjectOpenHashMap<>();
        for (ViewEdge edge : graph.edges()) {
            incoming.computeIfAbsent(edge.target(), unused -> new IntArrayList()).add(edge.source());
        }
        for (ViewNode node : graph.nodes()) {
            rings.computeIfAbsent(depths.get(node.id()), unused -> new ObjectArrayList<>()).add(node);
        }
        for (List<ViewNode> ring : rings.values()) {
            ring.sort(Comparator.comparingInt(ViewNode::componentId).thenComparingInt(ViewNode::id));
        }
        Int2ObjectMap<PlacedNode> placed = new Int2ObjectOpenHashMap<>();
        Int2DoubleMap angles = new Int2DoubleOpenHashMap();
        ViewNode root = nodeById.get(graph.rootId());
        double rootHeight = Math.max(baseHeight, 12 + 2D * portCounts.get(root.id()));
        placed.put(root.id(), new PlacedNode(root, -cardWidth / 2, -rootHeight / 2, cardWidth, rootHeight));
        angles.put(root.id(), 0);
        double previousRadius = Math.max(cardWidth, rootHeight) / 2;
        for (var entry : rings.int2ObjectEntrySet()) {
            List<ViewNode> ring = entry.getValue();
            if (entry.getIntKey() == 0) ring = ring.stream().filter(node -> node.id() != root.id()).toList();
            if (ring.isEmpty()) continue;
            ring = new ObjectArrayList<>(ring);
            Int2DoubleMap componentAngles = new Int2DoubleOpenHashMap();
            for (ViewNode node : ring) {
                if (!componentAngles.containsKey(node.componentId())) {
                    componentAngles.put(node.componentId(), parentAngle(node, incoming, angles));
                }
            }
            ring.sort(Comparator.comparingDouble((ViewNode node) -> componentAngles.get(node.componentId()))
                    .thenComparingInt(ViewNode::componentId).thenComparingInt(ViewNode::id));
            double maximumHeight = baseHeight;
            for (ViewNode node : ring) maximumHeight = Math.max(maximumHeight, 12 + 2D * portCounts.get(node.id()));
            double maximumExtent = Math.max(cardWidth, maximumHeight);
            double laneStep = Math.min(ringGap, maximumExtent * 0.35);
            double radius = Math.max(previousRadius + maximumExtent + ringGap + laneStep,
                    ring.size() * (maximumExtent + ringGap / 2) / TAU + laneStep);
            Int2IntMap componentLanes = new Int2IntOpenHashMap();
            componentLanes.defaultReturnValue(Integer.MIN_VALUE);
            int componentIndex = 0;
            for (ViewNode node : ring) {
                if (componentLanes.get(node.componentId()) != Integer.MIN_VALUE) continue;
                int lane = switch (componentIndex++ % 3) {
                    case 1 -> -1;
                    case 2 -> 1;
                    default -> 0;
                };
                componentLanes.put(node.componentId(), lane);
            }
            double offset = (entry.getIntKey() & 1) == 0 ? Math.PI / ring.size() : 0;
            for (int index = 0; index < ring.size(); index++) {
                ViewNode node = ring.get(index);
                double height = Math.max(baseHeight, 12 + 2D * portCounts.get(node.id()));
                double angle = offset + TAU * index / ring.size();
                double nodeRadius = radius + componentLanes.get(node.componentId()) * laneStep;
                double centerX = nodeRadius * Math.cos(angle);
                double centerY = nodeRadius * Math.sin(angle);
                angles.put(node.id(), angle);
                placed.put(node.id(), new PlacedNode(node, centerX - cardWidth / 2,
                        centerY - height / 2, cardWidth, height));
            }
            previousRadius = radius + laneStep + maximumExtent / 2;
        }
        List<RadialRequest> requests = requests(graph, placed);
        resizeForPorts(requests, placed, cardWidth, baseHeight);
        List<RoutedEdge> edges = new ObjectArrayList<>(requests.size());
        List<RoutedCurve> curves = new ObjectArrayList<>(requests.size());
        for (RadialRequest request : requests) {
            Curve curve = curve(request, edges.size(), placed.values());
            edges.add(new RoutedEdge(request.source(), request.target(), request.cyclic(),
                    request.originals(), request.group(), List.of()));
            curves.add(new RoutedCurve(request.source(), request.target(), request.cyclic(),
                    request.originals(), request.group(), curve.from(), curve.firstControl(),
                    curve.secondControl(), curve.to()));
        }
        return shift(placed, edges, curves, compact ? 12 : 18);
    }

    private static double parentAngle(ViewNode node, Int2ObjectMap<IntList> incoming, Int2DoubleMap angles) {
        IntList parents = incoming.get(node.id());
        if (parents == null) return node.id();
        double x = 0;
        double y = 0;
        int count = 0;
        for (int parent : parents) {
            if (!angles.containsKey(parent)) continue;
            double angle = angles.get(parent);
            x += Math.cos(angle);
            y += Math.sin(angle);
            count++;
        }
        if (count == 0) return node.id();
        double angle = Math.atan2(y, x);
        return angle < 0 ? angle + TAU : angle;
    }

    private static Int2IntMap depths(ViewGraph graph, Int2ObjectMap<ViewNode> nodes) {
        Int2ObjectMap<IntList> outgoing = new Int2ObjectOpenHashMap<>();
        for (ViewEdge edge : graph.edges()) {
            outgoing.computeIfAbsent(edge.source(), unused -> new IntArrayList()).add(edge.target());
        }
        var result = new Int2IntOpenHashMap();
        result.defaultReturnValue(Integer.MAX_VALUE);
        result.put(graph.rootId(), 0);
        var pending = new IntArrayFIFOQueue();
        pending.enqueue(graph.rootId());
        int maximum = 0;
        while (!pending.isEmpty()) {
            int source = pending.dequeueInt();
            IntList targets = outgoing.get(source);
            if (targets == null) continue;
            int depth = result.get(source) + 1;
            for (int target : targets) {
                if (depth >= result.get(target)) continue;
                result.put(target, depth);
                maximum = Math.max(maximum, depth);
                pending.enqueue(target);
            }
        }
        Int2IntMap componentDepth = new Int2IntOpenHashMap();
        componentDepth.defaultReturnValue(Integer.MAX_VALUE);
        for (ViewNode node : nodes.values()) {
            int depth = result.get(node.id());
            if (depth != Integer.MAX_VALUE && depth < componentDepth.get(node.componentId())) {
                componentDepth.put(node.componentId(), depth);
            }
        }
        for (ViewNode node : nodes.values()) {
            int depth = componentDepth.get(node.componentId());
            result.put(node.id(), depth == Integer.MAX_VALUE ? maximum + 1 : depth);
        }
        return result;
    }

    private static void resizeForPorts(List<RadialRequest> requests, Int2ObjectMap<PlacedNode> nodes,
                                       double baseWidth, double baseHeight) {
        var intents = new ObjectOpenHashSet<PortIntent>();
        for (RadialRequest request : requests) {
            intents.add(request.sourceIntent());
            intents.add(request.targetIntent());
        }
        Int2ObjectMap<int[]> counts = new Int2ObjectOpenHashMap<>();
        for (PortIntent intent : intents) {
            counts.computeIfAbsent(intent.node.id(), unused -> new int[Side.values().length])[intent.side.ordinal()]++;
        }
        for (var entry : counts.int2ObjectEntrySet()) {
            PlacedNode node = nodes.get(entry.getIntKey());
            int[] sides = entry.getValue();
            double width = Math.max(baseWidth, 12 + 2D * Math.max(sides[Side.TOP.ordinal()], sides[Side.BOTTOM.ordinal()]));
            double height = Math.max(baseHeight, 12 + 2D * Math.max(sides[Side.LEFT.ordinal()], sides[Side.RIGHT.ordinal()]));
            Point center = center(node);
            nodes.put(node.id(), new PlacedNode(node.viewNode(), center.x() - width / 2,
                    center.y() - height / 2, width, height));
            intentNodes(intents, node.id(), nodes.get(node.id()));
        }
        assignPorts(intents);
    }

    private static void intentNodes(Iterable<PortIntent> intents, int nodeId, PlacedNode node) {
        for (PortIntent intent : intents) if (intent.node.id() == nodeId) intent.node = node;
    }

    private static Int2IntMap portCounts(ViewGraph graph) {
        var result = new Int2IntOpenHashMap();
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
        for (NodePort port : ports) result.addTo(port.node(), 1);
        return result;
    }

    private static List<RadialRequest> requests(ViewGraph graph, Int2ObjectMap<PlacedNode> nodes) {
        var styles = CraftingPlanRouteGroup.indexStyles(graph.source());
        Object2ObjectMap<PortKey, PortIntent> intents = new Object2ObjectLinkedOpenHashMap<>();
        List<RadialRequest> result = new ObjectArrayList<>();
        for (ViewEdge edge : graph.edges()) {
            Object2ObjectMap<Style, IntList> split = new Object2ObjectLinkedOpenHashMap<>();
            for (int original : edge.originalEdgeIds()) {
                split.computeIfAbsent(styles.get(original), unused -> new IntArrayList()).add(original);
            }
            for (var entry : split.object2ObjectEntrySet()) {
                Style style = entry.getKey();
                int destination = style.materialFlow() ? edge.source() : edge.target();
                var group = new CraftingPlanRouteGroup(style, destination);
                PortIntent source = intent(intents, nodes.get(edge.source()), nodes.get(edge.target()), true, group);
                PortIntent target = intent(intents, nodes.get(edge.target()), nodes.get(edge.source()), false, group);
                if (edge.source() == edge.target()) {
                    source.forced = Side.RIGHT;
                    target.forced = Side.TOP;
                }
                result.add(new RadialRequest(edge.source(), edge.target(), edge.cyclic(),
                        IntLists.unmodifiable(entry.getValue()), group, source, target));
            }
        }
        assignPorts(intents.values());
        return result;
    }

    private static PortIntent intent(Object2ObjectMap<PortKey, PortIntent> intents, PlacedNode node,
                                     PlacedNode other, boolean source, CraftingPlanRouteGroup group) {
        var key = new PortKey(node.id(), source, group);
        PortIntent intent = intents.computeIfAbsent(key, unused -> new PortIntent(node, intents.size()));
        Point center = center(other);
        intent.x += center.x();
        intent.y += center.y();
        intent.count++;
        return intent;
    }

    private static void assignPorts(Iterable<PortIntent> intents) {
        Object2ObjectMap<NodeSide, List<PortIntent>> sides = new Object2ObjectLinkedOpenHashMap<>();
        for (PortIntent intent : intents) {
            intent.side = intent.forced == null ? side(intent) : intent.forced;
            sides.computeIfAbsent(new NodeSide(intent.node.id(), intent.side), unused -> new ObjectArrayList<>()).add(intent);
        }
        for (var entry : sides.object2ObjectEntrySet()) {
            Side side = entry.getKey().side();
            boolean horizontal = side == Side.TOP || side == Side.BOTTOM;
            entry.getValue().sort(Comparator.comparingDouble((PortIntent intent) -> (horizontal ? intent.x : intent.y) / intent.count).thenComparingInt(intent -> intent.ordinal));
            for (int index = 0; index < entry.getValue().size(); index++) {
                PortIntent intent = entry.getValue().get(index);
                intent.point = port(intent.node, side, (index + 1D) / (entry.getValue().size() + 1D));
            }
        }
    }

    private static Side side(PortIntent intent) {
        Point center = center(intent.node);
        double dx = intent.x / intent.count - center.x();
        double dy = intent.y / intent.count - center.y();
        if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? Side.RIGHT : Side.LEFT;
        return dy >= 0 ? Side.BOTTOM : Side.TOP;
    }

    private static Point port(PlacedNode node, Side side, double fraction) {
        double horizontal = node.x() + 6 + fraction * Math.max(0, node.width() - 12);
        double vertical = node.y() + 6 + fraction * Math.max(0, node.height() - 12);
        return switch (side) {
            case TOP -> new Point(horizontal, node.y());
            case RIGHT -> new Point(node.x() + node.width(), vertical);
            case BOTTOM -> new Point(horizontal, node.y() + node.height());
            case LEFT -> new Point(node.x(), vertical);
        };
    }

    private static Curve curve(RadialRequest request, int routeId, Iterable<PlacedNode> nodes) {
        Point from = request.sourceIntent().point;
        Point to = request.targetIntent().point;
        if (request.source() == request.target()) {
            double reach = Math.max(request.sourceIntent().node.width(), request.sourceIntent().node.height()) + 36 + 4D * (routeId & 3);
            Vector first = outward(request.sourceIntent().side);
            Vector second = outward(request.targetIntent().side);
            return new Curve(from, new Point(from.x() + first.x() * reach, from.y() + first.y() * reach),
                    new Point(to.x() + second.x() * reach, to.y() + second.y() * reach), to);
        }
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double length = Math.hypot(dx, dy);
        double handle = Math.min(120, length * 0.38);
        Vector first = outward(request.sourceIntent().side);
        Vector second = outward(request.targetIntent().side);
        Curve candidate = new Curve(from, from, to, to);
        for (int attempt = 0; attempt < 6; attempt++) {
            double bow = Math.min(length * 0.42, (request.cyclic() ? 36 : 12) + attempt * 18D);
            double sign = ((routeId + attempt / 3) & 1) == 0 ? 1 : -1;
            double nx = length == 0 ? 0 : -dy / length * bow * sign;
            double ny = length == 0 ? 0 : dx / length * bow * sign;
            candidate = new Curve(from,
                    new Point(from.x() + first.x() * handle + nx, from.y() + first.y() * handle + ny),
                    new Point(to.x() + second.x() * handle + nx, to.y() + second.y() * handle + ny), to);
            if (!intersectsNode(candidate, request.source(), request.target(), nodes)) return candidate;
        }
        return candidate;
    }

    private static boolean intersectsNode(Curve curve, int source, int target, Iterable<PlacedNode> nodes) {
        for (int step = 1; step < 24; step++) {
            Point point = cubic(curve, step / 24D);
            for (PlacedNode node : nodes) {
                if (node.id() != source && node.id() != target && node.contains(point.x(), point.y())) return true;
            }
        }
        return false;
    }

    private static Point cubic(Curve curve, double fraction) {
        double inverse = 1 - fraction;
        return new Point(inverse * inverse * inverse * curve.from().x() + 3 * inverse * inverse * fraction * curve.firstControl().x() + 3 * inverse * fraction * fraction * curve.secondControl().x() + fraction * fraction * fraction * curve.to().x(),
                inverse * inverse * inverse * curve.from().y() + 3 * inverse * inverse * fraction * curve.firstControl().y() + 3 * inverse * fraction * fraction * curve.secondControl().y() + fraction * fraction * fraction * curve.to().y());
    }

    private static Point center(PlacedNode node) {
        return new Point(node.x() + node.width() / 2, node.y() + node.height() / 2);
    }

    private static Vector outward(Side side) {
        return switch (side) {
            case TOP -> new Vector(0, -1);
            case RIGHT -> new Vector(1, 0);
            case BOTTOM -> new Vector(0, 1);
            case LEFT -> new Vector(-1, 0);
        };
    }

    private static Layout shift(Int2ObjectMap<PlacedNode> placed, List<RoutedEdge> edges,
                                List<RoutedCurve> curves, double padding) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (PlacedNode node : placed.values()) {
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.x() + node.width());
            maxY = Math.max(maxY, node.y() + node.height());
        }
        for (RoutedCurve curve : curves) {
            for (Point point : List.of(curve.from(), curve.firstControl(), curve.secondControl(), curve.to())) {
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }
        }
        double dx = padding - minX;
        double dy = padding - minY;
        List<PlacedNode> nodes = new ObjectArrayList<>(placed.size());
        for (PlacedNode node : placed.values()) {
            nodes.add(new PlacedNode(node.viewNode(), node.x() + dx, node.y() + dy,
                    node.width(), node.height()));
        }
        List<RoutedCurve> shifted = new ObjectArrayList<>(curves.size());
        for (RoutedCurve curve : curves) {
            shifted.add(new RoutedCurve(curve.source(), curve.target(), curve.cyclic(), curve.originalEdgeIds(),
                    curve.group(), move(curve.from(), dx, dy), move(curve.firstControl(), dx, dy),
                    move(curve.secondControl(), dx, dy), move(curve.to(), dx, dy)));
        }
        return new Layout(nodes, edges, new Bounds(0, 0, maxX - minX + 2 * padding,
                maxY - minY + 2 * padding), CraftingPlanRouteGeometry.EMPTY, shifted);
    }

    private static Point move(Point point, double x, double y) {
        return new Point(point.x() + x, point.y() + y);
    }

    private record Curve(Point from, Point firstControl, Point secondControl, Point to) {}

    private record NodePort(int node, boolean source, Style style, int destination) {}

    private record PortKey(int node, boolean source, CraftingPlanRouteGroup group) {}

    private record NodeSide(int node, Side side) {}

    private record RadialRequest(int source, int target, boolean cyclic, IntList originals,
                                 CraftingPlanRouteGroup group, PortIntent sourceIntent,
                                 PortIntent targetIntent) {}

    private record Vector(double x, double y) {}

    private static final class PortIntent {

        private PlacedNode node;
        private final int ordinal;
        private double x;
        private double y;
        private int count;
        private @Nullable Side forced;
        private @Nullable Side side;
        private @Nullable Point point;

        private PortIntent(PlacedNode node, int ordinal) {
            this.node = node;
            this.ordinal = ordinal;
        }
    }
}
