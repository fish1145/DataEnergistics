package com.fish_dan_.data_energistics.client.screen.patternencoding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** Isolates native slot depth, then orders panel content below the carried item and tooltips. */
final class PatternEncodingPreviewLayers {

    static final float PANEL_Z = 240.0F;
    static final float DETAIL_OFFSET_Z = 48.0F;
    // Native tooltip adds 400; this places panel tooltips above carried-item decorations at 432.
    static final float TOOLTIP_OFFSET_Z = 100.0F;
    private static final float ITEM_CONTENT_Z = 20.0F;
    private static final float NATIVE_ITEM_Z = 150.0F;

    private PatternEncodingPreviewLayers() {}

    /**
     * Starts a composited panel region after the caller flushed native drawing. AE2 craftable marks and
     * pattern decorators can write above the panel's own Z band. Preserve their already drawn color, but
     * discard that depth only under this panel and its drag handle. The enclosing scissor is restored.
     */
    static void clearCoveredDepth(GuiGraphics graphics, List<Rect2i> regions) {
        if (regions.isEmpty()) return;
        Rect2i first = regions.getFirst();
        int left = first.getX();
        int top = first.getY();
        int right = left + first.getWidth();
        int bottom = top + first.getHeight();
        for (int index = 1; index < regions.size(); index++) {
            Rect2i region = regions.get(index);
            left = Math.min(left, region.getX());
            top = Math.min(top, region.getY());
            right = Math.max(right, region.getX() + region.getWidth());
            bottom = Math.max(bottom, region.getY() + region.getHeight());
        }
        graphics.enableScissor(left, top, right, bottom);
        try {
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        } finally {
            graphics.disableScissor();
        }
    }

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
