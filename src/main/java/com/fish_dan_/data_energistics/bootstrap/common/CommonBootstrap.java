package com.fish_dan_.data_energistics.bootstrap.common;

import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreSyncAccessors;

import net.neoforged.bus.api.IEventBus;

public final class CommonBootstrap {

    private CommonBootstrap() {}

    public static void init(IEventBus modEventBus) {
        TrinityDataCoreSyncAccessors.init();
        CommonProxy.init(modEventBus);
    }
}
