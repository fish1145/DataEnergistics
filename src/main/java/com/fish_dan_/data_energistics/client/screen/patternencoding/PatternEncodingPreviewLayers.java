package com.fish_dan_.data_energistics.client.screen.patternencoding;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Panel content stays above native slots (up to 200), below the carried item (382) and tooltips (400). */
final class PatternEncodingPreviewLayers {

    static final float PANEL_Z = 240.0F;
    static final float DETAIL_OFFSET_Z = 48.0F;
    // Native tooltip adds 400; this places panel tooltips above carried-item decorations at 432.
    static final float TOOLTIP_OFFSET_Z = 100.0F;
    private static final float ITEM_CONTENT_Z = 20.0F;
    private static final float NATIVE_ITEM_Z = 150.0F;

    private PatternEncodingPreviewLayers() {}

    /** Cancels renderItem's implicit depth so an icon cannot escape its owning panel's depth interval. */
    static void renderIcon(GuiGraphics graphics, ItemStack stack, int x, int y) {
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(0.0F, 0.0F, ITEM_CONTENT_Z - NATIVE_ITEM_Z);
            graphics.renderItem(stack, x, y);
        } finally {
            pose.popPose();
        }
    }
}
