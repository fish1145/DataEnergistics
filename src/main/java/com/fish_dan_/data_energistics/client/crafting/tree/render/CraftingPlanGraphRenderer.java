package com.fish_dan_.data_energistics.client.crafting.tree.render;

import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts.CycleMark;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts.RouteStyle;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Bounds;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Layout;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphViewLod;
import it.unimi.dsi.fastutil.ints.IntSet;
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
    private final ObjectArrayList<RouteStyle> routeStyles = new ObjectArrayList<>();
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
                     @Nullable Bounds viewport, float pixelScale) {
        prepare(layout);
        CraftingPlanGraphStrokes strokes = new CraftingPlanGraphStrokes(graphics, pixelScale, viewport);
        for (int edgeIndex = 0; edgeIndex < layout.edges().size(); edgeIndex++) {
            var edge = layout.edges().get(edgeIndex);
            RouteStyle style = this.routeStyles.get(edgeIndex);
            boolean emphasized = highlighted.contains(edge.source()) && highlighted.contains(edge.target());
            double width = emphasized ? 2 : 1.5;
            for (int i = 1; i < edge.points().size(); i++) {
                Point a = edge.points().get(i - 1);
                Point b = edge.points().get(i);
                segment(strokes, a, b, width, pixelScale, style, viewport, lod);
            }
            if (style.materialFlow() && lod != GraphViewLod.BLOCK && edge.points().size() > 1) {
                // Layout edges encode demand; arrows show the opposite, actual material flow.
                Point end = edge.points().getFirst();
                Point previous = edge.points().get(1);
                strokes.arrow(previous.x(), previous.y(), end.x(), end.y(), 5, width, style.color(0));
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
                } else {
                    smallText(graphics, node.viewNode().collapsed() ? "+" : "−", node.width() - 9, node.height() - 9, 8, CraftingPlanGraphPalette.FRAME);
                }
            }
            graphics.pose().popPose();
        }
    }

    private void prepare(Layout layout) {
        if (this.styledLayout == layout) return;
        this.routeStyles.clear();
        for (var edge : layout.edges()) this.routeStyles.add(this.facts.route(edge.originalEdgeIds()));
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

    private static void segment(CraftingPlanGraphStrokes strokes, Point a, Point b, double width,
                                float pixelScale, RouteStyle style, @Nullable Bounds viewport, GraphViewLod lod) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = Math.hypot(dx, dy);
        if (length == 0) return;
        double margin = width + 0.5 / pixelScale;
        if (!visible(Math.min(a.x(), b.x()) - margin, Math.min(a.y(), b.y()) - margin,
                Math.abs(dx) + 2 * margin, Math.abs(dy) + 2 * margin, viewport))
            return;
        // Diagnostics are continuous, muted, undirected evidence links, not broken material-flow routes.
        int bands = 1;
        if (style.cycles().size() <= 1) {
            strokes.line(a.x(), a.y(), b.x(), b.y(), width, style.materialFlow() ? style.color(0) : CraftingPlanGraphPalette.DIAGNOSTIC);
        } else {
            bands = Math.max(style.cycles().size(), Math.min(48, (int) Math.ceil(length / Math.max(24, 18 / pixelScale))));
            double bandLength = length / bands;
            int first = 0;
            int last = bands;
            if (viewport != null) {
                double start = dx == 0 ? (viewport.y() - a.y()) / dy : (viewport.x() - a.x()) / dx;
                double end = dx == 0 ? (viewport.y() + viewport.height() - a.y()) / dy : (viewport.x() + viewport.width() - a.x()) / dx;
                first = Math.max(0, (int) Math.floor(Math.min(start, end) * length / bandLength) - 1);
                last = Math.min(bands, (int) Math.ceil(Math.max(start, end) * length / bandLength) + 1);
            }
            for (int band = first; band < last; band++) {
                double start = band * bandLength / length;
                double end = Math.min(1, (band + 1) * bandLength / length);
                strokes.line(a.x() + dx * start, a.y() + dy * start,
                        a.x() + dx * end, a.y() + dy * end, width, style.color(band));
            }
        }
        if (!style.materialFlow() || lod == GraphViewLod.BLOCK || length * pixelScale < 48) return;
        // Fixed world-sized arrows cannot swell across adjacent lanes as the camera zooms out.
        int arrows = Math.max(1, Math.min(4, (int) (length * pixelScale / 160)));
        for (int i = 0; i < arrows; i++) {
            double fraction = (i + 1D) / (arrows + 1);
            double tipX = a.x() + dx * fraction;
            double tipY = a.y() + dy * fraction;
            strokes.arrow(tipX + dx / length * 5, tipY + dy / length * 5, tipX, tipY, 5, width,
                    style.color(Math.min(bands - 1, (int) (fraction * bands))));
        }
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
