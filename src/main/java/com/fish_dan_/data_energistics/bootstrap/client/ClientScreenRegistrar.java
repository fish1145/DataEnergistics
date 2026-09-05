package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.screen.beam.BeamFormerScreen;
import com.fish_dan_.data_energistics.client.screen.crafting.CraftingPlanTreeScreen;
import com.fish_dan_.data_energistics.client.screen.machine.AdaptivePatternProviderScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataAsynchronousProcessingFactoryScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataDistributionTowerScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataExtractorScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataIntegratedChargerScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataMimeticFieldScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataRipperReassemblerMainScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataRipperScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataSanctumInterfaceScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataSanctumLargeInterfaceScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataSanctumStatusScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataSolarPanelScreen;
import com.fish_dan_.data_energistics.client.screen.machine.DataTeleportAnchorScreen;
import com.fish_dan_.data_energistics.client.screen.machine.MePatternBufferScreen;
import com.fish_dan_.data_energistics.client.screen.machine.MeVacuumScreen;
import com.fish_dan_.data_energistics.client.screen.machine.OrderPackageScreen;
import com.fish_dan_.data_energistics.client.screen.patternencoding.UniversalPatternEncodingTermScreen;
import com.fish_dan_.data_energistics.client.screen.storage.CompositeWarehouseScreen;
import com.fish_dan_.data_energistics.client.screen.storage.DigitalStorageDepotScreen;
import com.fish_dan_.data_energistics.client.screen.storage.MeCompositeInputWarehouseScreen;
import com.fish_dan_.data_energistics.client.screen.storage.MeCompositeOutputWarehouseScreen;
import com.fish_dan_.data_energistics.client.screen.terminal.UniversalCraftingTermScreen;
import com.fish_dan_.data_energistics.client.screen.terminal.UniversalMEStorageScreen;
import com.fish_dan_.data_energistics.client.screen.terminal.UniversalPatternAccessTermScreen;
import com.fish_dan_.data_energistics.client.screen.trinity.TrinityDataCoreScreen;
import com.fish_dan_.data_energistics.client.screen.trinity.TrinityInformationExchangeDepotScreen;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.init.client.InitScreens;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

final class ClientScreenRegistrar {

    private ClientScreenRegistrar() {}

    static void register(RegisterMenuScreensEvent event) {
        event.register(DEMenus.CRAFTING_PLAN_TREE.get(), CraftingPlanTreeScreen::new);
        InitScreens.register(event, DEMenus.DATA_RIPPER.get(), DataRipperScreen::new, "/screens/data_ripper.json");
        InitScreens.register(event, DEMenus.DATA_DISTRIBUTION_TOWER.get(), DataDistributionTowerScreen::new, "/screens/data_distribution_tower.json");
        InitScreens.register(event, DEMenus.DATA_EXTRACTOR.get(), DataExtractorScreen::new, "/screens/data_extractor.json");
        InitScreens.register(event, DEMenus.DATA_RIPPER_REASSEMBLER.get(), DataRipperReassemblerMainScreen::new,
                "/screens/data_reassembler.json");
        InitScreens.register(event, DEMenus.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(), DataAsynchronousProcessingFactoryScreen::new,
                "/screens/data_asynchronous_processing_factory.json");
        InitScreens.register(event, DEMenus.DATA_INTEGRATED_CHARGER.get(), DataIntegratedChargerScreen::new,
                "/screens/data_integrated_charger.json");
        event.register(DEMenus.TRINITY_INFORMATION_EXCHANGE_DEPOT.get(), TrinityInformationExchangeDepotScreen::new);
        event.register(DEMenus.TRINITY_DATA_CORE.get(), TrinityDataCoreScreen::new);
        InitScreens.register(event, DEMenus.DATA_MIMETIC_FIELD.get(), DataMimeticFieldScreen::new, "/screens/data_mimetic_field.json");
        InitScreens.register(event, DEMenus.DATA_SOLAR_PANEL.get(), DataSolarPanelScreen::new, "/screens/me_solar_panel.json");
        InitScreens.register(event, DEMenus.BEAM_FORMER.get(), BeamFormerScreen::new, "/screens/beam_former.json");
        InitScreens.register(event, DEMenus.DIGITAL_STORAGE_DEPOT.get(), DigitalStorageDepotScreen::new, "/screens/digital_storage_depot.json");
        InitScreens.register(event, DEMenus.COMPOSITE_WAREHOUSE.get(), CompositeWarehouseScreen::new, "/screens/composite_warehouse.json");
        InitScreens.register(event, DEMenus.ME_COMPOSITE_INPUT_WAREHOUSE.get(), MeCompositeInputWarehouseScreen::new, "/screens/me_composite_input_warehouse.json");
        InitScreens.register(event, DEMenus.ME_COMPOSITE_OUTPUT_WAREHOUSE.get(), MeCompositeOutputWarehouseScreen::new, "/screens/me_composite_output_warehouse.json");
        InitScreens.register(event, DEMenus.ME_PATTERN_BUFFER.get(), MePatternBufferScreen::new, "/screens/me_pattern_buffer.json");
        InitScreens.register(event, DEMenus.ME_VACUUM.get(), MeVacuumScreen::new, "/screens/me_vacuum.json");
        InitScreens.register(event, DEMenus.ORDER_PACKAGE.get(), OrderPackageScreen::new, "/screens/order_package.json");
        InitScreens.register(event, DEMenus.DATA_TELEPORT_ANCHOR.get(), DataTeleportAnchorScreen::new, "/screens/data_teleport_anchor.json");
        InitScreens.register(event, DEMenus.DATA_SANCTUM_STATUS.get(), DataSanctumStatusScreen::new, "/screens/data_sanctum_status.json");
        InitScreens.register(event, DEMenus.DATA_SANCTUM_INTERFACE.get(), DataSanctumInterfaceScreen::new, "/screens/data_sanctum_interface.json");
        InitScreens.register(event, DEMenus.DATA_SANCTUM_LARGE_INTERFACE.get(), DataSanctumLargeInterfaceScreen::new, "/screens/data_sanctum_large_interface.json");
        InitScreens.register(event, DEMenus.ADAPTIVE_PATTERN_PROVIDER.get(), AdaptivePatternProviderScreen::new, "/screens/adaptive_pattern_provider.json");
        InitScreens.register(event, DEMenus.UNIVERSAL_ME_STORAGE.get(), UniversalMEStorageScreen::new, "/screens/universal_me_storage_terminal.json");
        InitScreens.register(event, DEMenus.UNIVERSAL_CRAFTING_TERM.get(), UniversalCraftingTermScreen::new, "/screens/universal_crafting_terminal.json");
        InitScreens.register(event, DEMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(), UniversalPatternEncodingTermScreen::new, "/screens/universal_pattern_encoding_terminal.json");
        InitScreens.register(event, DEMenus.UNIVERSAL_PATTERN_ACCESS_TERM.get(), UniversalPatternAccessTermScreen::new, "/screens/universal_pattern_access_terminal.json");
    }
}
