package com.fish_dan_.data_energistics.integration.jade;

import com.fish_dan_.data_energistics.block.DataChargerBlock;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.block.DataExtractorBlock;
import com.fish_dan_.data_energistics.block.DataFrameworkMainBlock;
import com.fish_dan_.data_energistics.block.DataMimeticFieldBlock;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.block.DataSanctumInterfaceBlock;
import com.fish_dan_.data_energistics.block.DataSolarPanelBlock;
import com.fish_dan_.data_energistics.block.DataTeleportAnchorBlock;
import com.fish_dan_.data_energistics.block.DigitalConstructFlowerBlock;
import com.fish_dan_.data_energistics.block.DigitalStorageDepotBlock;
import com.fish_dan_.data_energistics.blockentity.DataChargerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataFrameworkBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataSanctumInterfaceBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataSolarPanelBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DigitalConstructFlowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class DataEnergisticsJadePlugin implements IWailaPlugin {

    private static final DataDistributionTowerJadeProvider TOWER_PROVIDER = new DataDistributionTowerJadeProvider();
    private static final DataDistributionTowerEnergyJadeProvider TOWER_ENERGY_PROVIDER = new DataDistributionTowerEnergyJadeProvider();
    private static final DataExtractorJadeProvider EXTRACTOR_PROVIDER = new DataExtractorJadeProvider();
    private static final DataMimeticFieldJadeProvider MIMETIC_FIELD_PROVIDER = new DataMimeticFieldJadeProvider();
    private static final DataRipperReassemblerJadeProvider DATA_RIPPER_REASSEMBLER_PROVIDER = new DataRipperReassemblerJadeProvider();
    private static final DataSolarPanelJadeProvider SOLAR_PANEL_PROVIDER = new DataSolarPanelJadeProvider();
    private static final DataTeleportAnchorJadeProvider TELEPORT_ANCHOR_PROVIDER = new DataTeleportAnchorJadeProvider();
    private static final MultiBlockJadeProvider MULTI_BLOCK_PROVIDER = new MultiBlockJadeProvider();
    private static final DataSanctumJadeProvider DATA_SANCTUM_PROVIDER = new DataSanctumJadeProvider();
    private static final DataChargerJadeProvider DATA_CHARGER_PROVIDER = new DataChargerJadeProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(TOWER_PROVIDER, DataDistributionTowerBlock.class);
        registration.registerEnergyStorage(TOWER_ENERGY_PROVIDER, DataDistributionTowerBlockEntity.class);
        registration.registerBlockDataProvider(EXTRACTOR_PROVIDER, DataExtractorBlockEntity.class);
        registration.registerBlockDataProvider(MIMETIC_FIELD_PROVIDER, DataMimeticFieldBlockEntity.class);
        registration.registerBlockDataProvider(DATA_RIPPER_REASSEMBLER_PROVIDER, DataRipperReassemblerBlockEntity.class);
        registration.registerBlockDataProvider(SOLAR_PANEL_PROVIDER, DataSolarPanelBlockEntity.class);
        registration.registerBlockDataProvider(TELEPORT_ANCHOR_PROVIDER, DataTeleportAnchorBlockEntity.class);
        registration.registerBlockDataProvider(DATA_SANCTUM_PROVIDER, DataSanctumBlock.class);
        registration.registerBlockDataProvider(DATA_CHARGER_PROVIDER, DataChargerBlockEntity.class);
        registration.registerBlockDataProvider(MULTI_BLOCK_PROVIDER, DataFrameworkBlockEntity.class);
        registration.registerBlockDataProvider(MULTI_BLOCK_PROVIDER, DigitalConstructFlowerBlockEntity.class);
        registration.registerBlockDataProvider(NetworkStatusJadeProvider.DIGITAL_STORAGE_DEPOT, DigitalStorageDepotBlockEntity.class);
        registration.registerBlockDataProvider(NetworkStatusJadeProvider.DATA_SANCTUM_INTERFACE, DataSanctumInterfaceBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TOWER_PROVIDER, DataDistributionTowerBlock.class);
        registration.registerEnergyStorageClient(TOWER_ENERGY_PROVIDER);
        registration.registerBlockComponent(EXTRACTOR_PROVIDER, DataExtractorBlock.class);
        registration.registerBlockComponent(MIMETIC_FIELD_PROVIDER, DataMimeticFieldBlock.class);
        registration.registerBlockComponent(DATA_RIPPER_REASSEMBLER_PROVIDER, DataRipperReassemblerBlock.class);
        registration.registerBlockComponent(SOLAR_PANEL_PROVIDER, DataSolarPanelBlock.class);
        registration.registerBlockComponent(TELEPORT_ANCHOR_PROVIDER, DataTeleportAnchorBlock.class);
        registration.registerBlockComponent(DATA_SANCTUM_PROVIDER, DataSanctumBlock.class);
        registration.registerBlockComponent(DATA_CHARGER_PROVIDER, DataChargerBlock.class);
        registration.registerBlockComponent(MULTI_BLOCK_PROVIDER, DataFrameworkMainBlock.class);
        registration.registerBlockComponent(MULTI_BLOCK_PROVIDER, DigitalConstructFlowerBlock.class);
        registration.registerBlockComponent(NetworkStatusJadeProvider.DIGITAL_STORAGE_DEPOT, DigitalStorageDepotBlock.class);
        registration.registerBlockComponent(NetworkStatusJadeProvider.DATA_SANCTUM_INTERFACE, DataSanctumInterfaceBlock.class);
        registration.addConfig(DataSanctumJadeProvider.MODE_ID, false);
        registration.addConfig(DataSanctumJadeProvider.PART_ID, false);
    }
}
