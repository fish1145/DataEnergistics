package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataSanctumBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.blockentity.TrinityPatternCoreBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.MeCompositeInputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.MeCompositeOutputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.item.order.OrderPackageMenuHost;
import com.fish_dan_.data_energistics.item.vacuum.MeVacuumMenuHost;
import com.fish_dan_.data_energistics.menu.machine.DataDistributionTowerMenu;
import com.fish_dan_.data_energistics.menu.machine.DataExtractorMenu;
import com.fish_dan_.data_energistics.menu.machine.DataMimeticFieldMenu;
import com.fish_dan_.data_energistics.menu.machine.DataRipperMenu;
import com.fish_dan_.data_energistics.menu.machine.DataRipperReassemblerMenu;
import com.fish_dan_.data_energistics.menu.machine.DataSolarPanelMenu;
import com.fish_dan_.data_energistics.menu.machine.DataSolarPanelMenuHost;
import com.fish_dan_.data_energistics.menu.machine.DataTeleportAnchorMenu;
import com.fish_dan_.data_energistics.menu.patternprovider.AdaptivePatternProviderMenu;
import com.fish_dan_.data_energistics.menu.sanctum.DataSanctumInterfaceMenu;
import com.fish_dan_.data_energistics.menu.sanctum.DataSanctumLargeInterfaceMenu;
import com.fish_dan_.data_energistics.menu.sanctum.DataSanctumStatusMenu;
import com.fish_dan_.data_energistics.menu.storage.CompositeWarehouseMenu;
import com.fish_dan_.data_energistics.menu.storage.DigitalStorageDepotMenu;
import com.fish_dan_.data_energistics.menu.storage.MeCompositeInputWarehouseMenu;
import com.fish_dan_.data_energistics.menu.storage.MeCompositeOutputWarehouseMenu;
import com.fish_dan_.data_energistics.menu.storage.MePatternBufferMenu;
import com.fish_dan_.data_energistics.menu.storage.MeVacuumMenu;
import com.fish_dan_.data_energistics.menu.storage.OrderPackageMenu;
import com.fish_dan_.data_energistics.menu.trinity.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.menu.trinity.TrinityInformationExchangeDepotMenu;
import com.fish_dan_.data_energistics.menu.trinity.TrinityInformationExchangeDepotMenuHost;
import com.fish_dan_.data_energistics.menu.trinity.TrinityPatternCoreMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalCraftingTermMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalMEStorageMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternAccessTermMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;
import com.fish_dan_.data_energistics.network.trinity.TrinityAutoBuildDefinitionBundleCodec;
import com.fish_dan_.data_energistics.part.DataRipperPart;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.menu.implementations.MenuTypeBuilder;

