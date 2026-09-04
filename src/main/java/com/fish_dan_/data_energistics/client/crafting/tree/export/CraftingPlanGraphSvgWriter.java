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
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.RoutedCurve;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteCrossing;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteCrossing.Underpass;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Run;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Segment;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGroup;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGroup.Style;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

/** Snapshots client font measurements once; all geometry and XML streaming thereafter run on the IO worker. */
final class CraftingPlanGraphSvgWriter {

    private static final double TEXT_SCALE = 0.65;
    private final Layout layout;
    private final CraftingPlanGraphDrawingFacts facts;
    private final Object2ObjectOpenHashMap<Style, RouteStyle> routeStyles = new Object2ObjectOpenHashMap<>();
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
        var bridges = new Int2ObjectOpenHashMap<ObjectList<CraftingPlanRouteCrossing>>();
        var underpasses = new Int2ObjectOpenHashMap<ObjectList<Underpass>>();
        double maximumBridgeRadius = 0;
        for (CraftingPlanRouteCrossing crossing : geometry.crossings()) {
            bridges.computeIfAbsent(crossing.bridgeSegmentId(), unused -> new ObjectArrayList<>()).add(crossing);
            maximumBridgeRadius = Math.max(maximumBridgeRadius, crossing.radius());
            for (Underpass underpass : crossing.underpasses()) {
                underpasses.computeIfAbsent(underpass.segmentId(), unused -> new ObjectArrayList<>()).add(underpass);
            }
        }
        bridges.values().forEach(crossings -> crossings.sort(Comparator.comparingDouble(CraftingPlanRouteCrossing::y)));
        underpasses.values().forEach(gaps -> gaps.sort(Comparator.comparingDouble(Underpass::x)));
        for (RoutedCurve curve : this.layout.curves()) curve(output, curve, style(curve.group()));
        for (Run run : geometry.runs()) {
            boolean crossed = false;
            for (int segmentId : run.segmentIds()) if (bridges.containsKey(segmentId) || underpasses.containsKey(segmentId)) {
                crossed = true;
                break;
            }
            if (crossed) crossedRun(output, geometry, run, style(run.group()), bridges, underpasses,
                    maximumBridgeRadius);
            else run(output, run, style(run.group()));
        }
        for (int segmentId : geometry.terminalSegments()) {
            Segment segment = geometry.segments().get(segmentId);
            RouteStyle style = style(segment.group());
            if (blockedArrow(segmentId, bridges, underpasses, segment.from().x(), segment.from().y(), CraftingPlanGraphRouteDrawing.ARROW_SIZE)) continue;
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
        return this.routeStyles.computeIfAbsent(group.style(), this.facts::route);
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

    private static void curve(Writer output, RoutedCurve curve, RouteStyle style) throws IOException {
        double length = curveLength(curve);
        int bands = CraftingPlanGraphRouteDrawing.bandCount(style, length,
                CraftingPlanGraphRouteDrawing.EXPORT_PIXEL_SCALE);
        if (bands == 1) cubicPath(output, new SvgCurve(curve.from(), curve.firstControl(),
                curve.secondControl(), curve.to()), style.lineColor(), style.lineOpacity());
        else for (int band = 0; band < bands; band++) {
            cubicPath(output, subCurve(curve, band / (double) bands, (band + 1D) / bands),
                    style.color(band), 1);
        }
        if (!style.materialFlow()) return;
        double delta = Math.min(0.08, CraftingPlanGraphRouteDrawing.ARROW_SIZE / Math.max(length,
                CraftingPlanGraphRouteDrawing.ARROW_SIZE));
        arrow(output, cubic(curve, delta), curve.from(), style.color(0));
        if (!CraftingPlanGraphRouteDrawing.hasInteriorArrows(style, length,
                CraftingPlanGraphRouteDrawing.EXPORT_PIXEL_SCALE))
            return;
        int arrows = CraftingPlanGraphRouteDrawing.interiorArrowCount(length,
                CraftingPlanGraphRouteDrawing.EXPORT_PIXEL_SCALE);
        for (int index = 0; index < arrows; index++) {
            double fraction = (index + 1D) / (arrows + 1);
            arrow(output, cubic(curve, Math.min(1, fraction + delta)), cubic(curve, fraction),
                    style.color(Math.min(bands - 1, (int) (fraction * bands))));
        }
    }

    private static SvgCurve subCurve(RoutedCurve curve, double start, double end) {
        SvgSplit endSplit = split(new SvgCurve(curve.from(), curve.firstControl(), curve.secondControl(), curve.to()), end);
        if (start == 0) return endSplit.left();
        return split(endSplit.left(), start / end).right();
    }

    private static SvgSplit split(SvgCurve curve, double fraction) {
        Point a = interpolate(curve.from(), curve.firstControl(), fraction);
        Point b = interpolate(curve.firstControl(), curve.secondControl(), fraction);
        Point c = interpolate(curve.secondControl(), curve.to(), fraction);
        Point d = interpolate(a, b, fraction);
        Point e = interpolate(b, c, fraction);
        Point middle = interpolate(d, e, fraction);
        return new SvgSplit(new SvgCurve(curve.from(), a, d, middle),
                new SvgCurve(middle, e, c, curve.to()));
    }

    private static void cubicPath(Writer output, SvgCurve curve, int color, double opacity) throws IOException {
        output.write("<path d=\"M " + curve.from().x() + " " + curve.from().y() + " C " + curve.firstControl().x() + " " + curve.firstControl().y() + " " + curve.secondControl().x() + " " + curve.secondControl().y() + " " + curve.to().x() + " " + curve.to().y() + "\" stroke=\"" + color(color) + "\" stroke-opacity=\"" + opacity + "\"/>\n");
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
        return new Point(inverse * inverse * inverse * curve.from().x() + 3 * inverse * inverse * fraction * curve.firstControl().x() + 3 * inverse * fraction * fraction * curve.secondControl().x() + fraction * fraction * fraction * curve.to().x(),
                inverse * inverse * inverse * curve.from().y() + 3 * inverse * inverse * fraction * curve.firstControl().y() + 3 * inverse * fraction * fraction * curve.secondControl().y() + fraction * fraction * fraction * curve.to().y());
    }

    private static void crossedRun(Writer output, CraftingPlanRouteGeometry geometry, Run run, RouteStyle style,
                                   Int2ObjectOpenHashMap<ObjectList<CraftingPlanRouteCrossing>> bridges,
                                   Int2ObjectOpenHashMap<ObjectList<Underpass>> underpasses,
                                   double maximumBridgeRadius) throws IOException {
        double length = Math.hypot(run.to().x() - run.from().x(), run.to().y() - run.from().y());
        int bands = CraftingPlanGraphRouteDrawing.bandCount(style, length, CraftingPlanGraphRouteDrawing.EXPORT_PIXEL_SCALE);
        for (int segmentId : run.segmentIds()) {
            Segment segment = geometry.segments().get(segmentId);
            ObjectList<CraftingPlanRouteCrossing> bridge = bridges.get(segmentId);
            ObjectList<Underpass> underpass = underpasses.get(segmentId);
            if (bridge != null) {
                bridges(output, segment, bridge, run, style, length, bands);
            } else if (underpass != null) {
                underpasses(output, segment, underpass, run, style, length, bands);
            } else {
                straightPiece(output, segment.from(), segment.to(), run, style, length, bands);
            }
        }
        if (!CraftingPlanGraphRouteDrawing.hasInteriorArrows(style, length, CraftingPlanGraphRouteDrawing.EXPORT_PIXEL_SCALE)) return;
        int arrows = CraftingPlanGraphRouteDrawing.interiorArrowCount(length, CraftingPlanGraphRouteDrawing.EXPORT_PIXEL_SCALE);
        double dx = run.to().x() - run.from().x();
        double dy = run.to().y() - run.from().y();
        for (int index = 0; index < arrows; index++) {
            double fraction = (index + 1D) / (arrows + 1);
            double x = run.from().x() + dx * fraction;
            double y = run.from().y() + dy * fraction;
            double depth = Math.min(CraftingPlanGraphRouteDrawing.ARROW_SIZE, length * (1 - fraction));
            if (CraftingPlanGraphRouteDrawing.blocksArrow(run, geometry, fraction * length, depth,
                    bridges, underpasses, 1, maximumBridgeRadius))
                continue;
            arrow(output, new Point(x + dx / length * depth,
                    y + dy / length * depth), new Point(x, y),
                    style.color(Math.min(bands - 1, (int) (fraction * bands))));
        }
    }

    private static boolean blockedArrow(int segmentId,
                                        Int2ObjectOpenHashMap<ObjectList<CraftingPlanRouteCrossing>> bridges,
                                        Int2ObjectOpenHashMap<ObjectList<Underpass>> underpasses,
                                        double x, double y, double size) {
        ObjectList<CraftingPlanRouteCrossing> bridge = bridges.get(segmentId);
        if (bridge != null) for (CraftingPlanRouteCrossing crossing : bridge) if (Math.abs(y - crossing.y()) <= crossing.radius() + size) return true;
        ObjectList<Underpass> underpass = underpasses.get(segmentId);
        if (underpass != null) for (Underpass gap : underpass) if (Math.abs(x - gap.x()) <= gap.gapHalfWidth() + size) return true;
        return false;
    }

    private static void underpasses(Writer output, Segment segment, ObjectList<Underpass> underpasses,
                                    Run run, RouteStyle style, double length, int bands) throws IOException {
        boolean forward = segment.to().x() > segment.from().x();
        Point cursor = segment.from();
        for (int index = forward ? 0 : underpasses.size() - 1; index >= 0 && index < underpasses.size(); index += forward ? 1 : -1) {
            Underpass underpass = underpasses.get(index);
            Point entry = new Point(underpass.x() + (forward ? -underpass.gapHalfWidth() : underpass.gapHalfWidth()), cursor.y());
            Point exit = new Point(underpass.x() + (forward ? underpass.gapHalfWidth() : -underpass.gapHalfWidth()), cursor.y());
            straightPiece(output, cursor, entry, run, style, length, bands);
            cursor = exit;
        }
        straightPiece(output, cursor, segment.to(), run, style, length, bands);
    }

    private static void bridges(Writer output, Segment segment, ObjectList<CraftingPlanRouteCrossing> crossings,
                                Run run, RouteStyle style, double length, int bands) throws IOException {
        boolean downward = segment.to().y() > segment.from().y();
        Point cursor = segment.from();
        for (int index = downward ? 0 : crossings.size() - 1; index >= 0 && index < crossings.size(); index += downward ? 1 : -1) {
            CraftingPlanRouteCrossing crossing = crossings.get(index);
            Point entry = new Point(crossing.x(), crossing.y() + (downward ? -crossing.radius() : crossing.radius()));
            Point exit = new Point(crossing.x(), crossing.y() + (downward ? crossing.radius() : -crossing.radius()));
            straightPiece(output, cursor, entry, run, style, length, bands);
            Point middle = new Point(crossing.x() + crossing.bend(), crossing.y());
            if (crossing.radius() > CraftingPlanRouteCrossing.MAX_RADIUS) {
                Point upper = new Point(middle.x(), entry.y());
                Point lower = new Point(middle.x(), exit.y());
                bridgeLeg(output, entry, upper, run, style, length, bands);
                straightPiece(output, upper, lower, run, style, length, bands);
                bridgeLeg(output, lower, exit, run, style, length, bands);
                cursor = exit;
                continue;
            }
            curvePiece(output, entry, new Point(middle.x(), entry.y()), middle, run, style, length, bands);
            curvePiece(output, middle, new Point(middle.x(), exit.y()), exit, run, style, length, bands);
            cursor = exit;
        }
        straightPiece(output, cursor, segment.to(), run, style, length, bands);
    }

    private static void bridgeLeg(Writer output, Point from, Point to, Run run, RouteStyle style,
                                  double length, int bands) throws IOException {
        int color = bands == 1 ? style.lineColor() : style.color(Math.min(bands - 1, (int) (distance(run, from) / length * bands)));
        line(output, from, to, color, bands == 1 ? style.lineOpacity() : 1);
    }

    private static void straightPiece(Writer output, Point from, Point to, Run run, RouteStyle style,
                                      double length, int bands) throws IOException {
        line(output, from, to, style.lineColor(), style.lineOpacity());
        if (bands == 1) return;
        double start = distance(run, from);
        double end = distance(run, to);
        double bandLength = length / bands;
        for (int band = Math.max(0, (int) Math.floor(start / bandLength)); band < Math.min(bands, (int) Math.ceil(end / bandLength)); band++) {
            double fromFraction = (Math.max(start, band * bandLength) - start) / (end - start);
            double toFraction = (Math.min(end, (band + 1D) * bandLength) - start) / (end - start);
            line(output, interpolate(from, to, fromFraction), interpolate(from, to, toFraction), style.color(band), 1);
        }
    }

    /** Split the actual quadratic curve at color boundaries, retaining the straight run's metadata phase. */
    private static void curvePiece(Writer output, Point from, Point control, Point to, Run run, RouteStyle style,
                                   double length, int bands) throws IOException {
        quadratic(output, from, control, to, style.lineColor(), style.lineOpacity());
        if (bands == 1) return;
        double start = distance(run, from);
        double end = distance(run, to);
        double bandLength = length / bands;
        for (int band = Math.max(0, (int) Math.floor(start / bandLength)); band < Math.min(bands, (int) Math.ceil(end / bandLength)); band++) {
            double fromFraction = (Math.max(start, band * bandLength) - start) / (end - start);
            double toFraction = (Math.min(end, (band + 1D) * bandLength) - start) / (end - start);
            double t0 = control.y() == from.y() ? Math.sqrt(fromFraction) : 1 - Math.sqrt(1 - fromFraction);
            double t1 = control.y() == from.y() ? Math.sqrt(toFraction) : 1 - Math.sqrt(1 - toFraction);
            Point a = quadraticPoint(from, control, to, t0);
            Point b = quadraticPoint(from, control, to, t1);
            Point c = new Point(a.x() + (t1 - t0) * ((1 - t0) * (control.x() - from.x()) + t0 * (to.x() - control.x())),
                    a.y() + (t1 - t0) * ((1 - t0) * (control.y() - from.y()) + t0 * (to.y() - control.y())));
            quadratic(output, a, c, b, style.color(band), 1);
        }
    }

    private static double distance(Run run, Point point) {
        return run.from().y() == run.to().y() ? Math.abs(point.x() - run.from().x()) : Math.abs(point.y() - run.from().y());
    }

    private static Point quadraticPoint(Point from, Point control, Point to, double t) {
        double inverse = 1 - t;
        return new Point(inverse * inverse * from.x() + 2 * inverse * t * control.x() + t * t * to.x(),
                inverse * inverse * from.y() + 2 * inverse * t * control.y() + t * t * to.y());
    }

    private static void quadratic(Writer output, Point from, Point control, Point to, int color, double opacity) throws IOException {
        output.write("<path d=\"M " + from.x() + " " + from.y() + " Q " + control.x() + " " + control.y() + " " + to.x() + " " + to.y() + "\" stroke=\"" + color(color) + "\" stroke-opacity=\"" + opacity + "\"/>\n");
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

    private record SvgCurve(Point from, Point firstControl, Point secondControl, Point to) {}

    private record SvgSplit(SvgCurve left, SvgCurve right) {}
}
