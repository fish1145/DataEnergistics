package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.DataCaptureBallItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Data_Energistics.MODID);
    private static final ResourceKey<CreativeModeTab> DATA_ENERGISTICS_TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Data_Energistics.id(Data_Energistics.MODID));
    private static final ResourceKey<CreativeModeTab> MULTIBLOCK_TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Data_Energistics.id(Data_Energistics.MODID + "_multiblock"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DATA_ENERGISTICS_TAB = CREATIVE_MODE_TABS.register(
            Data_Energistics.MODID,
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Data_Energistics.MODID))
                    .icon(ModItems.DATA_CRYSTAL::toStack)
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.DATA_FLOW_CELL_1K);
                        output.accept(ModItems.DATA_FLOW_CELL_4K);
                        output.accept(ModItems.DATA_FLOW_CELL_16K);
                        output.accept(ModItems.DATA_FLOW_CELL_64K);
                        output.accept(ModItems.DATA_FLOW_CELL_256K);
                        output.accept(ModItems.DATA_FLOW_CELL_1M);
                        output.accept(ModItems.DATA_FLOW_CELL_4M);
                        output.accept(ModItems.DATA_FLOW_CELL_16M);
                        output.accept(ModItems.DATA_FLOW_CELL_64M);
                        output.accept(ModItems.DATA_FLOW_CELL_256M);
                        output.accept(ModItems.DATA_CELL_INFINITY);

                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_1K);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_4K);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_16K);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_64K);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_256K);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_1M);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_4M);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_16M);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_64M);
                        output.accept(ModItems.PORTABLE_DATA_FLOW_CELL_256M);

                        output.accept(ModItems.DATA_STORAGE_COMPONENT_1K);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_4K);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_16K);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_64K);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_256K);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_1M);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_4M);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_16M);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_64M);
                        output.accept(ModItems.DATA_STORAGE_COMPONENT_256M);

                        output.accept(ModItems.DATA_SOLAR_PANEL);
                        output.accept(ModItems.DATA_EXTRACTOR);
                        output.accept(ModItems.DATA_RIPPER_REASSEMBLER);
                        output.accept(ModItems.DATA_FRAMEWORK);
                        output.accept(ModItems.DATA_DISTRIBUTION_TOWER);
                        output.accept(ModItems.DATA_MIMETIC_FIELD);
                        output.accept(ModItems.DATA_TELEPORT_ANCHOR);
                        output.accept(ModItems.DATA_SANCTUM);
                        output.accept(ModItems.DATA_SANCTUM_INTERFACE);
                        output.accept(ModItems.DATA_CHARGER);
                        output.accept(ModItems.EXTENDED_DATA_CHARGER);
                        output.accept(ModItems.DATA_SANCTUM_INTERFACE_PART);
                        output.accept(ModItems.DATA_SANCTUM_INTERFACE_UPGRADE);
                        output.accept(ModItems.ADAPTIVE_PATTERN_PROVIDER);
                        output.accept(ModItems.ADAPTIVE_PATTERN_PROVIDER_UPGRADE);
                        output.accept(ModItems.TNT_CONFIGURABLE);
                        output.accept(ModItems.DATA_NUKE);
                        output.accept(ModItems.RESIDUAL_DATA_ORE);
                        output.accept(ModItems.ENDER_COHESION_METEORITE_0);
                        output.accept(ModItems.ENDER_COHESION_METEORITE_1);
                        output.accept(ModItems.ENDER_COHESION_METEORITE_2);
                        output.accept(ModItems.DATA_METEORITE_COMPASS);
                        output.accept(ModItems.DATA_CRYSTAL_BLOCK);
                        output.accept(ModItems.DIGITAL_STORAGE_DEPOT);
                        output.accept(ModItems.BUDDING_DATA_CRYSTAL_0);
                        output.accept(ModItems.BUDDING_DATA_CRYSTAL_1);
                        output.accept(ModItems.BUDDING_DATA_CRYSTAL_2);
                        output.accept(ModItems.BUDDING_DATA_CRYSTAL_3);
                        output.accept(ModItems.BUDDING_DATA_CRYSTAL_4);
                        output.accept(ModItems.SMALL_DATA_CRYSTAL_BUD);
                        output.accept(ModItems.MEDIUM_DATA_CRYSTAL_BUD);
                        output.accept(ModItems.LARGE_DATA_CRYSTAL_BUD);
                        output.accept(ModItems.DATA_CRYSTAL_CLUSTER);
                        output.accept(ModItems.ADAPTIVE_PATTERN_PROVIDER_PART);
                        output.accept(ModItems.ME_SOLAR_PANEL_PART);
                        output.accept(ModItems.UNIVERSAL_TERMINAL);
                        output.accept(ModItems.DATA_CRYSTAL);
                        output.accept(ModItems.DATA_CRYSTAL_SWORD);
                        output.accept(ModItems.DATA_CRYSTAL_AXE);
                        output.accept(ModItems.DATA_CRYSTAL_PICKAXE);
                        output.accept(ModItems.DATA_CRYSTAL_HOE);
                        output.accept(ModItems.DATA_CRYSTAL_SHOVEL);
                        output.accept(ModItems.DATA_CRYSTAL_CUTTING_KNIFE);
                        output.accept(ModItems.DATA_LIGHT_SABER);
                        output.accept(ModItems.DATA_SANCTIFIER);
                        output.accept(ModItems.CARD_SABER_ENERGY);
                        output.accept(ModItems.REDSTONE_TUNING_CARD);
                        output.accept(ModItems.SOLIDIFIED_OBSIDIAN);
                        output.accept(ModItems.DATA_DUST);
                        output.accept(ModItems.OBSIDIAN_DUST);
                        output.accept(ModFluids.ENDER_BUCKET);
                        output.accept(ModFluids.DATA_CORROSION_LIQUID_BUCKET);
                        output.accept(ModItems.DATA_CARRIER);
                        output.accept(ModItems.MOB_DATA_CARRIER);
                        output.accept(ModItems.CROP_DATA_CARRIER);
                        output.accept(ModItems.ORE_DATA_CARRIER);
                        output.accept(ModItems.TIME_CORE);
                        output.accept(ModItems.ME_VACUUM);
                        output.accept(ModItems.DATA_FLOW_COMPONENT_HOUSING);
                        output.accept(ModItems.DATA_DISTRIBUTION_CONNECTOR);
                        output.accept(ModItems.DATA_INSCRIBER_TEMPLATE);
                        output.accept(ModItems.DATA_CIRCUIT_BOARD);
                        output.accept(ModItems.DATA_PROCESSOR);
                        output.accept(ModItems.DIGISIDIAN_MEMORIZE_INGOT);
                        output.accept(DataCaptureBallItem.createChargedStack());
                        output.accept(ModItems.MATTER_CONVERGING_CROSSBOW);
                        output.accept(ModItems.DATA_RIPPER);
                        output.accept(ModItems.FISH_DAN);
                        output.accept(ModItems.QIUYEQAQ2024);
                        output.accept(ModItems.TED_XENON);
                    })
                    .withTabsAfter(MULTIBLOCK_TAB_KEY)
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS.location())
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MULTIBLOCK_TAB = CREATIVE_MODE_TABS.register(
            Data_Energistics.MODID + "_multiblock",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Data_Energistics.MODID + ".multiblock"))
                    .icon(ModItems.TRINITY_DATA_CORE::toStack)
                    .displayItems((parameters, output) -> acceptMultiblockItems(output))
                    .withTabsBefore(DATA_ENERGISTICS_TAB_KEY)
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    private static void acceptMultiblockItems(CreativeModeTab.Output output) {
        output.accept(ModItems.TRINITY_DATA_CORE);
        output.accept(ModItems.COMPOSITE_INPUT_WAREHOUSE);
        output.accept(ModItems.COMPOSITE_OUTPUT_WAREHOUSE);
        output.accept(ModItems.ME_COMPOSITE_INPUT_WAREHOUSE);
        output.accept(ModItems.ME_COMPOSITE_OUTPUT_WAREHOUSE);
        output.accept(ModItems.ME_PATTERN_BUFFER);
        output.accept(ModItems.TRINITY_ACCESS_HATCH);

        output.accept(ModItems.ME_DIGITAL_STORAGE_CORE_1M);
        output.accept(ModItems.ME_DIGITAL_STORAGE_CORE_4M);
        output.accept(ModItems.ME_DIGITAL_STORAGE_CORE_16M);
        output.accept(ModItems.ME_DIGITAL_STORAGE_CORE_64M);
        output.accept(ModItems.ME_DIGITAL_STORAGE_CORE_256M);
        output.accept(ModItems.ME_DIGITAL_STORAGE_CORE_1G);
        output.accept(ModItems.ME_DIGITAL_STORAGE_CORE_4G);
        output.accept(ModItems.ME_DIGITAL_STORAGE_CORE_16G);
        output.accept(ModItems.ME_DIGITAL_STORAGE_CORE_64G);
        output.accept(ModItems.ME_DIGITAL_STORAGE_CORE_256G);

        output.accept(ModItems.ME_DIGITAL_MERGED_STORAGE_CORE_1M);
        output.accept(ModItems.ME_DIGITAL_MERGED_STORAGE_CORE_4M);
        output.accept(ModItems.ME_DIGITAL_MERGED_STORAGE_CORE_16M);
        output.accept(ModItems.ME_DIGITAL_MERGED_STORAGE_CORE_64M);
        output.accept(ModItems.ME_DIGITAL_MERGED_STORAGE_CORE_256M);
        output.accept(ModItems.ME_DIGITAL_MERGED_STORAGE_CORE_1G);
        output.accept(ModItems.ME_DIGITAL_MERGED_STORAGE_CORE_4G);
        output.accept(ModItems.ME_DIGITAL_MERGED_STORAGE_CORE_16G);
        output.accept(ModItems.ME_DIGITAL_MERGED_STORAGE_CORE_64G);
        output.accept(ModItems.ME_DIGITAL_MERGED_STORAGE_CORE_256G);

        output.accept(ModItems.ME_DIGITAL_PATTERN_PROCESSING_CORE);
        output.accept(ModItems.EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE);
        output.accept(ModItems.OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE);
    }
}
