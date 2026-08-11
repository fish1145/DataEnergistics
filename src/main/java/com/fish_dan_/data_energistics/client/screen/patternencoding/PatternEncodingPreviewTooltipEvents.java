package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** Keeps a floating pattern-provider preview from competing with tooltips from covered GUI layers. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class PatternEncodingPreviewTooltipEvents {

    private PatternEncodingPreviewTooltipEvents() {}

    /** Cancels a lower-layer tooltip while the cursor is over an active preview panel. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof PreviewLayerTooltipScreen previewLayer &&
                previewLayer.shouldSuppressUnderlyingTooltip(event.getX(), event.getY())) {
            event.setCanceled(true);
        }
    }
}
