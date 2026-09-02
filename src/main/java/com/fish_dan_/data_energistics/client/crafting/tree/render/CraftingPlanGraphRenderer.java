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
import net.minecraft.client.renderer.RenderType;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphViewLod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/** Shared screen/export renderer. Coordinates are immutable layout coordinates, independent of UI transforms. */
public final class CraftingPlanGraphRenderer {
    private final CraftingPlanGraph graph;
    private final CraftingPlanGraphDrawingFacts facts;
    private @Nullable Layout styledLayout;
    private List<RouteStyle> routeStyles = List.of();

    public CraftingPlanGraphRenderer(CraftingPlanGraph graph) {
        this.graph = graph;
        this.facts = new CraftingPlanGraphDrawingFacts(graph);
    }

    public static AEKey key(PlacedNode node) {
        return node.viewNode().sourceNode() instanceof Material material ? material.key()
                : ((Process) node.viewNode().sourceNode()).primaryOutput();
    }

    public void draw(GuiGraphics graphics, Layout layout, GraphViewLod lod,
                            boolean showAmounts, int selectedNodeId, Set<Integer> highlighted,
                            @Nullable Bounds viewport) {
        if (this.styledLayout != layout) {
            this.routeStyles = layout.edges().stream().map(edge -> this.facts.route(edge.originalEdgeIds())).toList();
            this.styledLayout = layout;
        }
        Matrix4f pose = graphics.pose().last().pose();
        float scale = (float) Math.hypot(pose.m00(), pose.m01());
        // Keep routes and borders above a screen-space hairline when the graph or export is minified.
        float stroke = Math.max(1.5F, 1.25F / scale);
        float outline = Math.max(1F, 1F / scale);
        for (int edgeIndex = 0; edgeIndex < layout.edges().size(); edgeIndex++) {
            var edge = layout.edges().get(edgeIndex);
            RouteStyle style = this.routeStyles.get(edgeIndex);
            boolean emphasized = highlighted.contains(edge.source()) && highlighted.contains(edge.target());
            float width = emphasized ? stroke * 1.5F : stroke;
            for (int i = 1; i < edge.points().size(); i++) {
                Point a = edge.points().get(i - 1);
                Point b = edge.points().get(i);
                if (visible(Math.min(a.x(), b.x()) - width, Math.min(a.y(), b.y()) - width,
                        Math.abs(a.x() - b.x()) + width * 2, Math.abs(a.y() - b.y()) + width * 2, viewport)) {
                    segment(graphics, a, b, width, scale, style);
                }
            }
            if (style.materialFlow() && edge.points().size() > 1) {
                // Layout edges encode demand; visible arrows show the opposite, actual material flow.
                Point end = edge.points().getFirst();
                Point previous = edge.points().get(1);
                float arrowSize = Math.max(5F, 3.5F / scale);
                if (visible(end.x() - arrowSize, end.y() - arrowSize, arrowSize * 2, arrowSize * 2, viewport)) {
                    arrow(graphics, previous, end, arrowSize, width, style.color(0));
                }
            }
        }
        for (PlacedNode node : layout.nodes()) {
            if (!visible(node.x(), node.y(), node.width(), node.height(), viewport)) continue;
            int x = (int) node.x();
            int y = (int) node.y();
            int w = (int) node.width();
            int h = (int) node.height();
            boolean materialNode = node.viewNode().sourceNode() instanceof Material;
            boolean missing = materialNode && (((Material) node.viewNode().sourceNode()).missing().signum() > 0
                    || ((Material) node.viewNode().sourceNode()).unresolved().signum() > 0);
            boolean selected = selectedNodeId == node.id() || highlighted.contains(node.id());
            List<CycleMark> cycles = this.facts.node(node.id());
            int cycleColor = cycles.isEmpty() ? CraftingPlanGraphPalette.FRAME : cycles.getFirst().color();
            int border = selected ? CraftingPlanGraphPalette.ACCENT : missing ? CraftingPlanGraphPalette.MISSING
                    : cycleColor;
            int surface = selected ? CraftingPlanGraphPalette.SELECTED
                    : materialNode ? CraftingPlanGraphPalette.MATERIAL : CraftingPlanGraphPalette.PROCESS;
            float inset = Math.min(outline, Math.min(w, h) / 3F);
            fill(graphics, x, y, x + w, y + h, border);
            fill(graphics, x + inset, y + inset, x + w - inset, y + h - inset, surface);
            int stateColor = missing ? CraftingPlanGraphPalette.MISSING : materialNode
                    && ((Material) node.viewNode().sourceNode()).stored().signum() > 0 ? CraftingPlanGraphPalette.STORED
                    : cycles.isEmpty() ? CraftingPlanGraphPalette.ACCENT : cycleColor;
            fill(graphics, x, y, x + Math.min(4F, outline * 2), y + h, stateColor);
            for (int i = 0; i < cycles.size(); i++) {
                fill(graphics, x + w * i / (float) cycles.size(), y + h - inset * 2,
                        x + w * (i + 1) / (float) cycles.size(), y + h, cycles.get(i).color());
            }
            if (lod == GraphViewLod.BLOCK) continue;
            AEKey key = key(node);
            // The middle LOD keeps actual ingredient identity and the same icon hit box as the full view.
            AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x + 6, y + 7, key);
            smallText(graphics, key.getDisplayName().getString(), x + 27, y + 5, w - 32, CraftingPlanGraphPalette.TEXT);
            if (lod == GraphViewLod.FULL) {
                boolean embeddedCount = showAmounts && node.embeddedProcessId() != null;
                int badgeWidth = embeddedCount ? (w - 18) / 2 : w - 12;
                if (showAmounts) {
                    BigInteger amount = node.id() == graph.rootId() ? graph.header().requested()
                            : node.viewNode().sourceNode() instanceof Material material ? material.required().signum() > 0 ? material.required() : material.crafting()
                            : ((Process) node.viewNode().sourceNode()).executions();
                    smallText(graphics, (materialNode ? "" : "× ") + TrinityAmountFormatter.format(amount),
                            x + 27, y + 15, w - 32, missing ? CraftingPlanGraphPalette.MISSING : CraftingPlanGraphPalette.ACCENT);
                }
                if (embeddedCount) {
                    Process process = (Process) graph.node(node.embeddedProcessId());
                    smallText(graphics, "× " + TrinityAmountFormatter.format(process.executions()), x + 6, y + h - 9,
                            cycles.isEmpty() ? w - 20 : w - badgeWidth - 18, CraftingPlanGraphPalette.MUTED_TEXT);
                }
                if (!cycles.isEmpty()) {
                    smallText(graphics, this.facts.label(node.id()), x + w - badgeWidth - 6, y + h - 9,
                            badgeWidth, CraftingPlanGraphPalette.TEXT);
                } else {
                    smallText(graphics, node.viewNode().collapsed() ? "+" : "−", x + w - 9, y + h - 9, 8, border);
                }
            }
        }
    }

    private static void segment(GuiGraphics graphics, Point a, Point b, float width, float scale, RouteStyle style) {
        double length = Math.hypot(b.x() - a.x(), b.y() - a.y());
        if (length == 0) return;
        int bands = style.materialFlow() ? style.cycles().size() > 1
                ? Math.max(style.cycles().size(), Math.min(48, (int) Math.ceil(length / Math.max(24, 18 / scale)))) : 1
                : Math.min(128, Math.max(2, (int) Math.ceil(length / Math.max(8, 5 / scale))));
        for (int band = 0; band < bands; band++) {
            if (!style.materialFlow() && (band & 1) != 0) continue;
            line(graphics, interpolate(a, b, band / (double) bands),
                    interpolate(a, b, (band + 1D) / bands), width, style.color(band));
        }
        if (!style.materialFlow() || length * scale < 24) return;
        // Repeated interior arrowheads make long routes and post-turn segments readable without relying on up/down.
        int arrows = Math.max(1, Math.min(4, (int) (length * scale / 100)));
        float arrowSize = Math.max(5F, 3.5F / scale);
        for (int i = 0; i < arrows; i++) {
            double fraction = (i + 1D) / (arrows + 1);
            Point tip = interpolate(a, b, fraction);
            Point from = interpolate(a, b, Math.min(1, fraction + arrowSize / length));
            arrow(graphics, from, tip, arrowSize, width, style.color(Math.min(bands - 1, (int) (fraction * bands))));
        }
    }

    private static Point interpolate(Point a, Point b, double fraction) {
        return new Point(a.x() + (b.x() - a.x()) * fraction, a.y() + (b.y() - a.y()) * fraction);
    }

    private static void line(GuiGraphics graphics, Point a, Point b, float width, int color) {
        double length = Math.hypot(b.x() - a.x(), b.y() - a.y());
        if (length == 0) return;
        float halfX = (float) ((b.y() - a.y()) / length * width / 2);
        float halfY = (float) ((a.x() - b.x()) / length * width / 2);
        VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = graphics.pose().last().pose();
        vertices.addVertex(pose, (float) a.x() + halfX, (float) a.y() + halfY, 0).setColor(color);
        vertices.addVertex(pose, (float) a.x() - halfX, (float) a.y() - halfY, 0).setColor(color);
        vertices.addVertex(pose, (float) b.x() - halfX, (float) b.y() - halfY, 0).setColor(color);
        vertices.addVertex(pose, (float) b.x() + halfX, (float) b.y() + halfY, 0).setColor(color);
    }

    private static void arrow(GuiGraphics graphics, Point from, Point tip, float size, float width, int color) {
        double length = Math.hypot(tip.x() - from.x(), tip.y() - from.y());
        if (length == 0) return;
        double dx = (tip.x() - from.x()) / length;
        double dy = (tip.y() - from.y()) / length;
        double depth = Math.min(size, length);
        double x = tip.x() - dx * depth;
        double y = tip.y() - dy * depth;
        line(graphics, new Point(x + dy * depth * 0.55, y - dx * depth * 0.55), tip, width, color);
        line(graphics, new Point(x - dy * depth * 0.55, y + dx * depth * 0.55), tip, width, color);
    }

    private static void fill(GuiGraphics graphics, float left, float top, float right, float bottom, int color) {
        VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = graphics.pose().last().pose();
        vertices.addVertex(pose, left, top, 0).setColor(color);
        vertices.addVertex(pose, left, bottom, 0).setColor(color);
        vertices.addVertex(pose, right, bottom, 0).setColor(color);
        vertices.addVertex(pose, right, top, 0).setColor(color);
    }

    private static boolean visible(double x, double y, double width, double height, @Nullable Bounds viewport) {
        return viewport == null || x + width >= viewport.x() && y + height >= viewport.y()
                && x <= viewport.x() + viewport.width() && y <= viewport.y() + viewport.height();
    }

    private static void smallText(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        var font = Minecraft.getInstance().font;
        String visible = font.plainSubstrByWidth(text, (int) (maxWidth / 0.65F));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.65F, 0.65F, 1);
        graphics.drawString(font, visible, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
