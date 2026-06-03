package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderHost;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.menu.AdaptivePatternProviderMenu;
import com.fish_dan_.data_energistics.menu.DataDistributionTowerMenu;
import com.fish_dan_.data_energistics.menu.DataExtractorMenu;
import com.fish_dan_.data_energistics.menu.DataMimeticFieldMenu;
import com.fish_dan_.data_energistics.menu.DataRipperMenu;
import com.fish_dan_.data_energistics.menu.DataRipperReassemblerMenu;
import com.fish_dan_.data_energistics.menu.DataSolarPanelMenu;
import com.fish_dan_.data_energistics.menu.DataSolarPanelMenuHost;
import com.fish_dan_.data_energistics.menu.DataTeleportAnchorMenu;
import com.fish_dan_.data_energistics.menu.DigitalStorageDepotMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalCraftingTermMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalMEStorageMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternAccessTermMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;
import com.fish_dan_.data_energistics.part.DataRipperPart;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.menu.implementations.MenuTypeBuilder;

public final class ModMenus {

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

    public static final DeferredHolder<MenuType<?>, MenuType<DataMimeticFieldMenu>> DATA_MIMETIC_FIELD = MENUS.register("data_mimetic_field", () -> MenuTypeBuilder
            .create(DataMimeticFieldMenu::new, DataMimeticFieldBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_mimetic_field")));

    public static final DeferredHolder<MenuType<?>, MenuType<DataSolarPanelMenu>> DATA_SOLAR_PANEL = MENUS.register("me_solar_panel", () -> MenuTypeBuilder
            .create(DataSolarPanelMenu::new, DataSolarPanelMenuHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "me_solar_panel")));

    public static final DeferredHolder<MenuType<?>, MenuType<DigitalStorageDepotMenu>> DIGITAL_STORAGE_DEPOT = MENUS.register("digital_storage_depot", () -> MenuTypeBuilder
            .create(DigitalStorageDepotMenu::new, DigitalStorageDepotBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "digital_storage_depot")));

    public static final DeferredHolder<MenuType<?>, MenuType<DataTeleportAnchorMenu>> DATA_TELEPORT_ANCHOR = MENUS.register("data_teleport_anchor", () -> MenuTypeBuilder
            .create(DataTeleportAnchorMenu::new, DataTeleportAnchorBlockEntity.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_teleport_anchor")));

    public static final DeferredHolder<MenuType<?>, MenuType<AdaptivePatternProviderMenu>> ADAPTIVE_PATTERN_PROVIDER = MENUS.register("adaptive_pattern_provider", () -> MenuTypeBuilder
            .create(AdaptivePatternProviderMenu::new, AdaptivePatternProviderHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "adaptive_pattern_provider")));

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

    private ModMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
