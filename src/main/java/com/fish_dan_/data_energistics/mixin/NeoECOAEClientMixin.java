package com.fish_dan_.data_energistics.mixin;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.client.NeoECOAEClient", remap = false)
public class NeoECOAEClientMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_WARNINGS = 5;
    private static final AtomicInteger SAFE_RENDER_FAILURES = new AtomicInteger();
    private static final String FIXED_BLOCK_ENTITY_RENDERERS = "cn.dancingsnow.neoecoae.api.rendering.FixedBlockEntityRenderers";
    private static volatile Method fixedRendererMethod;
    private static volatile boolean fixedRendererLookupAttempted;

    @Inject(method = "onAddChunkGeometry", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$wrapAddChunkGeometry(AddSectionGeometryEvent event, CallbackInfo ci) {
        BlockPos sectionOrigin = event.getSectionOrigin();
        event.addRenderer(context -> {
            try {
                invokeFixedRenderers(context, sectionOrigin);
            } catch (RuntimeException exception) {
                int failureCount = SAFE_RENDER_FAILURES.incrementAndGet();
                if (failureCount <= MAX_WARNINGS) {
                    LOGGER.warn(
                            "Skipped NeoECOAE additional chunk geometry at {} after renderer failure #{}",
                            sectionOrigin,
                            failureCount,
                            exception);
                }
            }
        });
        ci.cancel();
    }

    private static void invokeFixedRenderers(Object context, BlockPos sectionOrigin) {
        Method renderMethod = getFixedRendererMethod(context);
        if (renderMethod == null) {
            return;
        }

        try {
            renderMethod.invoke(null, context, sectionOrigin);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new RuntimeException("Failed to invoke NeoECOAE fixed renderer hook", exception);
        }
    }

    private static Method getFixedRendererMethod(Object context) {
        Method renderMethod = fixedRendererMethod;
        if (renderMethod != null) {
            return renderMethod;
        }
        if (fixedRendererLookupAttempted) {
            return null;
        }

        fixedRendererLookupAttempted = true;
        try {
            Class<?> renderersClass = Class.forName(FIXED_BLOCK_ENTITY_RENDERERS);
            renderMethod = renderersClass.getMethod("render", context.getClass().getInterfaces()[0], BlockPos.class);
            fixedRendererMethod = renderMethod;
            return renderMethod;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("Failed to resolve NeoECOAE fixed renderer hook reflectively", exception);
            return null;
        }
    }
}
