package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = Data_Energistics.MODID, dist = Dist.CLIENT)
public final class DataEnergisticsClient {

    public DataEnergisticsClient(IEventBus modEventBus) {
        ClientBootstrap.init(modEventBus);
    }
}
