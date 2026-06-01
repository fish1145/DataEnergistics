package com.fish_dan_.data_energistics.integration;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;

public final class Ae2WtLibCompat {

    private static final String CLIENT_COMPAT_CLASS = "com.fish_dan_.data_energistics.client.integration.Ae2WtLibClientCompat";

    private Ae2WtLibCompat() {}

    @SuppressWarnings("unchecked")
    public static <T> T maybeReplaceWirelessPatternEncodingScreen(Object currentScreen, boolean applyImmediately) {
        if (FMLEnvironment.dist != Dist.CLIENT || !ModFlags.isAe2WtLibLoaded() || currentScreen == null) {
            return null;
        }

        try {
            Method method = Class.forName(CLIENT_COMPAT_CLASS)
                    .getMethod("maybeReplaceWirelessPatternEncodingScreen", Object.class, boolean.class);
            return (T) method.invoke(null, currentScreen, applyImmediately);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
