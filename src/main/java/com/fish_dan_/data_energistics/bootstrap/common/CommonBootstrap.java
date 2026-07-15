package com.fish_dan_.data_energistics.bootstrap.common;

import com.fish_dan_.data_energistics.common.CommonProxy;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreSyncAccessors;
import com.fish_dan_.data_energistics.config.ConfigHolder;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

public final class CommonBootstrap {

    private CommonBootstrap() {}

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        TrinityDataCoreSyncAccessors.init();
        ConfigHolder.init(modContainer);
        CommonProxy.init(modEventBus);
    }
}
