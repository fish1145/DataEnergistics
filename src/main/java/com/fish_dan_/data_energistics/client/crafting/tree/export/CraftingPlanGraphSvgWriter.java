package com.fish_dan_.data_energistics.client.crafting.tree.export;

import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts.CycleMark;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts.RouteStyle;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphPalette;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphRenderer;
import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphRouteDrawing;
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
import net.minecraft.client.gui.Font;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.util.List;

/** Snapshots client font measurements once; all geometry and XML streaming thereafter run on the IO worker. */
final class CraftingPlanGraphSvgWriter {

    private static final double TEXT_SCALE = 0.65;
    private final Layout layout;
    private final CraftingPlanGraphDrawingFacts facts;
    private final Object2ObjectOpenHashMap<CraftingPlanRouteGroup, RouteStyle> routeStyles = new Object2ObjectOpenHashMap<>();
    private final List<AEKey> keys = new ObjectArrayList<>();
    private final List<NodeDrawing> nodes = new ObjectArrayList<>();

    CraftingPlanGraphSvgWriter(CraftingPlanGraph graph, Layout layout, boolean showAmounts) {
        Bounds bounds = layout.bounds();
        if (!Double.isFinite(bounds.x()) || !Double.isFinite(bounds.y()) || !Double.isFinite(bounds.width()) || !Double.isFinite(bounds.height()) || bounds.width() <= 0 || bounds.height() <= 0) {
            throw new IllegalArgumentException("The crafting graph must have finite, positive export bounds");
        }
        this.layout = layout;
        this.facts = new CraftingPlanGraphDrawingFacts(graph);
        Object2IntMap<AEKey> iconIds = new Object2IntOpenHashMap<>();
        iconIds.defaultReturnValue(-1);
        Font font = Minecraft.getInstance().font;
        for (PlacedNode node : layout.nodes()) {
            AEKey key = CraftingPlanGraphRenderer.key(node);
            int icon = iconIds.getInt(key);
            if (icon == -1) {
                icon = this.keys.size();
                iconIds.put(key, icon);
                this.keys.add(key);
            }
            boolean materialNode = node.viewNode().sourceNode() instanceof Material;
            boolean missing = materialNode && (((Material) node.viewNode().sourceNode()).missing().signum() > 0 || ((Material) node.viewNode().sourceNode()).unresolved().signum() > 0);
            List<CycleMark> cycles = this.facts.node(node.id());
            int cycleColor = cycles.isEmpty() ? CraftingPlanGraphPalette.FRAME : cycles.getFirst().color();
            int border = missing ? CraftingPlanGraphPalette.MISSING : cycleColor;
            int surface = materialNode ? CraftingPlanGraphPalette.MATERIAL : CraftingPlanGraphPalette.PROCESS;
            int state = missing ? CraftingPlanGraphPalette.MISSING : materialNode && ((Material) node.viewNode().sourceNode()).stored().signum() > 0 ? CraftingPlanGraphPalette.STORED : cycles.isEmpty() ? CraftingPlanGraphPalette.ACCENT : cycleColor;
            String name = key.getDisplayName().getString();
            List<TextDrawing> labels = new ObjectArrayList<>();
            text(labels, font, name, node.x() + 27, node.y() + 5, node.width() - 32, CraftingPlanGraphPalette.TEXT);
            boolean embeddedCount = showAmounts && node.embeddedProcessId() != null;
            double badgeWidth = embeddedCount ? Math.floor((node.width() - 18) / 2) : node.width() - 12;
            if (showAmounts) {
                BigInteger amount = node.id() == graph.rootId() ? graph.header().requested() : node.viewNode().sourceNode() instanceof Material material ? material.required().signum() > 0 ? material.required() : material.crafting() : ((Process) node.viewNode().sourceNode()).executions();
                text(labels, font, (materialNode ? "" : "× ") + TrinityAmountFormatter.format(amount),
                        node.x() + 27, node.y() + 15, node.width() - 32,
                        missing ? CraftingPlanGraphPalette.MISSING : CraftingPlanGraphPalette.ACCENT);
            }
            if (embeddedCount) {
                Process process = (Process) graph.node(node.embeddedProcessId());
                text(labels, font, "× " + TrinityAmountFormatter.format(process.executions()), node.x() + 6,
                        node.y() + node.height() - 9, cycles.isEmpty() ? node.width() - 20 : node.width() - badgeWidth - 18,
                        CraftingPlanGraphPalette.MUTED_TEXT);
            }
            if (!cycles.isEmpty()) {
                text(labels, font, this.facts.label(node.id()), node.x() + node.width() - badgeWidth - 6,
                        node.y() + node.height() - 9, badgeWidth, CraftingPlanGraphPalette.TEXT);
            } else if (node.viewNode().expandable()) {
                text(labels, font, node.viewNode().collapsed() ? "+" : "−", node.x() + node.width() - 9,
                        node.y() + node.height() - 9, 8, border);
            }
            this.nodes.add(new NodeDrawing(node, icon, name, border, surface, state, labels));
        }
    }

