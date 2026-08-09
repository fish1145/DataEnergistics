package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.client.screen.machine.DataDistributionTowerScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataRipperScreen;

import net.minecraft.client.Minecraft;

public final class MenuClientRefreshHandler {

    private MenuClientRefreshHandler() {}

    public static void refreshDataRipperScreen() {
        if (Minecraft.getInstance().screen instanceof DataRipperScreen screen) {
            screen.refreshGui();
        }
    }

    public static void refreshDataDistributionTowerScreen() {
        if (Minecraft.getInstance().screen instanceof DataDistributionTowerScreen screen) {
            screen.refreshFromServer();
        }
    }
}
