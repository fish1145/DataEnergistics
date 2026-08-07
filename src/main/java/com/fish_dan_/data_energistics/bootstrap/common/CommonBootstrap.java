package com.fish_dan_.data_energistics.bootstrap.common;

import com.fish_dan_.data_energistics.common.CommonProxy;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreSyncAccessors;
import com.fish_dan_.data_energistics.configuration.runtime.ConfigurationBootstrap;
import com.fish_dan_.data_energistics.configuration.runtime.HolderFingerprintBridge;

import net.neoforged.bus.api.IEventBus;

public final class CommonBootstrap {

    private CommonBootstrap() {}

    public static void init(IEventBus modEventBus) {
        TrinityDataCoreSyncAccessors.init();
        HolderFingerprintBridge configurationReload = ConfigurationBootstrap.initialize();
        CommonProxy.init(modEventBus, configurationReload);
    }
}
