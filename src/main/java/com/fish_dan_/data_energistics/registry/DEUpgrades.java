package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.ae2.cell.InfiniteDataCellHandler;
import com.fish_dan_.data_energistics.ae2.dataflow.DataFlowBusStrategies;
import com.fish_dan_.data_energistics.ae2.dataflow.DataFlowCellHandler;
import com.fish_dan_.data_energistics.block.AdaptivePatternProviderBlock;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.part.DataRipperPart;
import com.fish_dan_.data_energistics.part.DataSanctumInterfacePart;
import com.fish_dan_.data_energistics.part.MeSolarPanelPart;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

import appeng.api.parts.PartModels;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModelsHelper;

public final class DEUpgrades {

    private static final String ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP = "block.data_energistics.adaptive_pattern_provider";
    private static final String DATA_SANCTUM_INTERFACE_UPGRADE_TOOLTIP_GROUP = "block.data_energistics.data_sanctum_interface";
    private static final String DATA_SANCTUM_INTERFACE_PART_UPGRADE_TOOLTIP_GROUP = "item.data_energistics.data_sanctum_interface_part";

    private DEUpgrades() {}

    public static void init() {
        DataFlowBusStrategies.register();
        ((AdaptivePatternProviderBlock<?>) DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get()).bindBlockEntity();
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_SOLAR_PANEL_BLOCK_ENTITY.get(), DEBlocks.DATA_SOLAR_PANEL.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(), DEBlocks.DIGITAL_STORAGE_DEPOT.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), DEBlocks.DATA_EXTRACTOR.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(), DEBlocks.DATA_RIPPER_REASSEMBLER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.TRINITY_DATA_CORE_BLOCK_ENTITY.get(), DEBlocks.TRINITY_DATA_CORE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), DEBlocks.DATA_DISTRIBUTION_TOWER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), DEBlocks.DATA_MIMETIC_FIELD.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_TELEPORT_ANCHOR_BLOCK_ENTITY.get(), DEBlocks.DATA_TELEPORT_ANCHOR.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_SANCTUM_BLOCK_ENTITY.get(), DEBlocks.DATA_SANCTUM.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.DATA_SANCTUM_INTERFACE_BLOCK_ENTITY.get(), DEBlocks.DATA_SANCTUM_INTERFACE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(), DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.COMPOSITE_WAREHOUSE_BLOCK_ENTITY.get(), DEBlocks.COMPOSITE_INPUT_WAREHOUSE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.COMPOSITE_WAREHOUSE_BLOCK_ENTITY.get(), DEBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ME_COMPOSITE_INPUT_WAREHOUSE_BLOCK_ENTITY.get(), DEBlocks.ME_COMPOSITE_INPUT_WAREHOUSE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ME_COMPOSITE_OUTPUT_WAREHOUSE_BLOCK_ENTITY.get(), DEBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.ME_PATTERN_BUFFER_BLOCK_ENTITY.get(), DEBlocks.ME_PATTERN_BUFFER.get().asItem());
        AEBaseBlockEntity.registerBlockEntityItem(DEBlockEntities.TRINITY_INFORMATION_EXCHANGE_DEPOT_BLOCK_ENTITY.get(), DEBlocks.TRINITY_INFORMATION_EXCHANGE_DEPOT.get().asItem());
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.DATA_RIPPER.get(), 8, "item.data_energistics.data_ripper");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.DATA_RIPPER.get(), 5, "item.data_energistics.data_ripper");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_RIPPER.get(), 5, "item.data_energistics.data_ripper");
        Upgrades.add(AEItems.INVERTER_CARD, DEItems.DATA_RIPPER.get(), 5, "item.data_energistics.data_ripper");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DATA_FLOW_CELL_1K.get(), 3,
                "item.data_energistics.portable_data_flow_cell_1k");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DATA_FLOW_CELL_4K.get(), 3,
                "item.data_energistics.portable_data_flow_cell_4k");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DATA_FLOW_CELL_16K.get(), 3,
                "item.data_energistics.portable_data_flow_cell_16k");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DATA_FLOW_CELL_64K.get(), 3,
                "item.data_energistics.portable_data_flow_cell_64k");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DATA_FLOW_CELL_256K.get(), 3,
                "item.data_energistics.portable_data_flow_cell_256k");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DATA_FLOW_CELL_1M.get(), 3,
                "item.data_energistics.portable_data_flow_cell_1m");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DATA_FLOW_CELL_4M.get(), 3,
                "item.data_energistics.portable_data_flow_cell_4m");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DATA_FLOW_CELL_16M.get(), 3,
                "item.data_energistics.portable_data_flow_cell_16m");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DATA_FLOW_CELL_64M.get(), 3,
                "item.data_energistics.portable_data_flow_cell_64m");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.PORTABLE_DATA_FLOW_CELL_256M.get(), 3,
                "item.data_energistics.portable_data_flow_cell_256m");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.RADIX_CONTAINMENT_SPHERE.get(), 3,
                "item.data_energistics.radix_containment_sphere");
        Upgrades.add(AEItems.FUZZY_CARD, DEItems.RADIX_CONTAINMENT_SPHERE.get(), 1,
                "item.data_energistics.radix_containment_sphere");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.ME_VACUUM.get(), 3,
                "item.data_energistics.me_vacuum");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.DATA_CRYSTAL_SWORD.get(), 3,
                "item.data_energistics.data_crystal_sword");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.DATA_CRYSTAL_SWORD.get(), 3,
                "item.data_energistics.data_crystal_sword");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_CRYSTAL_SWORD.get(), 1,
                "item.data_energistics.data_crystal_sword");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.DATA_CRYSTAL_AXE.get(), 3,
                "item.data_energistics.data_crystal_axe");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.DATA_CRYSTAL_AXE.get(), 3,
                "item.data_energistics.data_crystal_axe");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_CRYSTAL_AXE.get(), 1,
                "item.data_energistics.data_crystal_axe");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.DATA_CRYSTAL_PICKAXE.get(), 3,
                "item.data_energistics.data_crystal_pickaxe");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.DATA_CRYSTAL_PICKAXE.get(), 3,
                "item.data_energistics.data_crystal_pickaxe");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_CRYSTAL_PICKAXE.get(), 1,
                "item.data_energistics.data_crystal_pickaxe");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.DATA_CRYSTAL_HOE.get(), 3,
                "item.data_energistics.data_crystal_hoe");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.DATA_CRYSTAL_HOE.get(), 3,
                "item.data_energistics.data_crystal_hoe");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_CRYSTAL_HOE.get(), 1,
                "item.data_energistics.data_crystal_hoe");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.DATA_CRYSTAL_SHOVEL.get(), 3,
                "item.data_energistics.data_crystal_shovel");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.DATA_CRYSTAL_SHOVEL.get(), 3,
                "item.data_energistics.data_crystal_shovel");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_CRYSTAL_SHOVEL.get(), 1,
                "item.data_energistics.data_crystal_shovel");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.DATA_CRYSTAL_CUTTING_KNIFE.get(), 3,
                "item.data_energistics.data_crystal_cutting_knife");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_CRYSTAL_CUTTING_KNIFE.get(), 1,
                "item.data_energistics.data_crystal_cutting_knife");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.DATA_LIGHT_SABER.get(), 3,
                "item.data_energistics.data_light_saber");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.DATA_LIGHT_SABER.get(), 3,
                "item.data_energistics.data_light_saber");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_LIGHT_SABER.get(), 1,
                "item.data_energistics.data_light_saber");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.DATA_SANCTIFIER.get(), 3,
                "item.data_energistics.data_sanctifier");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.DATA_SANCTIFIER.get(), 3,
                "item.data_energistics.data_sanctifier");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.DATA_SANCTIFIER.get(), 1,
                "item.data_energistics.data_sanctifier");
        Upgrades.add(AEItems.ENERGY_CARD, DEBlocks.DATA_EXTRACTOR.get(), 7, "block.data_energistics.data_extractor");
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.DATA_EXTRACTOR.get(), 7, "block.data_energistics.data_extractor");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.DATA_EXTRACTOR.get(), 5, "block.data_energistics.data_extractor");
        Upgrades.add(AEItems.FUZZY_CARD, DEBlocks.DATA_EXTRACTOR.get(), 1, "block.data_energistics.data_extractor");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEBlocks.DATA_RIPPER_REASSEMBLER.get(), 2, "block.data_energistics.data_reassembler");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.DATA_RIPPER_REASSEMBLER.get(), 5, "block.data_energistics.data_reassembler");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.DATA_SOLAR_PANEL.get(), 3, "block.data_energistics.me_solar_panel");
        Upgrades.add(AEItems.ENERGY_CARD, DEBlocks.DATA_SOLAR_PANEL.get(), 3, "block.data_energistics.me_solar_panel");
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.DIGITAL_STORAGE_DEPOT.get(), 4, "block.data_energistics.digital_storage_depot");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.ME_SOLAR_PANEL_PART.get(), 3, "item.data_energistics.me_solar_panel_part");
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.ME_SOLAR_PANEL_PART.get(), 3, "item.data_energistics.me_solar_panel_part");
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.DATA_MIMETIC_FIELD.get(), 1, "block.data_energistics.data_mimetic_field");
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.DATA_MIMETIC_FIELD.get(), 4, "block.data_energistics.data_mimetic_field");
        Upgrades.add(AEItems.VOID_CARD, DEBlocks.DATA_MIMETIC_FIELD.get(), 1, "block.data_energistics.data_mimetic_field");
        Upgrades.add(AEItems.ENERGY_CARD, DEBlocks.DATA_SANCTUM.get(), 3, "block.data_energistics.data_sanctum");
        registerCompartmentCapacityUpgrade();
        registerDataSanctumInterfaceUpgrade(AEItems.CAPACITY_CARD, 3);
        registerDataSanctumInterfaceUpgrade(AEItems.CRAFTING_CARD, 1);
        registerDataSanctumInterfaceUpgrade(AEItems.FUZZY_CARD, 1);
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 3, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.CAPACITY_CARD, DEItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 3, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.SPEED_CARD, DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 4, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.SPEED_CARD, DEItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 4, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(AEItems.ENERGY_CARD, DEItems.MATTER_CONVERGING_CROSSBOW.get(), 2,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(AEItems.FUZZY_CARD, DEItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(AEItems.INVERTER_CARD, DEItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(AEItems.VOID_CARD, DEItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(AEItems.SPEED_CARD, DEItems.MATTER_CONVERGING_CROSSBOW.get(), 4,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(DEItems.CARD_SABER_ENERGY.get(), DEItems.MATTER_CONVERGING_CROSSBOW.get(), 2,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), DEItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                "item.data_energistics.matter_converging_crossbow");
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), AEBlocks.PATTERN_PROVIDER.block(), 1, "block.ae2.pattern_provider");
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), DEItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1,
                ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        registerExternalRedstoneTuningCardCompat();
        registerExternalDataSanctumInterfaceCompat();
        registerAppliedFluxAdaptivePatternProviderCompat();
        registerAe2CrystalScienceAdaptivePatternProviderCompat();
        StorageCells.addCellHandler(DataFlowCellHandler.INSTANCE);
        StorageCells.addCellHandler(InfiniteDataCellHandler.INSTANCE);
    }

    private static void registerCompartmentCapacityUpgrade() {
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.COMPOSITE_INPUT_WAREHOUSE.get(), 5, "block.data_energistics.composite_input_warehouse");
        Upgrades.add(AEItems.CAPACITY_CARD, DEBlocks.COMPOSITE_OUTPUT_WAREHOUSE.get(), 5, "block.data_energistics.composite_output_warehouse");
    }

    public static void registerPartModels() {
        PartModels.registerModels(
                PartModelsHelper.createModels(DataRipperPart.class));
        PartModels.registerModels(
                PartModelsHelper.createModels(AdaptivePatternProviderPart.class));
        PartModels.registerModels(
                PartModelsHelper.createModels(DataSanctumInterfacePart.class));
        PartModels.registerModels(
                PartModelsHelper.createModels(MeSolarPanelPart.class));
        PartModels.registerModels(
                PartModelsHelper.createModels(UniversalTerminalPart.class));
    }

    private static void registerDataSanctumInterfaceUpgrade(ItemLike upgradeCard, int maxInstalled) {
        Upgrades.add(upgradeCard, DEBlocks.DATA_SANCTUM_INTERFACE.get(), maxInstalled,
                DATA_SANCTUM_INTERFACE_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(upgradeCard, DEItems.DATA_SANCTUM_INTERFACE_PART.get(), maxInstalled,
                DATA_SANCTUM_INTERFACE_PART_UPGRADE_TOOLTIP_GROUP);
    }

    private static void registerExternalDataSanctumInterfaceCompat() {
        registerExternalDataSanctumInterfaceUpgrade("ae2cs", "crystal_growth_card", 1);
        registerExternalDataSanctumInterfaceUpgrade("appflux", "induction_card", 1);
        registerExternalDataSanctumInterfaceUpgrade("extendedae_plus", "channel_card", 1);
    }

    private static void registerExternalDataSanctumInterfaceUpgrade(String namespace, String path, int maxInstalled) {
        Item upgradeCard = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
        if (upgradeCard == null || upgradeCard == Items.AIR) {
            return;
        }
        registerDataSanctumInterfaceUpgrade(upgradeCard, maxInstalled);
    }

    private static void registerAppliedFluxAdaptivePatternProviderCompat() {
        Item inductionCard = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("appflux", "induction_card"));
        if (inductionCard == null || inductionCard == Items.AIR) {
            return;
        }

        Upgrades.add(inductionCard, DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(inductionCard, DEItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
    }

    private static void registerAe2CrystalScienceAdaptivePatternProviderCompat() {
        Item crystalGrowthCard = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2cs", "crystal_growth_card"));
        if (crystalGrowthCard == null || crystalGrowthCard == Items.AIR) {
            return;
        }

        Upgrades.add(crystalGrowthCard, DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1,
                ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(crystalGrowthCard, DEItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1,
                ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
    }

    private static void registerExternalRedstoneTuningCardCompat() {
        registerExternalRedstoneTuningTarget("advanced_ae", "small_adv_pattern_provider", "block.advanced_ae.small_adv_pattern_provider");
        registerExternalRedstoneTuningTarget("advanced_ae", "adv_pattern_provider", "block.advanced_ae.adv_pattern_provider");
        registerExternalRedstoneTuningItemTarget("advanced_ae", "small_adv_pattern_provider_part",
                "block.advanced_ae.small_adv_pattern_provider");
        registerExternalRedstoneTuningItemTarget("advanced_ae", "adv_pattern_provider_part",
                "block.advanced_ae.adv_pattern_provider");
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
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), block, 1, tooltipKey);
    }

    private static void registerExternalRedstoneTuningItemTarget(String namespace, String path, String tooltipKey) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
        if (item == null || item == Items.AIR) {
            return;
        }
        Upgrades.add(DEItems.REDSTONE_TUNING_CARD.get(), item, 1, tooltipKey);
    }
}
