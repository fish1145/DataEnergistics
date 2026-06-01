package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class Ae2WtLibCompat {

    private static final String CLIENT_COMPAT_CLASS = "com.fish_dan_.data_energistics.client.integration.Ae2WtLibClientCompat";

    private Ae2WtLibCompat() {}

    @SuppressWarnings("unchecked")
    public static <T> T maybeReplaceWirelessPatternEncodingScreen(Object currentScreen, boolean applyImmediately) {
        if (FMLEnvironment.dist != Dist.CLIENT || !ModFlags.isAe2WtLibLoaded() || currentScreen == null) {
            return null;
        }

        return (T) ReflectionAccess.invokeStatic(
                CLIENT_COMPAT_CLASS,
                "maybeReplaceWirelessPatternEncodingScreen",
                new Class<?>[] { Object.class, boolean.class },
                currentScreen,
                applyImmediately);
    }
}
