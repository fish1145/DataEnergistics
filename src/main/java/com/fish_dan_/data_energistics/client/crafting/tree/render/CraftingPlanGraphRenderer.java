package com.fish_dan_.data_energistics.client.crafting.tree.render;

import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts.CycleMark;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts.RouteStyle;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Bounds;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.RoutedCurve;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteCrossing;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteCrossing.Underpass;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Run;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Segment;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGroup.Style;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphViewLod;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

/**
 * Shared screen/export renderer. Geometry is drawn anew at the destination pixel density, never enlarged from a bitmap.
 */
public final class CraftingPlanGraphRenderer {

    private final CraftingPlanGraph graph;
    private final CraftingPlanGraphDrawingFacts facts;
    private @Nullable Layout styledLayout;
    private final Object2ObjectOpenHashMap<Style, RouteStyle> routeStyles = new Object2ObjectOpenHashMap<>();
    private final ObjectArrayList<RouteStyle> segmentStyles = new ObjectArrayList<>();
    private final ObjectArrayList<RouteStyle> runStyles = new ObjectArrayList<>();
    private final Int2ObjectOpenHashMap<ObjectList<CraftingPlanRouteCrossing>> bridges = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<ObjectList<Underpass>> underpasses = new Int2ObjectOpenHashMap<>();
    private double maximumBridgeRadius;
    private final ObjectArrayList<NodeDrawing> nodeDrawings = new ObjectArrayList<>();
    private final ObjectArrayList<NodeDrawing> visibleNodes = new ObjectArrayList<>();

    public CraftingPlanGraphRenderer(CraftingPlanGraph graph) {
        this.graph = graph;
        this.facts = new CraftingPlanGraphDrawingFacts(graph);
    }

    public static AEKey key(PlacedNode node) {
        return node.viewNode().sourceNode() instanceof Material material ? material.key() : ((Process) node.viewNode().sourceNode()).primaryOutput();
    }

