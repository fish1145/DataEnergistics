package com.fish_dan_.data_energistics.client.integration;

import com.fish_dan_.data_energistics.integration.Ae2LtWirelessBridge;

import net.minecraft.client.renderer.RenderType;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public final class Ae2LtWirelessClientBridge {

    private static final String RENDER_TYPES_CLASS = "com.moakiee.ae2lt.client.Ae2ltRenderTypes";

    private static boolean renderInitialized;
    private static @Nullable Method renderFaceSeeThroughMethod;

    private Ae2LtWirelessClientBridge() {}

    public static @Nullable RenderType getFaceSeeThroughRenderType() {
        if (!Ae2LtWirelessBridge.isAvailable()) {
            return null;
        }
        if (!renderInitialized) {
            initializeRender();
        }
        try {
            Object result = renderFaceSeeThroughMethod != null ? renderFaceSeeThroughMethod.invoke(null) : null;
            return result instanceof RenderType renderType ? renderType : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void initializeRender() {
        renderInitialized = true;
        try {
            Class<?> renderTypesClass = Class.forName(RENDER_TYPES_CLASS);
            renderFaceSeeThroughMethod = renderTypesClass.getMethod("getFaceSeeThrough");
        } catch (Exception ignored) {
            renderFaceSeeThroughMethod = null;
        }
    }
}
