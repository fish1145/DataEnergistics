package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.ae2.DataFlowBusStrategies;
import com.fish_dan_.data_energistics.ae2.InfiniteDataCellHandler;
import com.fish_dan_.data_energistics.block.AdaptivePatternProviderBlock;
import com.fish_dan_.data_energistics.config.DataExtractorRuleTable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import appeng.api.parts.PartModels;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModelsHelper;

public final class ModUpgrades {

    private static final String ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP = "block.data_energistics.adaptive_pattern_provider";

    private ModUpgrades() {}

    public static void init() {
        DataExtractorRuleTable.load();
        UniversalTerminalAdapters.discoverFromRegisteredItems();
        DataFlowBusStrategies.register();
        ((AdaptivePatternProviderBlock<?>) ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get()).bindBlockEntity();
        AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_SOLAR_PANEL_BLOCK_ENTITY.get(), ModBlocks.DATA_SOLAR_PANEL.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(), ModBlocks.DIGITAL_STORAGE_DEPOT.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), ModBlocks.DATA_EXTRACTOR.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(), ModBlocks.DATA_RIPPER_REASSEMBLER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_FRAMEWORK_BLOCK_ENTITY.get(), ModBlocks.DATA_FRAMEWORK.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), ModBlocks.DATA_DISTRIBUTION_TOWER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), ModBlocks.DATA_MIMETIC_FIELD.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_TELEPORT_ANCHOR_BLOCK_ENTITY.get(), ModBlocks.DATA_TELEPORT_ANCHOR.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_SANCTUM_BLOCK_ENTITY.get(), ModBlocks.DATA_SANCTUM.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(), ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get().asItem());
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_RIPPER.get(), 8, "item.data_energistics.data_ripper");
        Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_RIPPER.get(), 4, "item.data_energistics.data_ripper");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.PORTABLE_DATA_FLOW_CELL_1K.get(), 3,
                "item.data_energistics.portable_data_flow_cell_1k");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.PORTABLE_DATA_FLOW_CELL_4K.get(), 3,
                "item.data_energistics.portable_data_flow_cell_4k");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.PORTABLE_DATA_FLOW_CELL_16K.get(), 3,
                "item.data_energistics.portable_data_flow_cell_16k");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.PORTABLE_DATA_FLOW_CELL_64K.get(), 3,
                "item.data_energistics.portable_data_flow_cell_64k");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.PORTABLE_DATA_FLOW_CELL_256K.get(), 3,
                "item.data_energistics.portable_data_flow_cell_256k");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CAPTURE_BALL.get(), 3,
                "item.data_energistics.data_capture_ball");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_SWORD.get(), 3,
                "item.data_energistics.data_crystal_sword");
        Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_CRYSTAL_SWORD.get(), 3,
                "item.data_energistics.data_crystal_sword");
        Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_SWORD.get(), 1,
                "item.data_energistics.data_crystal_sword");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_AXE.get(), 3,
                "item.data_energistics.data_crystal_axe");
        Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_CRYSTAL_AXE.get(), 3,
                "item.data_energistics.data_crystal_axe");
        Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_AXE.get(), 1,
                "item.data_energistics.data_crystal_axe");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_PICKAXE.get(), 3,
                "item.data_energistics.data_crystal_pickaxe");
        Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_CRYSTAL_PICKAXE.get(), 3,
                "item.data_energistics.data_crystal_pickaxe");
        Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_PICKAXE.get(), 1,
                "item.data_energistics.data_crystal_pickaxe");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_HOE.get(), 3,
                "item.data_energistics.data_crystal_hoe");
        Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_CRYSTAL_HOE.get(), 3,
                "item.data_energistics.data_crystal_hoe");
        Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_HOE.get(), 1,
                "item.data_energistics.data_crystal_hoe");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_SHOVEL.get(), 3,
                "item.data_energistics.data_crystal_shovel");
        Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_CRYSTAL_SHOVEL.get(), 3,
                "item.data_energistics.data_crystal_shovel");
        Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_SHOVEL.get(), 1,
                "item.data_energistics.data_crystal_shovel");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_CUTTING_KNIFE.get(), 3,
                "item.data_energistics.data_crystal_cutting_knife");
        Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_CUTTING_KNIFE.get(), 1,
                "item.data_energistics.data_crystal_cutting_knife");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_LIGHT_SABER.get(), 3,
                "item.data_energistics.data_light_saber");
        Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_LIGHT_SABER.get(), 3,
                "item.data_energistics.data_light_saber");
        Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_LIGHT_SABER.get(), 1,
                "item.data_energistics.data_light_saber");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_SANCTIFIER.get(), 3,
                "item.data_energistics.data_sanctifier");
        Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_SANCTIFIER.get(), 3,
                "item.data_energistics.data_sanctifier");
        Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_SANCTIFIER.get(), 1,
                "item.data_energistics.data_sanctifier");
        Upgrades.add(AEItems.ENERGY_CARD, ModBlocks.DATA_EXTRACTOR.get(), 6, "block.data_energistics.data_extractor");
        Upgrades.add(AEItems.CAPACITY_CARD, ModBlocks.DATA_EXTRACTOR.get(), 6, "block.data_energistics.data_extractor");
        Upgrades.add(AEItems.SPEED_CARD, ModBlocks.DATA_EXTRACTOR.get(), 5, "block.data_energistics.data_extractor");
        Upgrades.add(AEItems.SPEED_CARD, ModBlocks.DATA_RIPPER_REASSEMBLER.get(), 4, "block.data_energistics.data_reassembler");
        Upgrades.add(AEItems.SPEED_CARD, ModBlocks.DATA_SOLAR_PANEL.get(), 3, "block.data_energistics.me_solar_panel");
        Upgrades.add(AEItems.ENERGY_CARD, ModBlocks.DATA_SOLAR_PANEL.get(), 3, "block.data_energistics.me_solar_panel");
        Upgrades.add(AEItems.CAPACITY_CARD, ModBlocks.DIGITAL_STORAGE_DEPOT.get(), 4, "block.data_energistics.digital_storage_depot");
        Upgrades.add(AEItems.SPEED_CARD, ModItems.ME_SOLAR_PANEL_PART.get(), 3, "item.data_energistics.me_solar_panel_part");
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.ME_SOLAR_PANEL_PART.get(), 3, "item.data_energistics.me_solar_panel_part");
        Upgrades.add(AEItems.CAPACITY_CARD, ModBlocks.DATA_MIMETIC_FIELD.get(), 1, "block.data_energistics.data_mimetic_field");
        Upgrades.add(AEItems.SPEED_CARD, ModBlocks.DATA_MIMETIC_FIELD.get(), 4, "block.data_energistics.data_mimetic_field");
        Upgrades.add(AEItems.VOID_CARD, ModBlocks.DATA_MIMETIC_FIELD.get(), 1, "block.data_energistics.data_mimetic_field");
        Upgrades.add(AEItems.ENERGY_CARD, ModBlocks.DATA_SANCTUM.get(), 3, "block.data_energistics.data_sanctum");
        Upgrades.add(AEItems.CAPACITY_CARD, ModBlocks.DATA_SANCTUM_INTERFACE.get(), 3,
                "block.data_energistics.data_sanctum_interface");
        Upgrades.add(AEItems.CAPACITY_CARD, ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 3, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.CAPACITY_CARD, ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 3, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.SPEED_CARD, ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 4, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.SPEED_CARD, ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 4, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.ENERGY_CARD, ModItems.MATTER_CONVERGING_CROSSBOW.get(), 2,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(AEItems.FUZZY_CARD, ModItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(AEItems.INVERTER_CARD, ModItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(AEItems.VOID_CARD, ModItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(AEItems.SPEED_CARD, ModItems.MATTER_CONVERGING_CROSSBOW.get(), 4,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.MATTER_CONVERGING_CROSSBOW.get(), 2,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), ModItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), AEBlocks.PATTERN_PROVIDER.block(), 1, "block.ae2.pattern_provider");
        Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1,
                ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        registerExternalRedstoneTuningCardCompat();
        registerAppliedFluxAdaptivePatternProviderCompat();
        registerAe2CrystalScienceAdaptivePatternProviderCompat();
        PartModels.registerModels(
                PartModelsHelper.createModels(ModItems.DATA_RIPPER.get().getPartClass()));
        PartModels.registerModels(
                PartModelsHelper.createModels(ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get().getPartClass()));
        PartModels.registerModels(
                PartModelsHelper.createModels(ModItems.ME_SOLAR_PANEL_PART.get().getPartClass()));
        PartModels.registerModels(
                PartModelsHelper.createModels(ModItems.UNIVERSAL_TERMINAL.get().getPartClass()));
        StorageCells.addCellHandler(InfiniteDataCellHandler.INSTANCE);
    }

    private static void registerAppliedFluxAdaptivePatternProviderCompat() {
        Item inductionCard = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("appflux", "induction_card"));
        if (inductionCard == null || inductionCard == Items.AIR) {
            return;
        }

        Upgrades.add(inductionCard, ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(inductionCard, ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
    }

    private static void registerAe2CrystalScienceAdaptivePatternProviderCompat() {
        Item crystalGrowthCard = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2cs", "crystal_growth_card"));
        if (crystalGrowthCard == null || crystalGrowthCard == Items.AIR) {
            return;
        }

        Upgrades.add(crystalGrowthCard, ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1,
                ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(crystalGrowthCard, ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1,
                ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
    }

    private static void registerExternalRedstoneTuningCardCompat() {
        registerExternalRedstoneTuningTarget("advanced_ae", "small_adv_pattern_provider", "block.advanced_ae.small_adv_pattern_provider");
        registerExternalRedstoneTuningTarget("advanced_ae", "adv_pattern_provider", "block.advanced_ae.adv_pattern_provider");
        registerExternalRedstoneTuningItemTarget("advanced_ae", "small_adv_pattern_provider_part",
                "block.advanced_ae.small_adv_pattern_provider");
        registerExternalRedstoneTuningItemTarget("advanced_ae", "adv_pattern_provider_part",
                "block.advanced_ae.adv_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2lt", "overloaded_pattern_provider", "block.ae2lt.overloaded_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2cs", "simple_pattern_provider", "block.ae2cs.simple_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2cs", "resonating_pattern_provider", "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2cs", "extended_resonating_pattern_provider",
                "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2cs", "ex_resonating_pattern_provider",
                "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2cs", "meteorite_pattern_provider", "block.ae2cs.meteorite_pattern_provider");
        registerExternalRedstoneTuningItemTarget("ae2cs", "simple_pattern_provider_part",
                "block.ae2cs.simple_pattern_provider");
        registerExternalRedstoneTuningItemTarget("ae2cs", "resonating_pattern_provider_part",
                "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningItemTarget("ae2cs", "extended_resonating_pattern_provider_part",
                "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningItemTarget("ae2cs", "ex_resonating_pattern_provider_part",
                "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningItemTarget("ae2cs", "meteorite_pattern_provider_part",
                "block.ae2cs.meteorite_pattern_provider");
        registerExternalRedstoneTuningTarget("appliedcreate", "andesite_pattern_provider", "block.appliedcreate.andesite_pattern_provider");
        registerExternalRedstoneTuningTarget("appliedcreate", "brass_pattern_provider", "block.appliedcreate.brass_pattern_provider");
        registerExternalRedstoneTuningItemTarget("appliedcreate", "andesite_pattern_provider_part",
                "block.appliedcreate.andesite_pattern_provider");
        registerExternalRedstoneTuningItemTarget("appliedcreate", "brass_pattern_provider_part",
                "block.appliedcreate.brass_pattern_provider");
        registerExternalRedstoneTuningTarget("extendedae", "ex_pattern_provider", "block.extendedae.ex_pattern_provider");
        registerExternalRedstoneTuningItemTarget("extendedae", "ex_pattern_provider_part",
                "block.extendedae.ex_pattern_provider");
        registerExternalRedstoneTuningTarget("megacells", "mega_pattern_provider", "block.megacells.mega_pattern_provider");
        registerExternalRedstoneTuningTarget("extendedae_plus", "mirror_pattern_provider", "block.extendedae_plus.mirror_pattern_provider");
    }

    private static void registerExternalRedstoneTuningTarget(String namespace, String path, String tooltipKey) {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
        if (block == null || block == Blocks.AIR) {
            return;
        }
        Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), block, 1, tooltipKey);
    }

    private static void registerExternalRedstoneTuningItemTarget(String namespace, String path, String tooltipKey) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
        if (item == null || item == Items.AIR) {
            return;
        }
        Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), item, 1, tooltipKey);
    }
}
