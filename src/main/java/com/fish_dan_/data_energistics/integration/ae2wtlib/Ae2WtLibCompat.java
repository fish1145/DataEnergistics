package com.fish_dan_.data_energistics.integration.ae2wtlib;

import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;
import com.fish_dan_.data_energistics.integration.ModFlags;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class Ae2WtLibCompat {

    private Ae2WtLibCompat() {}

    @SuppressWarnings("unchecked")
    public static <T> T maybeReplaceWirelessPatternEncodingScreen(Object currentScreen, boolean applyImmediately) {
        if (FMLEnvironment.dist != Dist.CLIENT || currentScreen == null || !ModFlags.isAe2WtLibWirelessPatternEncodingSupportLoaded()) {
            return null;
        }

        return (T) DataEnergisticsClientBridgeAccess.get()
                .maybeReplaceWirelessPatternEncodingScreen(currentScreen, applyImmediately);
    }
}
