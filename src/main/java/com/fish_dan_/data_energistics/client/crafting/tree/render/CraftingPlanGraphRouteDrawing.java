package com.fish_dan_.data_energistics.client.crafting.tree.render;

import com.fish_dan_.data_energistics.client.crafting.tree.render.CraftingPlanGraphDrawingFacts.RouteStyle;

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
}
