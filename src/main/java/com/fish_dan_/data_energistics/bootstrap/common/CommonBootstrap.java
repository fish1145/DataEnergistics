package com.fish_dan_.data_energistics.bootstrap.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreSyncAccessors;
import com.fish_dan_.data_energistics.configuration.rules.schema.DataExtractorRulesConfiguration;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import net.neoforged.bus.api.IEventBus;

public final class CommonBootstrap {

    private CommonBootstrap() {}

    public static void init(IEventBus modEventBus) {
        TrinityDataCoreSyncAccessors.init();
        Data_Energistics.LOGGER.debug(
                "Registered Configuration schemas {} and {}",
                DataEnergisticsConfiguration.HOLDER.getConfigId(),
                DataExtractorRulesConfiguration.HOLDER.getConfigId());
        CommonProxy.init(modEventBus);
    }
}
