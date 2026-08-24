package com.fish_dan_.data_energistics.integration.jade.entrypoint;

import com.fish_dan_.data_energistics.block.TuningForkBaseBlock;
import com.fish_dan_.data_energistics.block.machine.DataChargerBlock;
import com.fish_dan_.data_energistics.block.machine.DataExtractorBlock;
import com.fish_dan_.data_energistics.block.machine.DataIntegratedChargerBlock;
import com.fish_dan_.data_energistics.block.machine.DataMimeticFieldBlock;
import com.fish_dan_.data_energistics.block.machine.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.block.machine.DataSolarPanelBlock;
import com.fish_dan_.data_energistics.block.machine.DataTeleportAnchorBlock;
import com.fish_dan_.data_energistics.block.sanctum.DataSanctumBlock;
import com.fish_dan_.data_energistics.block.sanctum.DataSanctumInterfaceBlock;
import com.fish_dan_.data_energistics.block.storage.CompartmentBlock;
import com.fish_dan_.data_energistics.block.storage.DigitalStorageDepotBlock;
import com.fish_dan_.data_energistics.block.tower.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.block.trinity.TrinityDataCoreBlock;
import com.fish_dan_.data_energistics.blockentity.TuningForkBaseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataAsynchronousProcessingFactoryBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataChargerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataSolarPanelBlockEntity;
import com.fish_dan_.data_energistics.blockentity.machine.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.sanctum.DataSanctumInterfaceBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.CompartmentBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityInformationExchangeDepotBlockEntity;
import com.fish_dan_.data_energistics.integration.jade.machine.DataChargerJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.machine.DataEnergyCellEnergyJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.machine.DataExtractorJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.machine.DataIntegratedChargerJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.machine.DataMimeticFieldJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.machine.DataRipperReassemblerJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.machine.DataSolarPanelJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.machine.DataTeleportAnchorJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.multiblock.MultiBlockJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.network.NetworkStatusJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.sanctum.DataSanctumJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.storage.CompartmentJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.tower.DataDistributionTowerEnergyJadeProvider;
import com.fish_dan_.data_energistics.integration.jade.tower.DataDistributionTowerJadeProvider;

import appeng.blockentity.networking.EnergyCellBlockEntity;
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
    private static final DataEnergyCellEnergyJadeProvider DATA_ENERGY_CELL_ENERGY_PROVIDER = new DataEnergyCellEnergyJadeProvider();
    private static final DataIntegratedChargerJadeProvider DATA_INTEGRATED_CHARGER_PROVIDER = new DataIntegratedChargerJadeProvider();
    private static final CompartmentJadeProvider COMPARTMENT_PROVIDER = new CompartmentJadeProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(COMPARTMENT_PROVIDER, CompartmentBlockEntity.class);
        registration.registerBlockDataProvider(TOWER_PROVIDER, DataDistributionTowerBlock.class);
        registration.registerEnergyStorage(TOWER_ENERGY_PROVIDER, DataDistributionTowerBlockEntity.class);
        registration.registerBlockDataProvider(EXTRACTOR_PROVIDER, DataExtractorBlockEntity.class);
        registration.registerBlockDataProvider(MIMETIC_FIELD_PROVIDER, DataMimeticFieldBlockEntity.class);
        registration.registerBlockDataProvider(DATA_RIPPER_REASSEMBLER_PROVIDER, DataRipperReassemblerBlockEntity.class);
        registration.registerBlockDataProvider(DATA_RIPPER_REASSEMBLER_PROVIDER, DataAsynchronousProcessingFactoryBlockEntity.class);
        registration.registerBlockDataProvider(SOLAR_PANEL_PROVIDER, DataSolarPanelBlockEntity.class);
        registration.registerBlockDataProvider(TELEPORT_ANCHOR_PROVIDER, DataTeleportAnchorBlockEntity.class);
        registration.registerBlockDataProvider(DATA_SANCTUM_PROVIDER, DataSanctumBlock.class);
        registration.registerBlockDataProvider(DATA_CHARGER_PROVIDER, DataChargerBlockEntity.class);
        registration.registerBlockDataProvider(DATA_INTEGRATED_CHARGER_PROVIDER, DataIntegratedChargerBlockEntity.class);
        registration.registerEnergyStorage(DATA_ENERGY_CELL_ENERGY_PROVIDER, EnergyCellBlockEntity.class);
        registration.registerBlockDataProvider(MULTI_BLOCK_PROVIDER, TrinityDataCoreBlockEntity.class);
        registration.registerBlockDataProvider(NetworkStatusJadeProvider.DIGITAL_STORAGE_DEPOT, DigitalStorageDepotBlockEntity.class);
        registration.registerBlockDataProvider(NetworkStatusJadeProvider.DATA_SANCTUM_INTERFACE, DataSanctumInterfaceBlockEntity.class);
        registration.registerBlockDataProvider(NetworkStatusJadeProvider.TRINITY_INFORMATION_EXCHANGE_DEPOT, TrinityInformationExchangeDepotBlockEntity.class);
        registration.registerBlockDataProvider(NetworkStatusJadeProvider.TUNING_FORK_BASE, TuningForkBaseBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(COMPARTMENT_PROVIDER, CompartmentBlock.class);
        registration.registerBlockComponent(TOWER_PROVIDER, DataDistributionTowerBlock.class);
        registration.registerEnergyStorageClient(TOWER_ENERGY_PROVIDER);
        registration.registerBlockComponent(EXTRACTOR_PROVIDER, DataExtractorBlock.class);
        registration.registerBlockComponent(MIMETIC_FIELD_PROVIDER, DataMimeticFieldBlock.class);
        registration.registerBlockComponent(DATA_RIPPER_REASSEMBLER_PROVIDER, DataRipperReassemblerBlock.class);
        registration.registerBlockComponent(SOLAR_PANEL_PROVIDER, DataSolarPanelBlock.class);
        registration.registerBlockComponent(TELEPORT_ANCHOR_PROVIDER, DataTeleportAnchorBlock.class);
        registration.registerBlockComponent(DATA_SANCTUM_PROVIDER, DataSanctumBlock.class);
        registration.registerBlockComponent(DATA_CHARGER_PROVIDER, DataChargerBlock.class);
        registration.registerBlockComponent(DATA_INTEGRATED_CHARGER_PROVIDER, DataIntegratedChargerBlock.class);
        registration.registerEnergyStorageClient(DATA_ENERGY_CELL_ENERGY_PROVIDER);
        registration.registerBlockComponent(MULTI_BLOCK_PROVIDER, TrinityDataCoreBlock.class);
        registration.registerBlockComponent(NetworkStatusJadeProvider.DIGITAL_STORAGE_DEPOT, DigitalStorageDepotBlock.class);
        registration.registerBlockComponent(NetworkStatusJadeProvider.DATA_SANCTUM_INTERFACE, DataSanctumInterfaceBlock.class);
        registration.registerBlockComponent(NetworkStatusJadeProvider.TRINITY_INFORMATION_EXCHANGE_DEPOT, CompartmentBlock.class);
        registration.registerBlockComponent(NetworkStatusJadeProvider.TUNING_FORK_BASE, TuningForkBaseBlock.class);
        registration.addConfig(DataSanctumJadeProvider.MODE_ID, false);
        registration.addConfig(DataSanctumJadeProvider.PART_ID, false);
        registration.addConfig(MultiBlockJadeProvider.ROLE_ID, false);
        registration.addConfig(MultiBlockJadeProvider.DEBUG_ID, false);
    }
}
