package com.fish_dan_.data_energistics.menu.common;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class MenuClientRefresh {

    private static final String HANDLER_CLASS = "com.fish_dan_.data_energistics.client.screen.MenuClientRefreshHandler";

    private MenuClientRefresh() {
    }

    public static void refreshDataRipperScreen() {
        invokeClientRefresh("refreshDataRipperScreen");
    }

    public static void refreshDataDistributionTowerScreen() {
        invokeClientRefresh("refreshDataDistributionTowerScreen");
    }

    private static void invokeClientRefresh(String methodName) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        try {
            Class.forName(HANDLER_CLASS).getMethod(methodName).invoke(null);
        } catch (ReflectiveOperationException ignored) {
            // Client-only GUI refresh is best-effort; sync data remains authoritative.
        }
    }
}