    /** Owned by this export and never mutated after construction; icon ids are indices in this list. */
    List<AEKey> keys() {
        return this.keys;
    }

    void begin(Writer output) throws IOException {
        Bounds bounds = this.layout.bounds();
        output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        output.write("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"" + bounds.width() + "\" height=\"" + bounds.height() + "\" viewBox=\"" + bounds.x() + " " + bounds.y() + " " + bounds.width() + " " + bounds.height() + "\">\n");
        output.write("<desc>Crafting plan: vector nodes, connections and text; embedded native AEKey raster icons.</desc>\n<defs>\n");
    }

    void finish(Writer output) throws IOException {
        output.write("</defs>\n<g fill=\"none\" stroke-width=\"" + CraftingPlanGraphRouteDrawing.STROKE_WIDTH + "\" stroke-linecap=\"butt\" stroke-linejoin=\"round\">\n");
        CraftingPlanRouteGeometry geometry = this.layout.geometry();
        for (Run run : geometry.runs()) {
            run(output, run, style(run.group()));
        }
        for (int segmentId : geometry.terminalSegments()) {
            Segment segment = geometry.segments().get(segmentId);
            RouteStyle style = style(segment.group());
            // Dependency routes point towards inputs, so physical material arrows run in reverse.
            arrow(output, segment.to(), segment.from(), style.color(0));
        }
        output.write("</g>\n");
        for (NodeDrawing drawing : this.nodes) {
            PlacedNode node = drawing.node();
            output.write("<g><title>" + escape(drawing.name()) + "</title>\n");
            rect(output, node.x(), node.y(), node.width(), node.height(), drawing.border());
            rect(output, node.x() + 1, node.y() + 1, node.width() - 2, node.height() - 2, drawing.surface());
            rect(output, node.x(), node.y(), 2, node.height(), drawing.state());
            List<CycleMark> cycles = this.facts.node(node.id());
            for (int i = 0; i < cycles.size(); i++) {
                rect(output, node.x() + node.width() * i / cycles.size(), node.y() + node.height() - 2,
                        node.width() / cycles.size(), 2, cycles.get(i).color());
            }
            output.write("<use xlink:href=\"#icon-" + drawing.icon() + "\" x=\"" + (node.x() + 6) + "\" y=\"" + (node.y() + 7) + "\"/>\n");
            for (TextDrawing label : drawing.labels()) {
                output.write("<text x=\"" + label.x() + "\" y=\"" + (label.y() + 5.5) + "\" font-family=\"sans-serif\" font-size=\"6.5\" fill=\"" + color(label.color()) + "\" textLength=\"" + label.width() + "\" lengthAdjust=\"spacingAndGlyphs\" xml:space=\"preserve\">" + escape(label.value()) + "</text>\n");
            }
            output.write("</g>\n");
        }
        output.write("</svg>\n");
    }

    private RouteStyle style(CraftingPlanRouteGroup group) {
        return this.routeStyles.computeIfAbsent(group, this.facts::route);
    }

    private static void text(List<TextDrawing> labels, Font font, String value, double x, double y,
                             double maximumWidth, int color) {
        String visible = font.plainSubstrByWidth(value, (int) (maximumWidth / TEXT_SCALE));
        if (!visible.isEmpty()) labels.add(new TextDrawing(visible, x, y, font.width(visible) * TEXT_SCALE, color));
    }

