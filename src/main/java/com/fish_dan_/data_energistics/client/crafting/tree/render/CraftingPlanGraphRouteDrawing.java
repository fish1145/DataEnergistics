package com.fish_dan_.data_energistics.client.crafting.tree.render;

import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts.RouteStyle;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteCrossing;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Run;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.Segment;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.List;

/** Shared scale-aware route decisions for the screen renderer and fixed-density graph exports. */
public final class CraftingPlanGraphRouteDrawing {

    public static final float EXPORT_PIXEL_SCALE = 4F;
    public static final double STROKE_WIDTH = 1.5;
    public static final double HIGHLIGHT_WIDTH = 2;
    public static final double ARROW_SIZE = 5;

    private CraftingPlanGraphRouteDrawing() {}

    public static int bandCount(RouteStyle style, double length, float pixelScale) {
        if (style.cycles().size() <= 1) return 1;
        return Math.max(style.cycles().size(), Math.min(48,
                (int) Math.ceil(length / Math.max(24, 18 / pixelScale))));
    }

    public static boolean hasInteriorArrows(RouteStyle style, double length, float pixelScale) {
        return style.materialFlow() && length * pixelScale >= 48;
    }

    public static int interiorArrowCount(double length, float pixelScale) {
        return Math.max(1, Math.min(4, (int) (length * pixelScale / 160)));
    }

    /** Returns an index within the run, not a global segment id. */
    public static int segmentIndexAt(Run run, CraftingPlanRouteGeometry geometry, double distance) {
        int first = 0;
        int last = run.segmentIds().size() - 1;
        boolean horizontal = run.from().y() == run.to().y();
        while (first < last) {
            int middle = (first + last + 1) >>> 1;
            Segment segment = geometry.segments().get(run.segmentIds().getInt(middle));
            double offset = horizontal ? Math.abs(segment.from().x() - run.from().x()) : Math.abs(segment.from().y() - run.from().y());
            if (offset <= distance) first = middle;
            else last = middle - 1;
        }
        return first;
    }

    /** Tests the complete arrow footprint, including a tail that crosses a shared-membership boundary. */
    public static boolean blocksArrow(Run run, CraftingPlanRouteGeometry geometry, double tipDistance, double depth,
                                      Int2ObjectMap<? extends List<CraftingPlanRouteCrossing>> bridges,
                                      Int2ObjectMap<? extends List<CraftingPlanRouteCrossing>> underpasses,
                                      double styleScale) {
        double start = Math.max(0, tipDistance - HIGHLIGHT_WIDTH * styleScale / 2);
        double end = tipDistance + depth + HIGHLIGHT_WIDTH * styleScale / 2;
        int first = segmentIndexAt(run, geometry, start);
        int last = segmentIndexAt(run, geometry, end);
        boolean vertical = run.from().x() == run.to().x();
        double origin = vertical ? run.from().y() : run.from().x();
        double direction = Math.signum((vertical ? run.to().y() : run.to().x()) - origin);
        double a = origin + direction * start;
        double b = origin + direction * end;
        double low = Math.min(a, b);
        double high = Math.max(a, b);
        for (int index = first; index <= last; index++) {
            int segmentId = run.segmentIds().getInt(index);
            List<CraftingPlanRouteCrossing> crossings = (vertical ? bridges : underpasses).get(segmentId);
            if (crossings == null) continue;
            int left = 0;
            int right = crossings.size();
            while (left < right) {
                int middle = (left + right) >>> 1;
                if (crossingBaseCoordinate(crossings.get(middle), vertical) < low - 2 * CraftingPlanRouteCrossing.MAX_RADIUS * styleScale) left = middle + 1;
                else right = middle;
            }
            for (int crossingIndex = left; crossingIndex < crossings.size(); crossingIndex++) {
                CraftingPlanRouteCrossing crossing = crossings.get(crossingIndex);
                if (crossingBaseCoordinate(crossing, vertical) > high + 2 * CraftingPlanRouteCrossing.MAX_RADIUS * styleScale) break;
                double center = crossingCoordinate(crossing, vertical, styleScale);
                double radius = styleScale * (vertical ? crossing.radius() : crossing.gapHalfWidth());
                if (center + radius >= low && center - radius <= high) return true;
            }
        }
        return false;
    }

    private static double crossingCoordinate(CraftingPlanRouteCrossing crossing, boolean vertical, double styleScale) {
        return vertical ? crossing.y() : crossing.x() + crossing.bend() * styleScale;
    }

    private static double crossingBaseCoordinate(CraftingPlanRouteCrossing crossing, boolean vertical) {
        return vertical ? crossing.y() : crossing.x();
    }
}
