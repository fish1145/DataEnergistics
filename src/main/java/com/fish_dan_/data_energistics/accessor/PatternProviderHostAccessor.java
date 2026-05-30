package com.fish_dan_.data_energistics.accessor;

import appeng.api.upgrades.IUpgradeInventory;

public interface PatternProviderHostAccessor extends RedstoneTuningAwareHost {

    IUpgradeInventory dataEnergistics$getRedstoneTuningUpgrades();
}
