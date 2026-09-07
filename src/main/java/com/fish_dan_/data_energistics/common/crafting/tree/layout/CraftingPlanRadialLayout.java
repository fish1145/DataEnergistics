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

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;

/** Concentric dependency rings with stable branch sectors, upright cards and compact local loop blocks. */
public final class CraftingPlanRadialLayout {

    private static final Side[] SIDES = Side.values();

    private CraftingPlanRadialLayout() {}

    public static Layout layout(ViewGraph graph, boolean compact) {
        if (graph.nodes().isEmpty()) {
            return new Layout(List.of(), List.of(), new Bounds(0, 0, 0, 0),
                    CraftingPlanRouteGeometry.EMPTY, List.of());
        }
        Int2ObjectMap<PlacedNode> placed = placeRings(graph, compact);
        List<RadialRequest> requests = requests(graph, placed);
        List<RoutedEdge> edges = new ObjectArrayList<>(requests.size());
        List<RoutedCurve> curves = new ObjectArrayList<>(requests.size());
        for (RadialRequest request : requests) {
            checkInterrupted();
            Curve curve = curve(request, edges.size(), placed.values());
            edges.add(new RoutedEdge(request.source(), request.target(), request.cyclic(),
                    request.originals(), request.group(), List.of()));
            curves.add(new RoutedCurve(request.source(), request.target(), request.cyclic(),
                    request.originals(), request.group(), curve.from(), curve.firstControl(),
                    curve.secondControl(), curve.to()));
        }
        return shift(placed, edges, curves, graph.rootId(), compact ? 12 : 18);
    }

