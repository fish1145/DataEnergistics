package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class MenuClientRefresh {

    private MenuClientRefresh() {}

    public static void refreshDataRipperScreen() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        DataEnergisticsClientBridgeAccess.get().refreshDataRipperScreen();
    }

    public static void refreshDataDistributionTowerScreen() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        DataEnergisticsClientBridgeAccess.get().refreshDataDistributionTowerScreen();
    }
}
