package com.fish_dan_.data_energistics.menu;

import appeng.api.upgrades.IUpgradeableObject;

public interface DataSolarPanelMenuHost extends IUpgradeableObject {

    boolean isOnline();

    boolean isDaytime();

    double getAECurrentPower();

    double getAEMaxPower();

    double getGeneratedPowerPerTick();

    boolean isRedstoneControlled();

    boolean setRedstoneControlled(boolean enabled);
}
