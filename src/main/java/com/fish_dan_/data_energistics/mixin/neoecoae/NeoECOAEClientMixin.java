package com.fish_dan_.data_energistics.mixin.neoecoae;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.client.NeoECOAEClient", remap = false)
public class NeoECOAEClientMixin {

    @Unique
    private static final Logger data_energistics$LOGGER = LogUtils.getLogger();
    @Unique
    private static final int MAX_WARNINGS = 5;
    @Unique
    private static final AtomicInteger SAFE_RENDER_FAILURES = new AtomicInteger();
    @Unique
    private static final String FIXED_BLOCK_ENTITY_RENDERERS = "cn.dancingsnow.neoecoae.api.rendering.FixedBlockEntityRenderers";
    @Unique
    private static volatile Method data_energistics$fixedRendererMethod;
    @Unique
    private static volatile boolean data_energistics$fixedRendererLookupAttempted;

    @Inject(method = "onAddChunkGeometry", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$wrapAddChunkGeometry(AddSectionGeometryEvent event, CallbackInfo ci) {
        BlockPos sectionOrigin = event.getSectionOrigin();
        event.addRenderer(context -> {
            try {
                data_energistics$invokeFixedRenderers(context, sectionOrigin);
            } catch (RuntimeException exception) {
                int failureCount = SAFE_RENDER_FAILURES.incrementAndGet();
                if (failureCount <= MAX_WARNINGS) {
                    data_energistics$LOGGER.warn(
                            "Skipped NeoECOAE additional chunk geometry at {} after renderer failure #{}",
                            sectionOrigin,
                            failureCount,
                            exception);
                }
            }
        });
        ci.cancel();
    }

    @Unique
    private static void data_energistics$invokeFixedRenderers(Object context, BlockPos sectionOrigin) {
        Method renderMethod = data_energistics$getFixedRendererMethod(context);
        if (renderMethod == null) {
            return;
        }

        try {
            renderMethod.invoke(null, context, sectionOrigin);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new RuntimeException("Failed to invoke NeoECOAE fixed renderer hook", exception);
        }
    }

    @Unique
    private static Method data_energistics$getFixedRendererMethod(Object context) {
        Method renderMethod = data_energistics$fixedRendererMethod;
        if (renderMethod != null) {
            return renderMethod;
        }
        if (data_energistics$fixedRendererLookupAttempted) {
            return null;
        }

        data_energistics$fixedRendererLookupAttempted = true;
        try {
            Class<?> renderersClass = Class.forName(FIXED_BLOCK_ENTITY_RENDERERS);
            renderMethod = renderersClass.getMethod("render", context.getClass().getInterfaces()[0], BlockPos.class);
            data_energistics$fixedRendererMethod = renderMethod;
            return renderMethod;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            data_energistics$LOGGER.warn("Failed to resolve NeoECOAE fixed renderer hook reflectively", exception);
            return null;
        }
    }
}
