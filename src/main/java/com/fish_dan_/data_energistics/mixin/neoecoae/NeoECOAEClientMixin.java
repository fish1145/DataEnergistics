package com.fish_dan_.data_energistics.mixin.neoecoae;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;

import cn.dancingsnow.neoecoae.api.rendering.FixedBlockEntityRenderers;
import cn.dancingsnow.neoecoae.client.NeoECOAEClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.atomic.AtomicInteger;

@Pseudo
@Mixin(value = NeoECOAEClient.class, remap = false)
public class NeoECOAEClientMixin {

    @Unique
    private static final int data_energistics$MAX_WARNINGS = 5;
    @Unique
    private static final AtomicInteger data_energistics$SAFE_RENDER_FAILURES = new AtomicInteger();

    @Redirect(
              method = "lambda$onAddChunkGeometry$0",
              at = @At(
                       value = "INVOKE",
                       target = "Lcn/dancingsnow/neoecoae/api/rendering/FixedBlockEntityRenderers;render(Lnet/neoforged/neoforge/client/event/AddSectionGeometryEvent$SectionRenderingContext;Lnet/minecraft/core/BlockPos;)V"),
              remap = false)
    private static void dataEnergistics$wrapFixedBlockEntityRenderers(
                                                                      AddSectionGeometryEvent.SectionRenderingContext context, BlockPos sectionOrigin) {
        try {
            FixedBlockEntityRenderers.render(context, sectionOrigin);
        } catch (RuntimeException exception) {
            int failureCount = data_energistics$SAFE_RENDER_FAILURES.incrementAndGet();
            if (failureCount <= data_energistics$MAX_WARNINGS) {
                Data_Energistics.LOGGER.warn(
                        "Skipped NeoECOAE fixed block entity renderers at {} after renderer failure #{}",
                        sectionOrigin,
                        failureCount,
                        exception);
            }
        }
    }
}