    private static void run(Writer output, Run run, RouteStyle style) throws IOException {
        double length = Math.hypot(run.to().x() - run.from().x(), run.to().y() - run.from().y());
        int bands = CraftingPlanGraphRouteDrawing.bandCount(style, length, CraftingPlanGraphRouteDrawing.EXPORT_PIXEL_SCALE);
        // A continuous underlay prevents antialiasing seams where differently colored cycle bands meet.
        if (bands == 1) {
            line(output, run.from(), run.to(), style.lineColor(), style.lineOpacity());
        } else {
            line(output, run.from(), run.to(), style.color(0), 1);
            for (int band = 0; band < bands; band++) {
                line(output, interpolate(run.from(), run.to(), band / (double) bands),
                        interpolate(run.from(), run.to(), (band + 1D) / bands), style.color(band), 1);
            }
        }
        if (!CraftingPlanGraphRouteDrawing.hasInteriorArrows(style, length, CraftingPlanGraphRouteDrawing.EXPORT_PIXEL_SCALE)) return;
        int arrows = CraftingPlanGraphRouteDrawing.interiorArrowCount(length, CraftingPlanGraphRouteDrawing.EXPORT_PIXEL_SCALE);
        for (int i = 0; i < arrows; i++) {
            double fraction = (i + 1D) / (arrows + 1);
            arrow(output, interpolate(run.from(), run.to(), Math.min(1, fraction + CraftingPlanGraphRouteDrawing.ARROW_SIZE / length)),
                    interpolate(run.from(), run.to(), fraction), style.color(Math.min(bands - 1, (int) (fraction * bands))));
        }
    }

    private static void arrow(Writer output, Point from, Point tip, int color) throws IOException {
        double length = Math.hypot(tip.x() - from.x(), tip.y() - from.y());
        double dx = (tip.x() - from.x()) / length;
        double dy = (tip.y() - from.y()) / length;
        double depth = Math.min(CraftingPlanGraphRouteDrawing.ARROW_SIZE, length);
        double x = tip.x() - dx * depth;
        double y = tip.y() - dy * depth;
        line(output, new Point(x + dy * depth * 0.55, y - dx * depth * 0.55), tip, color, 1);
        line(output, new Point(x - dy * depth * 0.55, y + dx * depth * 0.55), tip, color, 1);
    }

    private static Point interpolate(Point a, Point b, double fraction) {
        return new Point(a.x() + (b.x() - a.x()) * fraction, a.y() + (b.y() - a.y()) * fraction);
    }

    private static void line(Writer output, Point a, Point b, int color, double opacity) throws IOException {
        output.write("<path d=\"M " + a.x() + " " + a.y() + " L " + b.x() + " " + b.y() + "\" stroke=\"" + color(color) + "\" stroke-opacity=\"" + opacity + "\"/>\n");
    }

    private static void rect(Writer output, double x, double y, double width, double height, int color) throws IOException {
        output.write("<rect x=\"" + x + "\" y=\"" + y + "\" width=\"" + width + "\" height=\"" + height + "\" fill=\"" + color(color) + "\"/>\n");
    }

    private static String color(int argb) {
        return "#" + Integer.toHexString((argb & 0xFFFFFF) | 0x1000000).substring(1);
    }

    /** XML 1.0 escapes both markup and invalid controls without splitting supplementary Unicode characters. */
    private static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '"' -> result.append("&quot;");
                case '\'' -> result.append("&apos;");
                default -> {
                    if (codePoint == 9 || codePoint == 10 || codePoint == 13 || codePoint >= 0x20 && codePoint <= 0xD7FF || codePoint >= 0xE000 && codePoint <= 0xFFFD || codePoint >= 0x10000 && codePoint <= 0x10FFFF) {
                        result.appendCodePoint(codePoint);
                    } else {
                        result.append('\uFFFD');
                    }
                }
            }
        });
        return result.toString();
    }

    private record NodeDrawing(PlacedNode node, int icon, String name, int border, int surface, int state,
                               List<TextDrawing> labels) {}

    private record TextDrawing(String value, double x, double y, double width, int color) {}
}
