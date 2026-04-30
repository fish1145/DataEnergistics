package com.fish_dan_.data_energistics.integration.jade;

import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.block.DataExtractorBlock;
import com.fish_dan_.data_energistics.block.DataMimeticFieldBlock;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class DataEnergisticsJadePlugin implements IWailaPlugin {
    private static final DataDistributionTowerJadeProvider TOWER_PROVIDER = new DataDistributionTowerJadeProvider();
    private static final DataExtractorJadeProvider EXTRACTOR_PROVIDER = new DataExtractorJadeProvider();
    private static final DataMimeticFieldJadeProvider MIMETIC_FIELD_PROVIDER = new DataMimeticFieldJadeProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(TOWER_PROVIDER, DataDistributionTowerBlockEntity.class);
        registration.registerBlockDataProvider(EXTRACTOR_PROVIDER, DataExtractorBlockEntity.class);
        registration.registerBlockDataProvider(MIMETIC_FIELD_PROVIDER, DataMimeticFieldBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TOWER_PROVIDER, DataDistributionTowerBlock.class);
        registration.registerBlockComponent(EXTRACTOR_PROVIDER, DataExtractorBlock.class);
        registration.registerBlockComponent(MIMETIC_FIELD_PROVIDER, DataMimeticFieldBlock.class);
    }
}
