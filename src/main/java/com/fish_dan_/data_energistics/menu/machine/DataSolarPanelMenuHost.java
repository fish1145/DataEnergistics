package com.fish_dan_.data_energistics.menu.machine;

import com.fish_dan_.data_energistics.common.solar.energy.SolarEnergyPool;

import appeng.api.upgrades.IUpgradeableObject;

public interface DataSolarPanelMenuHost extends IUpgradeableObject {

    boolean isOnline();

    boolean isDaytime();

    double getAECurrentPower();

    double getAEMaxPower();

    /** Server-thread display snapshot; block arrays override this, while cable-mounted parts stay independent. */
    default SolarEnergyPool.Snapshot getEnergyStorageSnapshot() {
        return new SolarEnergyPool.Snapshot(getAECurrentPower(), getAEMaxPower());
    }

    double getGeneratedPowerPerTick();

    boolean isRedstoneControlled();

    boolean setRedstoneControlled(boolean enabled);
}
