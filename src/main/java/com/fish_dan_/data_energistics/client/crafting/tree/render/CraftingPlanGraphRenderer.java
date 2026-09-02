package com.fish_dan_.data_energistics.client.crafting.tree.render;

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
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Set;

/** Shared screen/export renderer. Coordinates are immutable layout coordinates, independent of UI transforms. */
public final class CraftingPlanGraphRenderer {
    private CraftingPlanGraphRenderer() {}

    public static AEKey key(PlacedNode node) {
        return node.viewNode().sourceNode() instanceof Material material ? material.key()
                : ((Process) node.viewNode().sourceNode()).primaryOutput();
    }

    public static void draw(GuiGraphics graphics, CraftingPlanGraph graph, Layout layout, GraphViewLod lod,
                            boolean showAmounts, int selectedNodeId, Set<Integer> highlighted,
                            @Nullable Bounds viewport) {
        Int2IntOpenHashMap ordinals = new Int2IntOpenHashMap();
        for (var cycle : graph.cycles()) for (int nodeId : cycle.nodeIds()) ordinals.putIfAbsent(nodeId, cycle.ordinal());
        for (var edge : layout.edges()) {
            int color = highlighted.contains(edge.source()) && highlighted.contains(edge.target()) ? 0xFF62E9DB
                    : edge.cyclic() ? 0xFFBE94E8 : 0xFF658197;
            for (int i = 1; i < edge.points().size(); i++) {
                Point a = edge.points().get(i - 1);
                Point b = edge.points().get(i);
                if (visible(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.abs(a.x() - b.x()) + 2,
                        Math.abs(a.y() - b.y()) + 2, viewport)) line(graphics, a, b, color);
            }
            if (lod == GraphViewLod.FULL && edge.points().size() > 1) {
                // Layout edges encode demand; visible arrows show the opposite, actual material flow.
                Point end = edge.points().getFirst();
                Point previous = edge.points().get(1);
                int dx = (int) Math.signum(end.x() - previous.x());
                int dy = (int) Math.signum(end.y() - previous.y());
                for (int depth = 1; depth <= 4; depth++) {
                    int x = (int) end.x() - dx * depth;
                    int y = (int) end.y() - dy * depth;
                    if (dx == 0) graphics.hLine(x - depth / 2, x + depth / 2, y, color);
                    else graphics.vLine(x, y - depth / 2, y + depth / 2, color);
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
            int border = selectedNodeId == node.id() || highlighted.contains(node.id()) ? 0xFF62E9DB
                    : missing ? 0xFFF07883 : node.viewNode().cyclic() ? 0xFFBE94E8 : materialNode ? 0xFF52748C : 0xFFB69558;
            graphics.fill(x, y, x + w, y + h, border);
            if (lod == GraphViewLod.BLOCK) continue;
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, materialNode ? 0xFF142638 : 0xFF30291F);
            AEKey key = key(node);
            if (lod == GraphViewLod.FULL) {
                AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x + 6, y + 7, key);
                smallText(graphics, key.getDisplayName().getString(), x + 27, y + 5, w - 32, 0xFFE4EDF5);
                if (showAmounts) {
                    BigInteger amount = node.id() == graph.rootId() ? graph.header().requested()
                            : node.viewNode().sourceNode() instanceof Material material ? material.required().signum() > 0 ? material.required() : material.crafting()
                            : ((Process) node.viewNode().sourceNode()).executions();
                    smallText(graphics, (materialNode ? "" : "× ") + TrinityAmountFormatter.format(amount),
                            x + 27, y + 15, w - 32, missing ? 0xFFFFA5AE : 0xFFC4DCE8);
                }
                if (showAmounts && node.embeddedProcessId() != null) {
                    Process process = (Process) graph.node(node.embeddedProcessId());
                    smallText(graphics, "× " + TrinityAmountFormatter.format(process.executions()), x + 6, y + h - 9, w - 20, 0xFFBECDD8);
                }
                if (node.viewNode().cyclic()) {
                    int ordinal = ordinals.get(node.id());
                    smallText(graphics, ordinal > 0 ? "↻ " + ordinal : "↻", x + w - 20, y + h - 9, 18, 0xFFD8BDF6);
                } else {
                    smallText(graphics, node.viewNode().collapsed() ? "+" : "−", x + w - 9, y + h - 9, 8, border);
                }
            } else {
                graphics.fill(x + 6, y + 7, x + 22, y + 23, border);
                graphics.fill(x + 27, y + 8, x + w - 7, y + 11, 0xFF557080);
            }
        }
    }

    private static void line(GuiGraphics graphics, Point a, Point b, int color) {
        if (a.x() == b.x()) graphics.fill((int) a.x(), (int) Math.min(a.y(), b.y()), (int) a.x() + 1, (int) Math.max(a.y(), b.y()) + 1, color);
        else graphics.fill((int) Math.min(a.x(), b.x()), (int) a.y(), (int) Math.max(a.x(), b.x()) + 1, (int) a.y() + 1, color);
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
