package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DECreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Data_Energistics.MODID);
    private static final ResourceKey<CreativeModeTab> DATA_ENERGISTICS_TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Data_Energistics.id(Data_Energistics.MODID));
    private static final ResourceKey<CreativeModeTab> MULTIBLOCK_TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Data_Energistics.id(Data_Energistics.MODID + "_multiblock"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DATA_ENERGISTICS_TAB = CREATIVE_MODE_TABS.register(
            Data_Energistics.MODID,
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Data_Energistics.MODID))
                    .icon(DEItems.DATA_CRYSTAL::toStack)
                    .displayItems((parameters, output) -> {
                        output.accept(DEItems.DIGITAL_STORAGE_CELL_1K);
                        output.accept(DEItems.DIGITAL_STORAGE_CELL_4K);
                        output.accept(DEItems.DIGITAL_STORAGE_CELL_16K);
                        output.accept(DEItems.DIGITAL_STORAGE_CELL_64K);
                        output.accept(DEItems.DIGITAL_STORAGE_CELL_256K);
                        output.accept(DEItems.DIGITAL_STORAGE_CELL_1M);
                        output.accept(DEItems.DIGITAL_STORAGE_CELL_4M);
                        output.accept(DEItems.DIGITAL_STORAGE_CELL_16M);
                        output.accept(DEItems.DIGITAL_STORAGE_CELL_64M);
                        output.accept(DEItems.DIGITAL_STORAGE_CELL_256M);
                        output.accept(DEItems.DATA_CELL_INFINITY);

                        output.accept(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_1K);
                        output.accept(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_4K);
                        output.accept(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_16K);
                        output.accept(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_64K);
                        output.accept(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_256K);
                        output.accept(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_1M);
                        output.accept(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_4M);
                        output.accept(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_16M);
                        output.accept(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_64M);
                        output.accept(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_256M);

                        output.accept(DEItems.DATA_STORAGE_COMPONENT_1K);
                        output.accept(DEItems.DATA_STORAGE_COMPONENT_4K);
                        output.accept(DEItems.DATA_STORAGE_COMPONENT_16K);
                        output.accept(DEItems.DATA_STORAGE_COMPONENT_64K);
                        output.accept(DEItems.DATA_STORAGE_COMPONENT_256K);
                        output.accept(DEItems.DATA_STORAGE_COMPONENT_1M);
                        output.accept(DEItems.DATA_STORAGE_COMPONENT_4M);
                        output.accept(DEItems.DATA_STORAGE_COMPONENT_16M);
                        output.accept(DEItems.DATA_STORAGE_COMPONENT_64M);
                        output.accept(DEItems.DATA_STORAGE_COMPONENT_256M);

                        output.accept(DEItems.DATA_SOLAR_PANEL);
                        output.accept(DEItems.DATA_EXTRACTOR);
                        output.accept(DEItems.DATA_RIPPER_REASSEMBLER);
                        output.accept(DEItems.DATA_FRAMEWORK);
                        output.accept(DEItems.DATA_DISTRIBUTION_TOWER);
                        output.accept(DEItems.DATA_MIMETIC_FIELD);
                        output.accept(DEItems.DATA_TELEPORT_ANCHOR);
                        output.accept(DEItems.DATA_SANCTUM);
                        output.accept(DEItems.DATA_SANCTUM_INTERFACE);
                        output.accept(DEItems.DATA_CHARGER);
                        output.accept(DEItems.EXTENDED_DATA_CHARGER);
                        output.accept(DEItems.DATA_INTEGRATED_CHARGER);
                        output.accept(DEItems.DATA_SANCTUM_INTERFACE_PART);
                        output.accept(DEItems.DATA_SANCTUM_INTERFACE_UPGRADE);
                        output.accept(DEItems.ADAPTIVE_PATTERN_PROVIDER);
                        output.accept(DEItems.ADAPTIVE_PATTERN_PROVIDER_UPGRADE);
                        output.accept(DEItems.TNT_CONFIGURABLE);
                        output.accept(DEItems.DATA_NUKE);
                        output.accept(DEItems.RESIDUAL_DATA_ORE);
                        output.accept(DEItems.ENDER_COHESION_METEORITE_0);
                        output.accept(DEItems.ENDER_COHESION_METEORITE_1);
                        output.accept(DEItems.ENDER_COHESION_METEORITE_2);
                        output.accept(DEItems.DATA_METEORITE_COMPASS);
                        output.accept(DEItems.DATA_CRYSTAL_BLOCK);
                        output.accept(DEItems.TUNING_FORK_BASE);
                        output.accept(DEItems.RESONANCE_DIGITALIZATION_CORE);
                        output.accept(DEItems.AMETHYST_TUNING_FORK);
                        output.accept(DEItems.DATA_TUNING_FORK);
                        output.accept(DEItems.RESONANCE_TUNING_FORK);
                        output.accept(DEItems.RESONANCE_CRYSTAL_BLOCK);
                        output.accept(DEItems.SMALL_RESONANCE_CRYSTAL_BUD);
                        output.accept(DEItems.MEDIUM_RESONANCE_CRYSTAL_BUD);
                        output.accept(DEItems.LARGE_RESONANCE_CRYSTAL_BUD);
                        output.accept(DEItems.RESONANCE_CRYSTAL_CLUSTER);
                        output.accept(DEItems.RESONANCE_CRYSTAL);
                        output.accept(DEItems.DIGITAL_STORAGE_DEPOT);
                        output.accept(DEItems.BUDDING_DATA_CRYSTAL_0);
                        output.accept(DEItems.BUDDING_DATA_CRYSTAL_1);
                        output.accept(DEItems.BUDDING_DATA_CRYSTAL_2);
                        output.accept(DEItems.BUDDING_DATA_CRYSTAL_3);
                        output.accept(DEItems.BUDDING_DATA_CRYSTAL_4);
                        output.accept(DEItems.SMALL_DATA_CRYSTAL_BUD);
                        output.accept(DEItems.MEDIUM_DATA_CRYSTAL_BUD);
                        output.accept(DEItems.LARGE_DATA_CRYSTAL_BUD);
                        output.accept(DEItems.DATA_CRYSTAL_CLUSTER);
                        output.accept(DEItems.ADAPTIVE_PATTERN_PROVIDER_PART);
                        output.accept(DEItems.ME_SOLAR_PANEL_PART);
                        output.accept(DEItems.UNIVERSAL_TERMINAL);
                        output.accept(DEItems.DATA_CRYSTAL);
                        output.accept(DEItems.DATA_CRYSTAL_SWORD);
                        output.accept(DEItems.DATA_CRYSTAL_AXE);
                        output.accept(DEItems.DATA_CRYSTAL_PICKAXE);
                        output.accept(DEItems.DATA_CRYSTAL_HOE);
                        output.accept(DEItems.DATA_CRYSTAL_SHOVEL);
                        output.accept(DEItems.DATA_CRYSTAL_CUTTING_KNIFE);
                        output.accept(DEItems.DATA_LIGHT_SABER);
                        output.accept(DEItems.DATA_SANCTIFIER);
                        output.accept(DEItems.CARD_SABER_ENERGY);
                        output.accept(DEItems.REDSTONE_TUNING_CARD);
                        output.accept(DEItems.SOLIDIFIED_OBSIDIAN);
                        output.accept(DEItems.DATA_DUST);
                        output.accept(DEItems.OBSIDIAN_DUST);
                        output.accept(DEFluids.ENDER_BUCKET);
                        output.accept(DEFluids.DATA_CORROSION_LIQUID_BUCKET);
                        output.accept(DEItems.DATA_CARRIER);
                        output.accept(DEItems.MOB_DATA_CARRIER);
                        output.accept(DEItems.CROP_DATA_CARRIER);
                        output.accept(DEItems.ORE_DATA_CARRIER);
                        output.accept(DEItems.TIME_CORE);
                        output.accept(DEItems.ME_VACUUM);
                        output.accept(DEItems.ORDER_PACKAGE);
                        output.accept(DEItems.DATA_FLOW_COMPONENT_HOUSING);
                        output.accept(DEItems.DATA_DISTRIBUTION_CONNECTOR);
                        output.accept(DEItems.DATA_INSCRIBER_TEMPLATE);
                        output.accept(DEItems.DATA_CIRCUIT_BOARD);
                        output.accept(DEItems.DATA_PROCESSOR);
                        output.accept(DEItems.COMPLEXIFIED_BIOCHIPS);
                        output.accept(DEItems.DIGISIDIAN_MEMORIZE_INGOT);
                        output.accept(RadixContainmentSphereItem.createChargedStack());
                        output.accept(DEItems.MATTER_CONVERGING_CROSSBOW);
                        output.accept(DEItems.DATA_RIPPER);
                        output.accept(DEItems.FISH_DAN);
                        output.accept(DEItems.QIUYEQAQ2024);
                        output.accept(DEItems.TED_XENON);
                    })
                    .withTabsAfter(MULTIBLOCK_TAB_KEY)
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS.location())
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MULTIBLOCK_TAB = CREATIVE_MODE_TABS.register(
            Data_Energistics.MODID + "_multiblock",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Data_Energistics.MODID + ".multiblock"))
                    .icon(DEItems.TRINITY_DATA_CORE::toStack)
                    .displayItems((parameters, output) -> acceptMultiblockItems(output))
                    .withTabsBefore(DATA_ENERGISTICS_TAB_KEY)
                    .build());

    private DECreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    private static void acceptMultiblockItems(CreativeModeTab.Output output) {
        output.accept(DEItems.TRINITY_DATA_CORE);
        output.accept(DEItems.COMPOSITE_INPUT_WAREHOUSE);
        output.accept(DEItems.COMPOSITE_OUTPUT_WAREHOUSE);
        output.accept(DEItems.ME_COMPOSITE_INPUT_WAREHOUSE);
        output.accept(DEItems.ME_COMPOSITE_OUTPUT_WAREHOUSE);
        output.accept(DEItems.ME_PATTERN_BUFFER);
        output.accept(DEItems.TRINITY_INFORMATION_EXCHANGE_DEPOT);

        output.accept(DEItems.ME_DIGITAL_STORAGE_CORE_1K);
        output.accept(DEItems.ME_DIGITAL_STORAGE_CORE_4K);
        output.accept(DEItems.ME_DIGITAL_STORAGE_CORE_16K);
        output.accept(DEItems.ME_DIGITAL_STORAGE_CORE_64K);
        output.accept(DEItems.ME_DIGITAL_STORAGE_CORE_256K);
        output.accept(DEItems.ME_DIGITAL_STORAGE_CORE_1M);
        output.accept(DEItems.ME_DIGITAL_STORAGE_CORE_4M);
        output.accept(DEItems.ME_DIGITAL_STORAGE_CORE_16M);
        output.accept(DEItems.ME_DIGITAL_STORAGE_CORE_64M);
        output.accept(DEItems.ME_DIGITAL_STORAGE_CORE_256M);

        output.accept(DEItems.ME_DIGITAL_MERGED_STORAGE_CORE_1K);
        output.accept(DEItems.ME_DIGITAL_MERGED_STORAGE_CORE_4K);
        output.accept(DEItems.ME_DIGITAL_MERGED_STORAGE_CORE_16K);
        output.accept(DEItems.ME_DIGITAL_MERGED_STORAGE_CORE_64K);
        output.accept(DEItems.ME_DIGITAL_MERGED_STORAGE_CORE_256K);
        output.accept(DEItems.ME_DIGITAL_MERGED_STORAGE_CORE_1M);
        output.accept(DEItems.ME_DIGITAL_MERGED_STORAGE_CORE_4M);
        output.accept(DEItems.ME_DIGITAL_MERGED_STORAGE_CORE_16M);
        output.accept(DEItems.ME_DIGITAL_MERGED_STORAGE_CORE_64M);
        output.accept(DEItems.ME_DIGITAL_MERGED_STORAGE_CORE_256M);

        output.accept(DEItems.ME_DIGITAL_PATTERN_PROCESSING_CORE);
        output.accept(DEItems.EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE);
        output.accept(DEItems.OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE);
    }
}