public final class DEMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Data_Energistics.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<DataRipperMenu>> DATA_RIPPER = MENUS.register("data_ripper", () -> MenuTypeBuilder
            .create(DataRipperMenu::new, DataRipperPart.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_ripper")));

    public static final DeferredHolder<MenuType<?>, MenuType<DataDistributionTowerMenu>> DATA_DISTRIBUTION_TOWER = MENUS.register("data_distribution_tower", () -> IMenuTypeExtension.create((id, playerInventory, data) -> {
        var pos = data.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        DataDistributionTowerBlockEntity tower = blockEntity instanceof DataDistributionTowerBlockEntity host ? host : null;
        return new DataDistributionTowerMenu(id, playerInventory, tower);
    }));

    public static final DeferredHolder<MenuType<?>, MenuType<DataExtractorMenu>> DATA_EXTRACTOR = MENUS.register("data_extractor", () -> MenuTypeBuilder
            .create(DataExtractorMenu::new, DataExtractorBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_extractor")));

    public static final DeferredHolder<MenuType<?>, MenuType<DataRipperReassemblerMenu>> DATA_RIPPER_REASSEMBLER = MENUS.register("data_reassembler", () -> MenuTypeBuilder
            .create(DataRipperReassemblerMenu::new, DataRipperReassemblerBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_reassembler")));

    public static final DeferredHolder<MenuType<?>, MenuType<TrinityDataCoreMenu>> TRINITY_DATA_CORE = MENUS.register("trinity_data_core", () -> IMenuTypeExtension.create((id, playerInventory, data) -> {
        var pos = data.readBlockPos();
        var hostId = data.readUUID();
        var menuSessionId = data.readUUID();
        var autoBuildPreviewSpec = TrinityAutoBuildDefinitionBundleCodec.read(data).previewSpec();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        TrinityDataCoreBlockEntity host = blockEntity instanceof TrinityDataCoreBlockEntity dataCore ? dataCore : null;
        if (host == null) {
            Data_Energistics.LOGGER.error(
                    "Cannot resolve the Trinity Data Core menu host at {} in {}",
                    pos,
                    playerInventory.player.level().dimension().location());
        }
        return new TrinityDataCoreMenu(
                id,
                playerInventory,
                host,
                hostId,
                menuSessionId,
                autoBuildPreviewSpec);
    }));

    public static final DeferredHolder<MenuType<?>, MenuType<DataMimeticFieldMenu>> DATA_MIMETIC_FIELD = MENUS.register("data_mimetic_field", () -> MenuTypeBuilder
            .create(DataMimeticFieldMenu::new, DataMimeticFieldBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_mimetic_field")));

    public static final DeferredHolder<MenuType<?>, MenuType<DataSolarPanelMenu>> DATA_SOLAR_PANEL = MENUS.register("me_solar_panel", () -> MenuTypeBuilder
            .create(DataSolarPanelMenu::new, DataSolarPanelMenuHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "me_solar_panel")));

    public static final DeferredHolder<MenuType<?>, MenuType<DigitalStorageDepotMenu>> DIGITAL_STORAGE_DEPOT = MENUS.register("digital_storage_depot", () -> MenuTypeBuilder
            .create(DigitalStorageDepotMenu::new, DigitalStorageDepotBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "digital_storage_depot")));

    public static final DeferredHolder<MenuType<?>, MenuType<CompositeWarehouseMenu>> COMPOSITE_WAREHOUSE = MENUS.register("composite_warehouse", () -> MenuTypeBuilder
            .create(CompositeWarehouseMenu::new, CompositeWarehouseBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "composite_warehouse")));

    public static final DeferredHolder<MenuType<?>, MenuType<MeCompositeInputWarehouseMenu>> ME_COMPOSITE_INPUT_WAREHOUSE = MENUS.register("me_composite_input_warehouse", () -> MenuTypeBuilder
            .create(MeCompositeInputWarehouseMenu::new, MeCompositeInputWarehouseBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "me_composite_input_warehouse")));

    public static final DeferredHolder<MenuType<?>, MenuType<MeCompositeOutputWarehouseMenu>> ME_COMPOSITE_OUTPUT_WAREHOUSE = MENUS.register("me_composite_output_warehouse", () -> MenuTypeBuilder
            .create(MeCompositeOutputWarehouseMenu::new, MeCompositeOutputWarehouseBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "me_composite_output_warehouse")));

    public static final DeferredHolder<MenuType<?>, MenuType<MePatternBufferMenu>> ME_PATTERN_BUFFER = MENUS.register("me_pattern_buffer", () -> MenuTypeBuilder
            .create(MePatternBufferMenu::new, MePatternBufferBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "me_pattern_buffer")));

    public static final DeferredHolder<MenuType<?>, MenuType<MeVacuumMenu>> ME_VACUUM = MENUS.register("me_vacuum", () -> MenuTypeBuilder
            .create(MeVacuumMenu::new, MeVacuumMenuHost.class)
            .withMenuTitle(host -> Component.translatable("item." + Data_Energistics.MODID + ".me_vacuum"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "me_vacuum")));

    public static final DeferredHolder<MenuType<?>, MenuType<OrderPackageMenu>> ORDER_PACKAGE = MENUS.register("order_package", () -> MenuTypeBuilder
            .create(OrderPackageMenu::new, OrderPackageMenuHost.class)
            .withMenuTitle(host -> Component.translatable("item." + Data_Energistics.MODID + ".order_package"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "order_package")));

    public static final DeferredHolder<MenuType<?>, MenuType<DataTeleportAnchorMenu>> DATA_TELEPORT_ANCHOR = MENUS.register("data_teleport_anchor", () -> MenuTypeBuilder
            .create(DataTeleportAnchorMenu::new, DataTeleportAnchorBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_teleport_anchor")));

    public static final DeferredHolder<MenuType<?>, MenuType<DataSanctumStatusMenu>> DATA_SANCTUM_STATUS = MENUS.register("data_sanctum_status", () -> IMenuTypeExtension.create((id, playerInventory, data) -> {
        var pos = data.readBlockPos();
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        DataSanctumBlockEntity sanctum = blockEntity instanceof DataSanctumBlockEntity host ? host : null;
        return new DataSanctumStatusMenu(id, playerInventory, sanctum);
    }));

    public static final DeferredHolder<MenuType<?>, MenuType<DataSanctumInterfaceMenu>> DATA_SANCTUM_INTERFACE = MENUS.register("data_sanctum_interface", () -> MenuTypeBuilder
            .create(DataSanctumInterfaceMenu::new, DataSanctumBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_sanctum_interface")));

    public static final DeferredHolder<MenuType<?>, MenuType<DataSanctumLargeInterfaceMenu>> DATA_SANCTUM_LARGE_INTERFACE = MENUS.register("data_sanctum_large_interface", () -> MenuTypeBuilder
            .create(DataSanctumLargeInterfaceMenu::new, DataSanctumLargeInterfaceHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_sanctum_large_interface")));

    public static final DeferredHolder<MenuType<?>, MenuType<AdaptivePatternProviderMenu>> ADAPTIVE_PATTERN_PROVIDER = MENUS.register("adaptive_pattern_provider", () -> MenuTypeBuilder
            .create(AdaptivePatternProviderMenu::new, AdaptivePatternProviderHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "adaptive_pattern_provider")));

    public static final DeferredHolder<MenuType<?>, MenuType<TrinityPatternCoreMenu>> TRINITY_PATTERN_CORE = MENUS.register("trinity_pattern_core", () -> MenuTypeBuilder
            .create(TrinityPatternCoreMenu::new, TrinityPatternCoreBlockEntity.class)
            .withMenuTitle(host -> host.getBlockState().getBlock().getName())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "trinity_pattern_core")));

    public static final DeferredHolder<MenuType<?>, MenuType<TrinityInformationExchangeDepotMenu>> TRINITY_INFORMATION_EXCHANGE_DEPOT = MENUS.register("trinity_information_exchange_depot", () -> MenuTypeBuilder
            .create(TrinityInformationExchangeDepotMenu::new, TrinityInformationExchangeDepotMenuHost.class)
            .withMenuTitle(host -> DEBlocks.TRINITY_INFORMATION_EXCHANGE_DEPOT.get().getName())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "trinity_information_exchange_depot")));

    public static final DeferredHolder<MenuType<?>, MenuType<UniversalMEStorageMenu>> UNIVERSAL_ME_STORAGE = MENUS.register("universal_me_storage", () -> MenuTypeBuilder
            .create(UniversalMEStorageMenu::new, UniversalTerminalPart.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "universal_me_storage")));

    public static final DeferredHolder<MenuType<?>, MenuType<UniversalCraftingTermMenu>> UNIVERSAL_CRAFTING_TERM = MENUS.register("universal_crafting_terminal", () -> MenuTypeBuilder
            .create(UniversalCraftingTermMenu::new, UniversalTerminalPart.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "universal_crafting_terminal")));

    public static final DeferredHolder<MenuType<?>, MenuType<UniversalPatternEncodingTermMenu>> UNIVERSAL_PATTERN_ENCODING_TERM = MENUS.register("universal_pattern_encoding_terminal", () -> MenuTypeBuilder
            .create(UniversalPatternEncodingTermMenu::new, UniversalTerminalPart.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "universal_pattern_encoding_terminal")));

    public static final DeferredHolder<MenuType<?>, MenuType<UniversalPatternAccessTermMenu>> UNIVERSAL_PATTERN_ACCESS_TERM = MENUS.register("universal_pattern_access_terminal", () -> MenuTypeBuilder
            .create(UniversalPatternAccessTermMenu::new, UniversalTerminalPart.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "universal_pattern_access_terminal")));

    private DEMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
