package com.fish_dan_.data_energistics.client.integration;

import com.fish_dan_.data_energistics.integration.Ae2LtWirelessBridge;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.client.renderer.RenderType;

import org.jetbrains.annotations.Nullable;

public final class Ae2LtWirelessClientBridge {

    private static final String RENDER_TYPES_CLASS = "com.moakiee.ae2lt.client.Ae2ltRenderTypes";

    private static boolean renderInitialized;
    private static @Nullable RenderType faceSeeThroughRenderType;

    private Ae2LtWirelessClientBridge() {}

    public static @Nullable RenderType getFaceSeeThroughRenderType() {
        if (!Ae2LtWirelessBridge.isAvailable()) {
            return null;
        }
        if (!renderInitialized) {
            initializeRender();
        }
        return faceSeeThroughRenderType;
    }

    private static void initializeRender() {
        renderInitialized = true;
        Object result = ReflectionAccess.invokeStatic(RENDER_TYPES_CLASS, "getFaceSeeThrough", new Class<?>[0]);
        faceSeeThroughRenderType = result instanceof RenderType renderType ? renderType : null;
    }
}
