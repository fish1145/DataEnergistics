package com.fish_dan_.data_energistics.bootstrap.common;

import com.fish_dan_.data_energistics.common.CommonProxy;
import com.fish_dan_.data_energistics.config.ConfigHolder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import org.jetbrains.annotations.Nullable;

public final class CommonBootstrap {

    private CommonBootstrap() {
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        ConfigHolder.init(modContainer);
        CommonProxy.init(modEventBus);
    }
}