    private static Int2ObjectMap<PlacedNode> placeRings(ViewGraph graph, boolean compact) {
        var dependency = CraftingPlanGraphLayout.place(graph, compact);
        Int2IntMap ports = portCounts(graph);
        Int2ObjectMap<RingBlock> blocks = new Int2ObjectAVLTreeMap<>();
        int rootComponent = -1;
        for (PlacedNode node : dependency.nodes()) {
            RingBlock block = blocks.computeIfAbsent(node.viewNode().componentId(),
                    unused -> new RingBlock(dependency.ranks().get(node.id())));
            block.nodes.add(node);
            block.minY = Math.min(block.minY, node.y());
            block.maxY = Math.max(block.maxY, node.y() + node.height());
            if (node.id() == graph.rootId()) rootComponent = node.viewNode().componentId();
        }
        RingBlock root = blocks.get(rootComponent);
        double gap = compact ? 12 : 20;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        Int2ObjectMap<List<RingBlock>> rings = new Int2ObjectAVLTreeMap<>();
        for (RingBlock block : blocks.values()) {
            checkInterrupted();
            block.nodes.sort(Comparator.comparingInt((PlacedNode node) -> node.id() == graph.rootId() ? 0 : 1)
                    .thenComparingDouble(PlacedNode::y).thenComparingDouble(PlacedNode::x).thenComparingInt(PlacedNode::id));
            for (PlacedNode node : block.nodes) {
                block.cardWidth = Math.max(block.cardWidth, Math.max(compact ? 72 : 84, 12 + 2D * ports.get(node.id())));
                block.cardHeight = Math.max(block.cardHeight, Math.max(compact ? 30 : 36, 12 + 2D * ports.get(node.id())));
            }
            block.columns = Math.max(1, (int) Math.ceil(Math.sqrt(block.nodes.size() * block.cardHeight / block.cardWidth)));
            int rows = (block.nodes.size() + block.columns - 1) / block.columns;
            block.width = block.columns * block.cardWidth + (block.columns - 1) * gap;
            block.height = rows * block.cardHeight + (rows - 1) * gap;
            block.envelope = Math.hypot(block.width, block.height) / 2;
            minY = Math.min(minY, block.minY);
            maxY = Math.max(maxY, block.maxY);
            if (block != root) rings.computeIfAbsent(block.rank + 1, unused -> new ObjectArrayList<>()).add(block);
        }
        double span = maxY - minY + 2 * gap;
        double previousRadius = 0;
        double previousEnvelope = Math.hypot(root.width - root.cardWidth / 2, root.height - root.cardHeight / 2);
        for (List<RingBlock> ring : rings.values()) {
            double envelope = 0;
            for (RingBlock block : ring) {
                block.angle = -Math.PI + 2 * Math.PI * ((block.minY + block.maxY) / 2 - minY + gap) / span;
                envelope = Math.max(envelope, block.envelope);
            }
            ring.sort(Comparator.comparingDouble(block -> block.angle));
            double radius = previousRadius + previousEnvelope + envelope + 2 * gap;
            if (ring.size() > 1) {
                for (int index = 0; index < ring.size(); index++) {
                    RingBlock first = ring.get(index);
                    RingBlock second = ring.get((index + 1) % ring.size());
                    double angle = second.angle - first.angle;
                    if (index == ring.size() - 1) angle += 2 * Math.PI;
                    // Disjoint angular envelopes also separate non-neighbours, without pairwise repulsion.
                    radius = Math.max(radius, (Math.max(first.envelope, second.envelope) + gap) /
                            Math.sin(Math.min(Math.PI, angle) / 2));
                }
            }
            for (RingBlock block : ring) block.radius = radius;
            previousRadius = radius;
            previousEnvelope = envelope;
        }
        Int2ObjectMap<PlacedNode> placed = new Int2ObjectAVLTreeMap<>();
        for (RingBlock block : blocks.values()) {
            checkInterrupted();
            double x = block.radius * Math.cos(block.angle) - block.width / 2;
            double y = block.radius * Math.sin(block.angle) - block.height / 2;
            if (block == root) {
                x = -block.cardWidth / 2;
                y = -block.cardHeight / 2;
            }
            for (int index = 0; index < block.nodes.size(); index++) {
                PlacedNode node = block.nodes.get(index);
                placed.put(node.id(), new PlacedNode(node.viewNode(),
                        x + index % block.columns * (block.cardWidth + gap),
                        y + index / block.columns * (block.cardHeight + gap), block.cardWidth, block.cardHeight));
            }
        }
        return placed;
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private static final class RingBlock {

        private final int rank;
        private final List<PlacedNode> nodes = new ObjectArrayList<>();
        private double minY = Double.POSITIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double cardWidth;
        private double cardHeight;
        private int columns;
        private double width;
        private double height;
        private double envelope;
        private double angle;
        private double radius;

        private RingBlock(int rank) {
            this.rank = rank;
        }
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
                    source.forcedSideOrdinal = Side.RIGHT.ordinal();
                    target.forcedSideOrdinal = Side.TOP.ordinal();
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
            intent.sideOrdinal = intent.forcedSideOrdinal < 0 ? side(intent).ordinal() : intent.forcedSideOrdinal;
            sides.computeIfAbsent(new NodeSide(intent.node.id(), intent.side()), unused -> new ObjectArrayList<>()).add(intent);
        }
        for (var entry : sides.object2ObjectEntrySet()) {
            Side side = entry.getKey().side();
            boolean horizontal = side == Side.TOP || side == Side.BOTTOM;
            entry.getValue().sort(Comparator.comparingDouble((PortIntent intent) -> (horizontal ? intent.x : intent.y) / intent.count).thenComparingInt(intent -> intent.ordinal));
            for (int index = 0; index < entry.getValue().size(); index++) {
                PortIntent intent = entry.getValue().get(index);
                Point point = port(intent.node, side, (index + 1D) / (entry.getValue().size() + 1D));
                intent.pointX = point.x();
                intent.pointY = point.y();
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
        Point from = request.sourceIntent().point();
        Point to = request.targetIntent().point();
        if (request.source() == request.target()) {
            double reach = Math.max(request.sourceIntent().node.width(), request.sourceIntent().node.height()) + 36 + 4D * (routeId & 3);
            Vector first = outward(request.sourceIntent().side());
            Vector second = outward(request.targetIntent().side());
            return new Curve(from, new Point(from.x() + first.x() * reach, from.y() + first.y() * reach),
                    new Point(to.x() + second.x() * reach, to.y() + second.y() * reach), to);
        }
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double length = Math.hypot(dx, dy);
        double handle = Math.min(120, length * 0.38);
        Vector first = outward(request.sourceIntent().side());
        Vector second = outward(request.targetIntent().side());
        Curve candidate = new Curve(from, new Point(from.x() + first.x() * handle, from.y() + first.y() * handle),
                new Point(to.x() + second.x() * handle, to.y() + second.y() * handle), to);
        if (!intersectsNode(candidate, request.source(), request.target(), nodes)) return candidate;
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
                                List<RoutedCurve> curves, int rootId, double padding) {
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
        Point root = center(placed.get(rootId));
        double horizontalExtent = Math.max(root.x() - minX, maxX - root.x());
        double verticalExtent = Math.max(root.y() - minY, maxY - root.y());
        minX = root.x() - horizontalExtent;
        minY = root.y() - verticalExtent;
        maxX = root.x() + horizontalExtent;
        maxY = root.y() + verticalExtent;
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
        private int forcedSideOrdinal = -1;
        private int sideOrdinal;
        private double pointX;
        private double pointY;

        private PortIntent(PlacedNode node, int ordinal) {
            this.node = node;
            this.ordinal = ordinal;
        }

        private Side side() {
            return SIDES[sideOrdinal];
        }

        private Point point() {
            return new Point(pointX, pointY);
        }
    }
}
