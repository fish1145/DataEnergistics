package com.fish_dan_.data_energistics.client.screen.patternencoding;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** Places upload panels before native cursor/tooltips and suppresses hits from covered GUI layers. */
@OnlyIn(Dist.CLIENT)
public final class PatternEncodingPreviewRenderEvents {

    private PatternEncodingPreviewRenderEvents() {}

    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        if (event.getContainerScreen() instanceof PatternEncodingPreviewLayerScreen previewLayer) {
            previewLayer.renderPreviewForeground(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    /** Cancels a lower-layer tooltip while the cursor is over an active preview panel. */
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof PatternEncodingPreviewLayerScreen previewLayer &&
                previewLayer.shouldSuppressUnderlyingTooltip(event.getX(), event.getY())) {
            event.setCanceled(true);
        }
    }
}