    public void draw(GuiGraphics graphics, Layout layout, GraphViewLod lod,
                     boolean showAmounts, int selectedNodeId, IntSet highlighted,
                     CraftingPlanSegmentSelection highlightedSegments,
                     IntSet highlightedRoutes,
                     @Nullable Bounds viewport, float pixelScale, float contentZoom, boolean screenPixelStyles) {
        prepare(layout);
        double styleScale = screenPixelStyles ? Math.min(1, 1 / contentZoom) : 1;
        CraftingPlanGraphStrokes strokes = new CraftingPlanGraphStrokes(graphics, pixelScale, viewport);
        for (int routeId = 0; routeId < layout.curves().size(); routeId++) {
            RoutedCurve curve = layout.curves().get(routeId);
            drawCurve(strokes, curve, this.routeStyles.computeIfAbsent(curve.group().style(), this.facts::route),
                    highlightedRoutes.contains(routeId), pixelScale, styleScale, viewport, lod);
        }
        CraftingPlanRouteGeometry geometry = layout.geometry();
        for (int runId = 0; runId < geometry.runs().size(); runId++) {
            drawRun(strokes, geometry, geometry.runs().get(runId), this.runStyles.get(runId), highlightedSegments,
                    pixelScale, styleScale, viewport, lod);
        }
        if (lod != GraphViewLod.BLOCK) {
            for (int segmentId : geometry.terminalSegments()) {
                Segment segment = geometry.segments().get(segmentId);
                RouteStyle style = this.segmentStyles.get(segmentId);
                double width = styleScale * (highlightedSegments.contains(segmentId) ? CraftingPlanGraphRouteDrawing.HIGHLIGHT_WIDTH : CraftingPlanGraphRouteDrawing.STROKE_WIDTH);
                double arrowSize = styleScale * CraftingPlanGraphRouteDrawing.ARROW_SIZE;
                if (blocksArrow(segmentId, segment.from().x(), segment.from().y(), arrowSize)) continue;
                // Layout routes encode demand, so this terminal marker points along actual material flow.
                strokes.arrow(segment.to().x(), segment.to().y(), segment.from().x(), segment.from().y(),
                        arrowSize, width, style.color(0));
            }
        }
        this.visibleNodes.clear();
        for (NodeDrawing drawing : this.nodeDrawings) {
            PlacedNode node = drawing.node();
            if (!visible(node.x(), node.y(), node.width(), node.height(), viewport)) continue;
            this.visibleNodes.add(drawing);
            double x = node.x();
            double y = node.y();
            double w = node.width();
            double h = node.height();
            boolean materialNode = node.viewNode().sourceNode() instanceof Material;
            boolean selected = selectedNodeId == node.id() || highlighted.contains(node.id());
            List<CycleMark> cycles = this.facts.node(node.id());
            int cycleColor = cycles.isEmpty() ? CraftingPlanGraphPalette.FRAME : cycles.getFirst().color();
            int border = selected ? CraftingPlanGraphPalette.ACCENT : drawing.missing() ? CraftingPlanGraphPalette.MISSING : cycleColor;
            int surface = selected ? CraftingPlanGraphPalette.SELECTED : materialNode ? CraftingPlanGraphPalette.MATERIAL : CraftingPlanGraphPalette.PROCESS;
            double borderWidth = Math.min(styleScale, Math.min(w, h) / 4);
            double stateWidth = Math.min(2 * styleScale, w / 3);
            strokes.fill(x, y, x + w, y + h, border);
            strokes.fill(x + borderWidth, y + borderWidth, x + w - borderWidth, y + h - borderWidth, surface);
            int stateColor = drawing.missing() ? CraftingPlanGraphPalette.MISSING : materialNode && ((Material) node.viewNode().sourceNode()).stored().signum() > 0 ? CraftingPlanGraphPalette.STORED : cycles.isEmpty() ? CraftingPlanGraphPalette.ACCENT : cycleColor;
            strokes.fill(x, y, x + stateWidth, y + h, stateColor);
            for (int i = 0; i < cycles.size(); i++) {
                strokes.fill(x + w * i / cycles.size(), y + h - stateWidth,
                        x + w * (i + 1) / cycles.size(), y + h, cycles.get(i).color());
            }
        }
        // Bounded geometry batches precede native AEKey handlers, which may perform immediate blits.
        strokes.flush();
        if (lod == GraphViewLod.BLOCK) return;
        for (NodeDrawing drawing : this.visibleNodes) {
            PlacedNode node = drawing.node();
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(node.x(), node.y(), 0);
            AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, 6, 7, drawing.key());
            smallText(graphics, drawing.name(), 27, 5, node.width() - 32, CraftingPlanGraphPalette.TEXT);
            if (lod == GraphViewLod.FULL) {
                List<CycleMark> cycles = this.facts.node(node.id());
                boolean embeddedCount = showAmounts && node.embeddedProcessId() != null;
                double badgeWidth = embeddedCount ? (node.width() - 18) / 2 : node.width() - 12;
                if (showAmounts) smallText(graphics, drawing.amount(), 27, 15, node.width() - 32,
                        drawing.missing() ? CraftingPlanGraphPalette.MISSING : CraftingPlanGraphPalette.ACCENT);
                if (embeddedCount) smallText(graphics, drawing.embeddedAmount(), 6, node.height() - 9,
                        cycles.isEmpty() ? node.width() - 20 : node.width() - badgeWidth - 18, CraftingPlanGraphPalette.MUTED_TEXT);
                if (!cycles.isEmpty()) {
                    smallText(graphics, this.facts.label(node.id()), node.width() - badgeWidth - 6, node.height() - 9,
                            badgeWidth, CraftingPlanGraphPalette.TEXT);
                } else if (node.viewNode().expandable()) {
                    smallText(graphics, node.viewNode().collapsed() ? "+" : "−",
                            node.width() - 9, node.height() - 9, 8, CraftingPlanGraphPalette.FRAME);
                }
            }
            graphics.pose().popPose();
        }
    }

    private static void drawCurve(CraftingPlanGraphStrokes strokes, RoutedCurve curve, RouteStyle style,
                                  boolean highlighted, float pixelScale, double styleScale,
                                  @Nullable Bounds viewport, GraphViewLod lod) {
        double minX = Math.min(Math.min(curve.from().x(), curve.to().x()),
                Math.min(curve.firstControl().x(), curve.secondControl().x()));
        double minY = Math.min(Math.min(curve.from().y(), curve.to().y()),
                Math.min(curve.firstControl().y(), curve.secondControl().y()));
        double maxX = Math.max(Math.max(curve.from().x(), curve.to().x()),
                Math.max(curve.firstControl().x(), curve.secondControl().x()));
        double maxY = Math.max(Math.max(curve.from().y(), curve.to().y()),
                Math.max(curve.firstControl().y(), curve.secondControl().y()));
        if (!visible(minX, minY, maxX - minX, maxY - minY, viewport)) return;
        double length = curveLength(curve);
        int steps = Math.clamp((int) Math.ceil(length * pixelScale / 6), 12, 512);
        int bands = CraftingPlanGraphRouteDrawing.bandCount(style, length, pixelScale);
        double width = styleScale * (highlighted ? CraftingPlanGraphRouteDrawing.HIGHLIGHT_WIDTH : CraftingPlanGraphRouteDrawing.STROKE_WIDTH);
        Point previous = curve.from();
        for (int step = 1; step <= steps; step++) {
            double fraction = step / (double) steps;
            Point next = cubic(curve, fraction);
            int color = bands == 1 ? style.lineColor() : style.color(Math.min(bands - 1, (int) (fraction * bands)));
            strokes.line(previous.x(), previous.y(), next.x(), next.y(), width, color);
            previous = next;
        }
        if (lod == GraphViewLod.BLOCK || !style.materialFlow()) return;
        double arrowSize = styleScale * CraftingPlanGraphRouteDrawing.ARROW_SIZE;
        Point terminalTail = cubic(curve, Math.min(1, arrowSize * 1.5 / Math.max(length, arrowSize)));
        strokes.arrow(terminalTail.x(), terminalTail.y(), curve.from().x(), curve.from().y(),
                arrowSize, width, style.color(0));
        if (!CraftingPlanGraphRouteDrawing.hasInteriorArrows(style, length, pixelScale)) return;
        int arrows = CraftingPlanGraphRouteDrawing.interiorArrowCount(length, pixelScale);
        double delta = Math.min(0.08, arrowSize / Math.max(length, arrowSize));
        for (int index = 0; index < arrows; index++) {
            double fraction = (index + 1D) / (arrows + 1);
            Point tip = cubic(curve, fraction);
            Point tail = cubic(curve, Math.min(1, fraction + delta));
            strokes.arrow(tail.x(), tail.y(), tip.x(), tip.y(), arrowSize, width,
                    style.color(Math.min(bands - 1, (int) (fraction * bands))));
        }
    }

    private static double curveLength(RoutedCurve curve) {
        Point previous = curve.from();
        double length = 0;
        for (int step = 1; step <= 16; step++) {
            Point next = cubic(curve, step / 16D);
            length += Math.hypot(next.x() - previous.x(), next.y() - previous.y());
            previous = next;
        }
        return length;
    }

    private static Point cubic(RoutedCurve curve, double fraction) {
        double inverse = 1 - fraction;
        double first = inverse * inverse * inverse;
        double second = 3 * inverse * inverse * fraction;
        double third = 3 * inverse * fraction * fraction;
        double last = fraction * fraction * fraction;
        return new Point(first * curve.from().x() + second * curve.firstControl().x() + third * curve.secondControl().x() + last * curve.to().x(),
                first * curve.from().y() + second * curve.firstControl().y() + third * curve.secondControl().y() + last * curve.to().y());
    }

    private void prepare(Layout layout) {
        if (this.styledLayout == layout) return;
        this.routeStyles.clear();
        this.segmentStyles.clear();
        for (Segment segment : layout.geometry().segments()) {
            this.segmentStyles.add(this.routeStyles.computeIfAbsent(segment.group().style(), this.facts::route));
        }
        this.runStyles.clear();
        for (Run run : layout.geometry().runs()) {
            this.runStyles.add(this.routeStyles.computeIfAbsent(run.group().style(), this.facts::route));
        }
        this.bridges.clear();
        this.underpasses.clear();
        this.maximumBridgeRadius = 0;
        for (CraftingPlanRouteCrossing crossing : layout.geometry().crossings()) {
            this.maximumBridgeRadius = Math.max(this.maximumBridgeRadius, crossing.radius());
            this.bridges.computeIfAbsent(crossing.bridgeSegmentId(), unused -> new ObjectArrayList<>()).add(crossing);
            for (Underpass underpass : crossing.underpasses()) {
                this.underpasses.computeIfAbsent(underpass.segmentId(), unused -> new ObjectArrayList<>()).add(underpass);
            }
        }
        this.bridges.values().forEach(crossings -> crossings.sort(Comparator.comparingDouble(CraftingPlanRouteCrossing::y)));
        this.underpasses.values().forEach(gaps -> gaps.sort(Comparator.comparingDouble(Underpass::x)));
        this.nodeDrawings.clear();
        for (PlacedNode node : layout.nodes()) {
            AEKey key = key(node);
            boolean materialNode = node.viewNode().sourceNode() instanceof Material;
            boolean missing = materialNode && (((Material) node.viewNode().sourceNode()).missing().signum() > 0 || ((Material) node.viewNode().sourceNode()).unresolved().signum() > 0);
            BigInteger amount = node.id() == this.graph.rootId() ? this.graph.header().requested() : node.viewNode().sourceNode() instanceof Material material ? material.required().signum() > 0 ? material.required() : material.crafting() : ((Process) node.viewNode().sourceNode()).executions();
            String embedded = node.embeddedProcessId() == null ? "" : "× " + TrinityAmountFormatter.format(((Process) this.graph.node(node.embeddedProcessId())).executions());
            this.nodeDrawings.add(new NodeDrawing(node, key, key.getDisplayName().getString(),
                    (materialNode ? "" : "× ") + TrinityAmountFormatter.format(amount), embedded, missing));
        }
        this.styledLayout = layout;
    }

    private void drawRun(CraftingPlanGraphStrokes strokes, CraftingPlanRouteGeometry geometry, Run run,
                         RouteStyle style, CraftingPlanSegmentSelection highlightedSegments, float pixelScale,
                         double styleScale, @Nullable Bounds viewport, GraphViewLod lod) {
        double dx = run.to().x() - run.from().x();
        double dy = run.to().y() - run.from().y();
        double length = Math.hypot(dx, dy);
        double margin = this.maximumBridgeRadius + styleScale * (CraftingPlanGraphRouteDrawing.ARROW_SIZE * 0.55 + CraftingPlanGraphRouteDrawing.HIGHLIGHT_WIDTH) + 0.5 / pixelScale;
        if (!visible(Math.min(run.from().x(), run.to().x()) - margin,
                Math.min(run.from().y(), run.to().y()) - margin, Math.abs(dx) + 2 * margin,
                Math.abs(dy) + 2 * margin, viewport))
            return;
        int bands = CraftingPlanGraphRouteDrawing.bandCount(style, length, pixelScale);
        int first = 0;
        int last = run.segmentIds().size() - 1;
        if (viewport != null) {
            double firstDistance = viewportDistance(run, viewport, margin, length, true);
            double lastDistance = viewportDistance(run, viewport, margin, length, false);
            first = CraftingPlanGraphRouteDrawing.segmentIndexAt(run, geometry, firstDistance);
            last = CraftingPlanGraphRouteDrawing.segmentIndexAt(run, geometry, lastDistance);
        }
        for (int index = first; index <= last; index++) {
            int segmentId = run.segmentIds().getInt(index);
            Segment segment = geometry.segments().get(segmentId);
            double width = styleScale * (highlightedSegments.contains(segmentId) ? CraftingPlanGraphRouteDrawing.HIGHLIGHT_WIDTH : CraftingPlanGraphRouteDrawing.STROKE_WIDTH);
            drawSegment(strokes, segmentId, segment, style, width, distanceFromRun(run, segment.from()), length, bands,
                    pixelScale, viewport, margin);
        }
        if (lod == GraphViewLod.BLOCK || !CraftingPlanGraphRouteDrawing.hasInteriorArrows(style, length, pixelScale)) return;
        int arrows = CraftingPlanGraphRouteDrawing.interiorArrowCount(length, pixelScale);
        for (int arrow = 0; arrow < arrows; arrow++) {
            double fraction = (arrow + 1D) / (arrows + 1);
            double tipX = run.from().x() + dx * fraction;
            double tipY = run.from().y() + dy * fraction;
            double depth = Math.min(styleScale * CraftingPlanGraphRouteDrawing.ARROW_SIZE, length * (1 - fraction));
            int segmentId = run.segmentIds().getInt(CraftingPlanGraphRouteDrawing.segmentIndexAt(run, geometry, fraction * length));
            if (CraftingPlanGraphRouteDrawing.blocksArrow(run, geometry, fraction * length, depth,
                    this.bridges, this.underpasses, styleScale, this.maximumBridgeRadius))
                continue;
            double width = styleScale * (highlightedSegments.contains(segmentId) ? CraftingPlanGraphRouteDrawing.HIGHLIGHT_WIDTH : CraftingPlanGraphRouteDrawing.STROKE_WIDTH);
            strokes.arrow(tipX + dx / length * depth,
                    tipY + dy / length * depth, tipX, tipY,
                    depth, width,
                    style.color(Math.min(bands - 1, (int) (fraction * bands))));
        }
    }

    private void drawSegment(CraftingPlanGraphStrokes strokes, int segmentId, Segment segment, RouteStyle style, double width,
                             double offset, double runLength, int bands, float pixelScale,
                             @Nullable Bounds viewport, double viewportMargin) {
        ObjectList<CraftingPlanRouteCrossing> bridge = this.bridges.get(segmentId);
        ObjectList<Underpass> underpass = this.underpasses.get(segmentId);
        if (bridge != null) {
            drawBridge(strokes, segment, style, width, offset, runLength, bands, pixelScale, bridge, viewport,
                    viewportMargin);
        } else if (underpass != null) {
            drawUnderpass(strokes, segment, style, width, offset, runLength, bands,
                    underpass, viewport, viewportMargin);
        } else {
            drawPiece(strokes, segment.from(), segment.to(), style, width, offset,
                    offset + Math.hypot(segment.to().x() - segment.from().x(), segment.to().y() - segment.from().y()), runLength, bands);
        }
    }

    private static void drawPiece(CraftingPlanGraphStrokes strokes, Point from, Point to, RouteStyle style, double width,
                                  double startOffset, double endOffset, double runLength, int bands) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        if (style.cycles().size() <= 1) {
            strokes.line(from.x(), from.y(), to.x(), to.y(), width, style.lineColor());
            return;
        }
        double bandLength = runLength / bands;
        int first = Math.max(0, (int) Math.floor(startOffset / bandLength));
        int last = Math.min(bands, (int) Math.ceil(endOffset / bandLength));
        for (int band = first; band < last; band++) {
            double start = Math.max(startOffset, band * bandLength);
            double end = Math.min(endOffset, (band + 1D) * bandLength);
            if (start >= end) continue;
            double startFraction = (start - startOffset) / (endOffset - startOffset);
            double endFraction = (end - startOffset) / (endOffset - startOffset);
            strokes.line(from.x() + dx * startFraction, from.y() + dy * startFraction,
                    from.x() + dx * endFraction, from.y() + dy * endFraction, width, style.color(band));
        }
    }

    private static void drawUnderpass(CraftingPlanGraphStrokes strokes, Segment segment, RouteStyle style, double width,
                                      double offset, double runLength, int bands,
                                      ObjectList<Underpass> underpasses, @Nullable Bounds viewport,
                                      double viewportMargin) {
        Point cursor = segment.from();
        boolean forward = segment.to().x() > segment.from().x();
        int first = underpassStart(underpasses,
                viewport == null ? Double.NEGATIVE_INFINITY : viewport.x() - viewportMargin);
        int last = underpassEnd(underpasses,
                viewport == null ? Double.POSITIVE_INFINITY : viewport.x() + viewport.width() + viewportMargin);
        for (int index = forward ? first : last - 1; index >= first && index < last; index += forward ? 1 : -1) {
            Underpass underpass = underpasses.get(index);
            double center = underpass.x();
            double gap = underpass.gapHalfWidth();
            double near = center + (forward ? -gap : gap);
            double far = center + (forward ? gap : -gap);
            Point entry = new Point(near, segment.from().y());
            Point exit = new Point(far, segment.from().y());
            drawPiece(strokes, cursor, entry, style, width, offset + Math.abs(cursor.x() - segment.from().x()),
                    offset + Math.abs(entry.x() - segment.from().x()), runLength, bands);
            cursor = exit;
        }
        drawPiece(strokes, cursor, segment.to(), style, width, offset + Math.abs(cursor.x() - segment.from().x()),
                offset + Math.abs(segment.to().x() - segment.from().x()), runLength, bands);
    }

    private static void drawBridge(CraftingPlanGraphStrokes strokes, Segment segment, RouteStyle style, double width,
                                   double offset, double runLength, int bands, float pixelScale,
                                   ObjectList<CraftingPlanRouteCrossing> crossings, @Nullable Bounds viewport,
                                   double viewportMargin) {
        Point cursor = segment.from();
        boolean downward = segment.to().y() > segment.from().y();
        int first = crossingStart(crossings, viewport == null ? Double.NEGATIVE_INFINITY : viewport.y() - viewportMargin);
        int last = crossingEnd(crossings,
                viewport == null ? Double.POSITIVE_INFINITY : viewport.y() + viewport.height() + viewportMargin);
        for (int index = downward ? first : last - 1; index >= first && index < last; index += downward ? 1 : -1) {
            CraftingPlanRouteCrossing crossing = crossings.get(index);
            double radius = crossing.radius();
            Point entry = new Point(crossing.x(), crossing.y() + (downward ? -radius : radius));
            Point exit = new Point(crossing.x(), crossing.y() + (downward ? radius : -radius));
            drawPiece(strokes, cursor, entry, style, width, offset + Math.abs(cursor.y() - segment.from().y()),
                    offset + Math.abs(entry.y() - segment.from().y()), runLength, bands);
            if (radius > CraftingPlanRouteCrossing.MAX_RADIUS) {
                Point upper = new Point(crossing.x() + crossing.bend(), entry.y());
                Point lower = new Point(crossing.x() + crossing.bend(), exit.y());
                double entryOffset = offset + Math.abs(entry.y() - segment.from().y());
                double exitOffset = offset + Math.abs(exit.y() - segment.from().y());
                strokes.line(entry.x(), entry.y(), upper.x(), upper.y(), width,
                        bridgeColor(style, bands, entryOffset, runLength));
                drawPiece(strokes, upper, lower, style, width, entryOffset, exitOffset, runLength, bands);
                strokes.line(lower.x(), lower.y(), exit.x(), exit.y(), width,
                        bridgeColor(style, bands, exitOffset, runLength));
                cursor = exit;
                continue;
            }
            Point previous = entry;
            int steps = Math.clamp(2 * (int) Math.ceil(2 * Math.sqrt(radius * pixelScale)), 4, 128);
            for (int step = 1; step <= steps; step++) {
                Point next = bridgePoint(crossing, downward ? step / (double) steps : 1 - step / (double) steps);
                drawPiece(strokes, previous, next, style, width,
                        offset + Math.abs(previous.y() - segment.from().y()),
                        offset + Math.abs(next.y() - segment.from().y()), runLength, bands);
                previous = next;
            }
            cursor = exit;
        }
        drawPiece(strokes, cursor, segment.to(), style, width, offset + Math.abs(cursor.y() - segment.from().y()),
                offset + Math.abs(segment.to().y() - segment.from().y()), runLength, bands);
    }

    private static int bridgeColor(RouteStyle style, int bands, double offset, double runLength) {
        return bands == 1 ? style.lineColor() : style.color(Math.min(bands - 1, (int) (offset / runLength * bands)));
    }

    private boolean blocksArrow(int segmentId, double x, double y, double size) {
        ObjectList<CraftingPlanRouteCrossing> bridge = this.bridges.get(segmentId);
        if (bridge != null) for (CraftingPlanRouteCrossing crossing : bridge) {
            if (Math.abs(y - crossing.y()) <= crossing.radius() + size) return true;
        }
        ObjectList<Underpass> underpass = this.underpasses.get(segmentId);
        if (underpass != null) for (Underpass gap : underpass) {
            if (Math.abs(x - gap.x()) <= gap.gapHalfWidth() + size) return true;
        }
        return false;
    }

    private static Point bridgePoint(CraftingPlanRouteCrossing crossing, double fraction) {
        double local = fraction <= 0.5 ? fraction * 2 : (fraction - 0.5) * 2;
        double bend = crossing.bend();
        double radius = crossing.radius();
        double startX = fraction <= 0.5 ? crossing.x() : crossing.x() + bend;
        double controlX = crossing.x() + bend;
        double endX = fraction <= 0.5 ? crossing.x() + bend : crossing.x();
        double startY = fraction <= 0.5 ? crossing.y() - radius : crossing.y();
        double controlY = fraction <= 0.5 ? crossing.y() - radius : crossing.y() + radius;
        double endY = fraction <= 0.5 ? crossing.y() : crossing.y() + radius;
        double inverse = 1 - local;
        return new Point(inverse * inverse * startX + 2 * inverse * local * controlX + local * local * endX,
                inverse * inverse * startY + 2 * inverse * local * controlY + local * local * endY);
    }

    private static int underpassStart(ObjectList<Underpass> underpasses, double coordinate) {
        int first = 0;
        int last = underpasses.size();
        while (first < last) {
            int middle = (first + last) >>> 1;
            if (underpasses.get(middle).x() < coordinate) first = middle + 1;
            else last = middle;
        }
        return first;
    }

    private static int underpassEnd(ObjectList<Underpass> underpasses, double coordinate) {
        int first = 0;
        int last = underpasses.size();
        while (first < last) {
            int middle = (first + last) >>> 1;
            if (underpasses.get(middle).x() <= coordinate) first = middle + 1;
            else last = middle;
        }
        return first;
    }

    private static int crossingStart(ObjectList<CraftingPlanRouteCrossing> crossings, double coordinate) {
        int first = 0;
        int last = crossings.size();
        while (first < last) {
            int middle = (first + last) >>> 1;
            double value = crossings.get(middle).y();
            if (value < coordinate) first = middle + 1;
            else last = middle;
        }
        return first;
    }

    private static int crossingEnd(ObjectList<CraftingPlanRouteCrossing> crossings, double coordinate) {
        int first = 0;
        int last = crossings.size();
        while (first < last) {
            int middle = (first + last) >>> 1;
            double value = crossings.get(middle).y();
            if (value <= coordinate) first = middle + 1;
            else last = middle;
        }
        return first;
    }

    private static double viewportDistance(Run run, Bounds viewport, double margin, double runLength, boolean first) {
        boolean horizontal = run.from().y() == run.to().y();
        double start = (horizontal ? viewport.x() : viewport.y()) - margin;
        double end = (horizontal ? viewport.x() + viewport.width() : viewport.y() + viewport.height()) + margin;
        double origin = horizontal ? run.from().x() : run.from().y();
        double direction = Math.signum((horizontal ? run.to().x() : run.to().y()) - origin);
        double from = (start - origin) * direction;
        double to = (end - origin) * direction;
        return Math.max(0, Math.min(runLength, first ? Math.min(from, to) : Math.max(from, to)));
    }

    private static double distanceFromRun(Run run, Point point) {
        return run.from().y() == run.to().y() ? Math.abs(point.x() - run.from().x()) : Math.abs(point.y() - run.from().y());
    }

    private static boolean visible(double x, double y, double width, double height, @Nullable Bounds viewport) {
        return viewport == null || x + width >= viewport.x() && y + height >= viewport.y() && x <= viewport.x() + viewport.width() && y <= viewport.y() + viewport.height();
    }

    private static void smallText(GuiGraphics graphics, String text, double x, double y, double maxWidth, int color) {
        var font = Minecraft.getInstance().font;
        String visible = font.plainSubstrByWidth(text, (int) (maxWidth / 0.65F));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.65F, 0.65F, 1);
        graphics.drawString(font, visible, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private record NodeDrawing(PlacedNode node, AEKey key, String name, String amount, String embeddedAmount, boolean missing) {}
}
