package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** Places upload panels before native cursor/tooltips and suppresses hits from covered GUI layers. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class PatternEncodingPreviewRenderEvents {

    private PatternEncodingPreviewRenderEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        if (event.getContainerScreen() instanceof PatternEncodingPreviewLayerScreen previewLayer) {
            previewLayer.renderPreviewForeground(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    /** Cancels a lower-layer tooltip while the cursor is over an active preview panel. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof PatternEncodingPreviewLayerScreen previewLayer &&
                previewLayer.shouldSuppressUnderlyingTooltip(event.getX(), event.getY())) {
            event.setCanceled(true);
        }
    }
}
