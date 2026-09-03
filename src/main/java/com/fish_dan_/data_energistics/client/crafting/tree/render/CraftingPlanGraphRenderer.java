package com.fish_dan_.data_energistics.client.crafting.tree.render;

import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts.CycleMark;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts.RouteStyle;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Bounds;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Run;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Segment;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGroup;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphViewLod;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

/**
 * Shared screen/export renderer. Geometry is drawn anew at the destination pixel density, never enlarged from a bitmap.
 */
public final class CraftingPlanGraphRenderer {

    private final CraftingPlanGraph graph;
    private final CraftingPlanGraphDrawingFacts facts;
    private @Nullable Layout styledLayout;
    private final Object2ObjectOpenHashMap<CraftingPlanRouteGroup, RouteStyle> routeStyles = new Object2ObjectOpenHashMap<>();
    private final ObjectArrayList<RouteStyle> segmentStyles = new ObjectArrayList<>();
    private final ObjectArrayList<RouteStyle> runStyles = new ObjectArrayList<>();
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
                     @Nullable Bounds viewport, float pixelScale) {
        prepare(layout);
        CraftingPlanGraphStrokes strokes = new CraftingPlanGraphStrokes(graphics, pixelScale, viewport);
        CraftingPlanRouteGeometry geometry = layout.geometry();
        for (int runId = 0; runId < geometry.runs().size(); runId++) {
            drawRun(strokes, geometry, geometry.runs().get(runId), this.runStyles.get(runId), highlightedSegments,
                    pixelScale, viewport, lod);
        }
        if (lod != GraphViewLod.BLOCK) {
            for (int segmentId : geometry.terminalSegments()) {
                Segment segment = geometry.segments().get(segmentId);
                RouteStyle style = this.segmentStyles.get(segmentId);
                double width = highlightedSegments.contains(segmentId) ? CraftingPlanGraphRouteDrawing.HIGHLIGHT_WIDTH : CraftingPlanGraphRouteDrawing.STROKE_WIDTH;
                // Layout routes encode demand, so this terminal marker points along actual material flow.
                strokes.arrow(segment.to().x(), segment.to().y(), segment.from().x(), segment.from().y(),
                        CraftingPlanGraphRouteDrawing.ARROW_SIZE, width, style.color(0));
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
            strokes.fill(x, y, x + w, y + h, border);
            strokes.fill(x + 1, y + 1, x + w - 1, y + h - 1, surface);
            int stateColor = drawing.missing() ? CraftingPlanGraphPalette.MISSING : materialNode && ((Material) node.viewNode().sourceNode()).stored().signum() > 0 ? CraftingPlanGraphPalette.STORED : cycles.isEmpty() ? CraftingPlanGraphPalette.ACCENT : cycleColor;
            strokes.fill(x, y, x + 2, y + h, stateColor);
            for (int i = 0; i < cycles.size(); i++) {
                strokes.fill(x + w * i / cycles.size(), y + h - 2,
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
            // Keep fractional layout positions. Integer icon/text APIs operate relative to this exact origin.
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
                    smallText(graphics, node.viewNode().collapsed() ? "+" : "−", node.width() - 9, node.height() - 9, 8, CraftingPlanGraphPalette.FRAME);
                }
            }
            graphics.pose().popPose();
        }
    }

    private void prepare(Layout layout) {
        if (this.styledLayout == layout) return;
        this.routeStyles.clear();
        this.segmentStyles.clear();
        for (Segment segment : layout.geometry().segments()) {
            this.segmentStyles.add(this.routeStyles.computeIfAbsent(segment.group(), this.facts::route));
        }
        this.runStyles.clear();
        for (Run run : layout.geometry().runs()) {
            this.runStyles.add(this.routeStyles.computeIfAbsent(run.group(), this.facts::route));
        }
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

    private static void drawRun(CraftingPlanGraphStrokes strokes, CraftingPlanRouteGeometry geometry, Run run,
                                RouteStyle style, CraftingPlanSegmentSelection highlightedSegments, float pixelScale,
                                @Nullable Bounds viewport, GraphViewLod lod) {
        double dx = run.to().x() - run.from().x();
        double dy = run.to().y() - run.from().y();
        double length = Math.hypot(dx, dy);
        double margin = CraftingPlanGraphRouteDrawing.ARROW_SIZE * 0.55 + CraftingPlanGraphRouteDrawing.HIGHLIGHT_WIDTH + 0.5 / pixelScale;
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
            first = segmentIndexAt(run, geometry, firstDistance);
            last = segmentIndexAt(run, geometry, lastDistance);
        }
        for (int index = first; index <= last; index++) {
            int segmentId = run.segmentIds().getInt(index);
            Segment segment = geometry.segments().get(segmentId);
            double width = highlightedSegments.contains(segmentId) ? CraftingPlanGraphRouteDrawing.HIGHLIGHT_WIDTH : CraftingPlanGraphRouteDrawing.STROKE_WIDTH;
            drawSegment(strokes, segment, style, width, distanceFromRun(run, segment.from()), length, bands);
        }
        if (lod == GraphViewLod.BLOCK || !CraftingPlanGraphRouteDrawing.hasInteriorArrows(style, length, pixelScale)) return;
        int arrows = CraftingPlanGraphRouteDrawing.interiorArrowCount(length, pixelScale);
        for (int arrow = 0; arrow < arrows; arrow++) {
            double fraction = (arrow + 1D) / (arrows + 1);
            double tipX = run.from().x() + dx * fraction;
            double tipY = run.from().y() + dy * fraction;
            int segmentId = run.segmentIds().getInt(segmentIndexAt(run, geometry, fraction * length));
            double width = highlightedSegments.contains(segmentId) ? CraftingPlanGraphRouteDrawing.HIGHLIGHT_WIDTH : CraftingPlanGraphRouteDrawing.STROKE_WIDTH;
            strokes.arrow(tipX + dx / length * CraftingPlanGraphRouteDrawing.ARROW_SIZE,
                    tipY + dy / length * CraftingPlanGraphRouteDrawing.ARROW_SIZE, tipX, tipY,
                    CraftingPlanGraphRouteDrawing.ARROW_SIZE, width,
                    style.color(Math.min(bands - 1, (int) (fraction * bands))));
        }
    }

    private static void drawSegment(CraftingPlanGraphStrokes strokes, Segment segment, RouteStyle style, double width,
                                    double offset, double runLength, int bands) {
        double dx = segment.to().x() - segment.from().x();
        double dy = segment.to().y() - segment.from().y();
        double length = Math.hypot(dx, dy);
        if (style.cycles().size() <= 1) {
            strokes.line(segment.from().x(), segment.from().y(), segment.to().x(), segment.to().y(), width, style.lineColor());
            return;
        }
        double bandLength = runLength / bands;
        int first = Math.max(0, (int) Math.floor(offset / bandLength));
        int last = Math.min(bands, (int) Math.ceil((offset + length) / bandLength));
        for (int band = first; band < last; band++) {
            double start = Math.max(offset, band * bandLength);
            double end = Math.min(offset + length, (band + 1D) * bandLength);
            if (start >= end) continue;
            double startFraction = (start - offset) / length;
            double endFraction = (end - offset) / length;
            strokes.line(segment.from().x() + dx * startFraction, segment.from().y() + dy * startFraction,
                    segment.from().x() + dx * endFraction, segment.from().y() + dy * endFraction, width, style.color(band));
        }
    }

    private static int segmentIndexAt(Run run, CraftingPlanRouteGeometry geometry, double distance) {
        int first = 0;
        int last = run.segmentIds().size() - 1;
        while (first < last) {
            int middle = (first + last + 1) >>> 1;
            Segment segment = geometry.segments().get(run.segmentIds().getInt(middle));
            if (distanceFromRun(run, segment.from()) <= distance) first = middle;
            else last = middle - 1;
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
