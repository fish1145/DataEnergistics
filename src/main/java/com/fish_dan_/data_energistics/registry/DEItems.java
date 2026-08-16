package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.item.carrier.BiologyDataCarrierItem;
import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;
import com.fish_dan_.data_energistics.item.cell.DataStorageComponentItem;
import com.fish_dan_.data_energistics.item.cell.DigitalStorageCellItem;
import com.fish_dan_.data_energistics.item.cell.InfiniteDataCellItem;
import com.fish_dan_.data_energistics.item.cell.PortableDigitalStorageCellItem;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItem;
import com.fish_dan_.data_energistics.item.dataripper.DataRipperPartItem;
import com.fish_dan_.data_energistics.item.decor.DollBlockItem;
import com.fish_dan_.data_energistics.item.depot.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.item.meteorite.DataMeteoriteCompassItem;
import com.fish_dan_.data_energistics.item.order.OrderPackageItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.PoweredAxeItem;
import com.fish_dan_.data_energistics.item.powered.PoweredCuttingKnifeItem;
import com.fish_dan_.data_energistics.item.powered.PoweredHoeItem;
import com.fish_dan_.data_energistics.item.powered.PoweredPickaxeItem;
import com.fish_dan_.data_energistics.item.powered.PoweredShovelItem;
import com.fish_dan_.data_energistics.item.powered.PoweredSwordItem;
import com.fish_dan_.data_energistics.item.terminal.UniversalTerminalPartItem;
import com.fish_dan_.data_energistics.item.upgrade.AdaptivePatternProviderUpgradeItem;
import com.fish_dan_.data_energistics.item.upgrade.DataSanctumInterfaceUpgradeItem;
import com.fish_dan_.data_energistics.item.vacuum.MeVacuumItem;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.part.DataSanctumInterfacePart;
import com.fish_dan_.data_energistics.part.MeSolarPanelPart;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.Upgrades;
import appeng.items.parts.PartItem;
import appeng.items.storage.StorageTier;

import java.util.List;

public final class DEItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Data_Energistics.MODID);
    private static final Tool NO_MINING_SWORD_TOOL = new Tool(List.of(), 1.0F, 2);
    private static final Tier DATA_CRYSTAL_TOOL_TIER = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2000,
            Tiers.GOLD.getSpeed(),
            Tiers.NETHERITE.getAttackDamageBonus(),
            Tiers.NETHERITE.getEnchantmentValue(),
            () -> Ingredient.EMPTY);
    private static final Tier DATA_CRYSTAL_SWORD_TIER = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2000,
            Tiers.NETHERITE.getSpeed(),
            Tiers.NETHERITE.getAttackDamageBonus(),
            Tiers.NETHERITE.getEnchantmentValue(),
            () -> Ingredient.EMPTY);

    public static final DeferredItem<DigitalStorageCellItem> DIGITAL_STORAGE_CELL_1K = registerDigitalStorageCell("digital_storage_cell_1k", 0.5, 1);
    public static final DeferredItem<DigitalStorageCellItem> DIGITAL_STORAGE_CELL_4K = registerDigitalStorageCell("digital_storage_cell_4k", 1.0, 4);
    public static final DeferredItem<DigitalStorageCellItem> DIGITAL_STORAGE_CELL_16K = registerDigitalStorageCell("digital_storage_cell_16k", 1.5, 16);
    public static final DeferredItem<DigitalStorageCellItem> DIGITAL_STORAGE_CELL_64K = registerDigitalStorageCell("digital_storage_cell_64k", 2.5, 64);
    public static final DeferredItem<DigitalStorageCellItem> DIGITAL_STORAGE_CELL_256K = registerDigitalStorageCell("digital_storage_cell_256k", 3.0, 256);
    public static final DeferredItem<DigitalStorageCellItem> DIGITAL_STORAGE_CELL_1M = registerDigitalStorageCell("digital_storage_cell_1m", 3.5, 1024);
    public static final DeferredItem<DigitalStorageCellItem> DIGITAL_STORAGE_CELL_4M = registerDigitalStorageCell("digital_storage_cell_4m", 4.0, 4096);
    public static final DeferredItem<DigitalStorageCellItem> DIGITAL_STORAGE_CELL_16M = registerDigitalStorageCell("digital_storage_cell_16m", 4.5, 16384);
    public static final DeferredItem<DigitalStorageCellItem> DIGITAL_STORAGE_CELL_64M = registerDigitalStorageCell("digital_storage_cell_64m", 5.0, 65536);
    public static final DeferredItem<DigitalStorageCellItem> DIGITAL_STORAGE_CELL_256M = registerDigitalStorageCell("digital_storage_cell_256m", 5.5, 262144);
    public static final DeferredItem<InfiniteDataCellItem> DATA_CELL_INFINITY = ITEMS.register(
            "data_cell_infinity",
            () -> new InfiniteDataCellItem(new Item.Properties()));

    public static final DeferredItem<PortableDigitalStorageCellItem> PORTABLE_DIGITAL_STORAGE_CELL_1K = registerPortableDigitalStorageCell("portable_digital_storage_cell_1k", StorageTier.SIZE_1K, 0x4FD8FF);
    public static final DeferredItem<PortableDigitalStorageCellItem> PORTABLE_DIGITAL_STORAGE_CELL_4K = registerPortableDigitalStorageCell("portable_digital_storage_cell_4k", StorageTier.SIZE_4K, 0x56F0B5);
    public static final DeferredItem<PortableDigitalStorageCellItem> PORTABLE_DIGITAL_STORAGE_CELL_16K = registerPortableDigitalStorageCell("portable_digital_storage_cell_16k", StorageTier.SIZE_16K, 0xA0EE68);
    public static final DeferredItem<PortableDigitalStorageCellItem> PORTABLE_DIGITAL_STORAGE_CELL_64K = registerPortableDigitalStorageCell("portable_digital_storage_cell_64k", StorageTier.SIZE_64K, 0xFF9B5C);
    public static final DeferredItem<PortableDigitalStorageCellItem> PORTABLE_DIGITAL_STORAGE_CELL_256K = registerPortableDigitalStorageCell("portable_digital_storage_cell_256k", StorageTier.SIZE_256K, 0xFF72C8);

    public static final DeferredItem<BlockItem> DATA_SOLAR_PANEL = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_SOLAR_PANEL);
    public static final DeferredItem<BlockItem> DATA_EXTRACTOR = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_EXTRACTOR);
    public static final DeferredItem<BlockItem> DATA_RIPPER_REASSEMBLER = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_RIPPER_REASSEMBLER);
    public static final DeferredItem<BlockItem> TRINITY_DATA_CORE = ITEMS.registerSimpleBlockItem(DEBlocks.TRINITY_DATA_CORE);
    public static final DeferredItem<BlockItem> DATA_FRAMEWORK = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_FRAMEWORK);
    public static final DeferredItem<BlockItem> DATA_DISTRIBUTION_TOWER = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_DISTRIBUTION_TOWER);
    public static final DeferredItem<BlockItem> DATA_MIMETIC_FIELD = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_MIMETIC_FIELD);
    public static final DeferredItem<BlockItem> DATA_TELEPORT_ANCHOR = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_TELEPORT_ANCHOR);
    public static final DeferredItem<BlockItem> DATA_SANCTUM = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_SANCTUM);
    public static final DeferredItem<BlockItem> DATA_SANCTUM_INTERFACE = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_SANCTUM_INTERFACE);
    public static final DeferredItem<BlockItem> DATA_CHARGER = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_CHARGER);
    public static final DeferredItem<BlockItem> EXTENDED_DATA_CHARGER = ITEMS.registerSimpleBlockItem(DEBlocks.EXTENDED_DATA_CHARGER);
    public static final DeferredItem<DataSanctumInterfaceUpgradeItem> DATA_SANCTUM_INTERFACE_UPGRADE = ITEMS.register(
            "data_sanctum_interface_upgrade",
            () -> new DataSanctumInterfaceUpgradeItem(new Item.Properties()));
    public static final DeferredItem<PartItem<DataSanctumInterfacePart>> DATA_SANCTUM_INTERFACE_PART = ITEMS.register(
            "data_sanctum_interface_part",
            () -> new PartItem<>(new Item.Properties(), DataSanctumInterfacePart.class, DataSanctumInterfacePart::new));
    public static final DeferredItem<BlockItem> ADAPTIVE_PATTERN_PROVIDER = ITEMS.registerSimpleBlockItem(DEBlocks.ADAPTIVE_PATTERN_PROVIDER);
    public static final DeferredItem<AdaptivePatternProviderUpgradeItem> ADAPTIVE_PATTERN_PROVIDER_UPGRADE = ITEMS.register(
            "adaptive_pattern_provider_upgrade",
            () -> new AdaptivePatternProviderUpgradeItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> TNT_CONFIGURABLE = ITEMS.registerSimpleBlockItem(DEBlocks.TNT_CONFIGURABLE);
    public static final DeferredItem<BlockItem> DATA_NUKE = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_NUKE);
    public static final DeferredItem<BlockItem> RESIDUAL_DATA_ORE = ITEMS.registerSimpleBlockItem(DEBlocks.RESIDUAL_DATA_ORE);
    public static final DeferredItem<BlockItem> ENDER_COHESION_METEORITE_0 = ITEMS.registerSimpleBlockItem(DEBlocks.ENDER_COHESION_METEORITE_0);
    public static final DeferredItem<BlockItem> ENDER_COHESION_METEORITE_1 = ITEMS.registerSimpleBlockItem(DEBlocks.ENDER_COHESION_METEORITE_1);
    public static final DeferredItem<BlockItem> ENDER_COHESION_METEORITE_2 = ITEMS.registerSimpleBlockItem(DEBlocks.ENDER_COHESION_METEORITE_2);
    public static final DeferredItem<BlockItem> DATA_MYSTERIOUS_CUBE = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_MYSTERIOUS_CUBE);
    public static final DeferredItem<DataMeteoriteCompassItem> DATA_METEORITE_COMPASS = ITEMS.register(
            "data_meteorite_compass",
            () -> new DataMeteoriteCompassItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> DATA_CRYSTAL_BLOCK = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_CRYSTAL_BLOCK);
    public static final DeferredItem<BlockItem> DIGITAL_STORAGE_DEPOT = ITEMS.register(
            "digital_storage_depot",
            () -> new DigitalStorageDepotBlockItem(DEBlocks.DIGITAL_STORAGE_DEPOT.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> ME_DIGITAL_STORAGE_CORE_1K = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_STORAGE_CORE_1K);
    public static final DeferredItem<BlockItem> ME_DIGITAL_STORAGE_CORE_4K = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_STORAGE_CORE_4K);
    public static final DeferredItem<BlockItem> ME_DIGITAL_STORAGE_CORE_16K = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_STORAGE_CORE_16K);
    public static final DeferredItem<BlockItem> ME_DIGITAL_STORAGE_CORE_64K = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_STORAGE_CORE_64K);
    public static final DeferredItem<BlockItem> ME_DIGITAL_STORAGE_CORE_256K = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_STORAGE_CORE_256K);
    public static final DeferredItem<BlockItem> ME_DIGITAL_STORAGE_CORE_1M = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_STORAGE_CORE_1M);
    public static final DeferredItem<BlockItem> ME_DIGITAL_STORAGE_CORE_4M = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_STORAGE_CORE_4M);
    public static final DeferredItem<BlockItem> ME_DIGITAL_STORAGE_CORE_16M = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_STORAGE_CORE_16M);
    public static final DeferredItem<BlockItem> ME_DIGITAL_STORAGE_CORE_64M = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_STORAGE_CORE_64M);
    public static final DeferredItem<BlockItem> ME_DIGITAL_STORAGE_CORE_256M = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_STORAGE_CORE_256M);
    public static final DeferredItem<BlockItem> ME_DIGITAL_MERGED_STORAGE_CORE_1K = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_1K);
    public static final DeferredItem<BlockItem> ME_DIGITAL_MERGED_STORAGE_CORE_4K = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_4K);
    public static final DeferredItem<BlockItem> ME_DIGITAL_MERGED_STORAGE_CORE_16K = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_16K);
    public static final DeferredItem<BlockItem> ME_DIGITAL_MERGED_STORAGE_CORE_64K = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_64K);
    public static final DeferredItem<BlockItem> ME_DIGITAL_MERGED_STORAGE_CORE_256K = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_256K);
    public static final DeferredItem<BlockItem> ME_DIGITAL_MERGED_STORAGE_CORE_1M = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_1M);
    public static final DeferredItem<BlockItem> ME_DIGITAL_MERGED_STORAGE_CORE_4M = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_4M);
    public static final DeferredItem<BlockItem> ME_DIGITAL_MERGED_STORAGE_CORE_16M = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_16M);
    public static final DeferredItem<BlockItem> ME_DIGITAL_MERGED_STORAGE_CORE_64M = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_64M);
    public static final DeferredItem<BlockItem> ME_DIGITAL_MERGED_STORAGE_CORE_256M = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_MERGED_STORAGE_CORE_256M);
    public static final DeferredItem<BlockItem> ME_DIGITAL_PATTERN_PROCESSING_CORE = ITEMS.registerSimpleBlockItem(DEBlocks.ME_DIGITAL_PATTERN_PROCESSING_CORE);
    public static final DeferredItem<BlockItem> EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE = ITEMS.registerSimpleBlockItem(DEBlocks.EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE);
    public static final DeferredItem<BlockItem> OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE = ITEMS.registerSimpleBlockItem(DEBlocks.OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE);
    public static final DeferredItem<BlockItem> COMPOSITE_INPUT_WAREHOUSE = ITEMS.registerSimpleBlockItem(DEBlocks.COMPOSITE_INPUT_WAREHOUSE);
    public static final DeferredItem<BlockItem> COMPOSITE_OUTPUT_WAREHOUSE = ITEMS.registerSimpleBlockItem(DEBlocks.COMPOSITE_OUTPUT_WAREHOUSE);
    public static final DeferredItem<BlockItem> ME_COMPOSITE_INPUT_WAREHOUSE = ITEMS.registerSimpleBlockItem(DEBlocks.ME_COMPOSITE_INPUT_WAREHOUSE);
    public static final DeferredItem<BlockItem> ME_COMPOSITE_OUTPUT_WAREHOUSE = ITEMS.registerSimpleBlockItem(DEBlocks.ME_COMPOSITE_OUTPUT_WAREHOUSE);
    public static final DeferredItem<BlockItem> ME_PATTERN_BUFFER = ITEMS.registerSimpleBlockItem(DEBlocks.ME_PATTERN_BUFFER);
    public static final DeferredItem<BlockItem> TRINITY_INFORMATION_EXCHANGE_DEPOT = ITEMS.registerSimpleBlockItem(DEBlocks.TRINITY_INFORMATION_EXCHANGE_DEPOT);
    public static final DeferredItem<BlockItem> BUDDING_DATA_CRYSTAL_0 = ITEMS.registerSimpleBlockItem(DEBlocks.BUDDING_DATA_CRYSTAL_0);
    public static final DeferredItem<BlockItem> BUDDING_DATA_CRYSTAL_1 = ITEMS.registerSimpleBlockItem(DEBlocks.BUDDING_DATA_CRYSTAL_1);
    public static final DeferredItem<BlockItem> BUDDING_DATA_CRYSTAL_2 = ITEMS.registerSimpleBlockItem(DEBlocks.BUDDING_DATA_CRYSTAL_2);
    public static final DeferredItem<BlockItem> BUDDING_DATA_CRYSTAL_3 = ITEMS.registerSimpleBlockItem(DEBlocks.BUDDING_DATA_CRYSTAL_3);
    public static final DeferredItem<BlockItem> BUDDING_DATA_CRYSTAL_4 = ITEMS.registerSimpleBlockItem(DEBlocks.BUDDING_DATA_CRYSTAL_4);
    public static final DeferredItem<BlockItem> SMALL_DATA_CRYSTAL_BUD = ITEMS.registerSimpleBlockItem(DEBlocks.SMALL_DATA_CRYSTAL_BUD);
    public static final DeferredItem<BlockItem> MEDIUM_DATA_CRYSTAL_BUD = ITEMS.registerSimpleBlockItem(DEBlocks.MEDIUM_DATA_CRYSTAL_BUD);
    public static final DeferredItem<BlockItem> LARGE_DATA_CRYSTAL_BUD = ITEMS.registerSimpleBlockItem(DEBlocks.LARGE_DATA_CRYSTAL_BUD);
    public static final DeferredItem<BlockItem> DATA_CRYSTAL_CLUSTER = ITEMS.registerSimpleBlockItem(DEBlocks.DATA_CRYSTAL_CLUSTER);
    public static final DeferredItem<PartItem<AdaptivePatternProviderPart>> ADAPTIVE_PATTERN_PROVIDER_PART = ITEMS.register(
            "adaptive_pattern_provider_part",
            () -> new PartItem<>(new Item.Properties(), AdaptivePatternProviderPart.class, AdaptivePatternProviderPart::new));
    public static final DeferredItem<PartItem<MeSolarPanelPart>> ME_SOLAR_PANEL_PART = ITEMS.register(
            "me_solar_panel_part",
            () -> new PartItem<>(new Item.Properties(), MeSolarPanelPart.class, MeSolarPanelPart::new));
    public static final DeferredItem<PartItem<UniversalTerminalPart>> UNIVERSAL_TERMINAL = ITEMS.register(
            "universal_terminal",
            () -> new UniversalTerminalPartItem(new Item.Properties()));
    public static final DeferredItem<Item> DATA_CRYSTAL = ITEMS.registerSimpleItem("data_crystal");
    public static final DeferredItem<PoweredSwordItem> DATA_CRYSTAL_SWORD = ITEMS.register(
            "data_crystal_sword",
            () -> new PoweredSwordItem(DATA_CRYSTAL_SWORD_TIER,
                    handheldProperties(0, PoweredSwordItem.createAttributes(DATA_CRYSTAL_SWORD_TIER, 6.0F, -2.0F)),
                    NO_MINING_SWORD_TOOL,
                    false));
    public static final DeferredItem<PoweredAxeItem> DATA_CRYSTAL_AXE = ITEMS.register(
            "data_crystal_axe",
            () -> new PoweredAxeItem(DATA_CRYSTAL_TOOL_TIER,
                    handheldProperties(0, PoweredAxeItem.createAttributes(DATA_CRYSTAL_TOOL_TIER, 8.0F, -2.8F))));
    public static final DeferredItem<PoweredPickaxeItem> DATA_CRYSTAL_PICKAXE = ITEMS.register(
            "data_crystal_pickaxe",
            () -> new PoweredPickaxeItem(DATA_CRYSTAL_TOOL_TIER,
                    handheldProperties(0, PoweredPickaxeItem.createAttributes(DATA_CRYSTAL_TOOL_TIER, 0.0F, -2.6F))));
    public static final DeferredItem<PoweredHoeItem> DATA_CRYSTAL_HOE = ITEMS.register(
            "data_crystal_hoe",
            () -> new PoweredHoeItem(DATA_CRYSTAL_TOOL_TIER,
                    handheldProperties(0, PoweredHoeItem.createAttributes(DATA_CRYSTAL_TOOL_TIER, 0.0F, -2.6F))));
    public static final DeferredItem<PoweredShovelItem> DATA_CRYSTAL_SHOVEL = ITEMS.register(
            "data_crystal_shovel",
            () -> new PoweredShovelItem(DATA_CRYSTAL_TOOL_TIER,
                    handheldProperties(0, PoweredShovelItem.createAttributes(DATA_CRYSTAL_TOOL_TIER, 0.0F, -2.6F))));
    public static final DeferredItem<PoweredCuttingKnifeItem> DATA_CRYSTAL_CUTTING_KNIFE = ITEMS.register(
            "data_crystal_cutting_knife",
            () -> new PoweredCuttingKnifeItem(new Item.Properties().stacksTo(1).setNoRepair()));
    public static final DeferredItem<PoweredSwordItem> DATA_LIGHT_SABER = ITEMS.register(
            "data_light_saber",
            () -> new PoweredSwordItem(DATA_CRYSTAL_SWORD_TIER,
                    handheldProperties(0, PoweredSwordItem.createAttributes(DATA_CRYSTAL_SWORD_TIER, 13.0F, -2.0F))));
    public static final DeferredItem<PoweredSwordItem> DATA_SANCTIFIER = ITEMS.register(
            "data_sanctifier",
            () -> new PoweredSwordItem(DATA_CRYSTAL_SWORD_TIER,
                    handheldProperties(0, PoweredSwordItem.createAttributes(DATA_CRYSTAL_SWORD_TIER, 32.0F, -2.0F))
                            .fireResistant()));
    public static final DeferredItem<Item> CARD_SABER_ENERGY = ITEMS.register(
            "card_saber_energy",
            () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    public static final DeferredItem<Item> REDSTONE_TUNING_CARD = ITEMS.register(
            "redstone_tuning_card",
            () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    public static final DeferredItem<Item> SOLIDIFIED_OBSIDIAN = ITEMS.registerSimpleItem("solidified_obsidian");
    public static final DeferredItem<Item> DATA_DUST = ITEMS.registerSimpleItem("data_dust");
    public static final DeferredItem<Item> OBSIDIAN_DUST = ITEMS.registerSimpleItem("obsidian_dust");
    public static final DeferredItem<Item> DATA_CARRIER = ITEMS.register("data_carrier",
            () -> new BiologyDataCarrierItem(new Item.Properties(), false));
    public static final DeferredItem<Item> MOB_DATA_CARRIER = ITEMS.register("mob_data_carrier",
            () -> new BiologyDataCarrierItem(new Item.Properties(), true));
    public static final DeferredItem<Item> CROP_DATA_CARRIER = ITEMS.register("crop_data_carrier",
            () -> new BiologyDataCarrierItem(new Item.Properties(), true));
    public static final DeferredItem<Item> ORE_DATA_CARRIER = ITEMS.register("ore_data_carrier",
            () -> new BiologyDataCarrierItem(new Item.Properties(), true));
    public static final DeferredItem<Item> TIME_CORE = ITEMS.registerSimpleItem("time_core");
    public static final DeferredItem<MeVacuumItem> ME_VACUUM = ITEMS.register(
            "me_vacuum",
            () -> new MeVacuumItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<OrderPackageItem> ORDER_PACKAGE = ITEMS.register(
            "order_package",
            () -> new OrderPackageItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DATA_FLOW_COMPONENT_HOUSING = ITEMS.registerSimpleItem("data_flow_component_housing");
    public static final DeferredItem<DataDistributionConnectorItem> DATA_DISTRIBUTION_CONNECTOR = ITEMS.register(
            "data_distribution_connector",
            () -> new DataDistributionConnectorItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DATA_INSCRIBER_TEMPLATE = ITEMS.registerSimpleItem("data_inscriber_template");
    public static final DeferredItem<Item> DATA_CIRCUIT_BOARD = ITEMS.registerSimpleItem("data_circuit_board");
    public static final DeferredItem<Item> DATA_PROCESSOR = ITEMS.registerSimpleItem("data_processor");
    public static final DeferredItem<Item> DIGISIDIAN_MEMORIZE_INGOT = ITEMS.registerSimpleItem("digisidian_memorize_ingot");
    public static final DeferredItem<DataStorageComponentItem> DATA_STORAGE_COMPONENT_1K = ITEMS.register(
            "data_storage_component_1k",
            () -> new DataStorageComponentItem(new Item.Properties(), 1));
    public static final DeferredItem<DataStorageComponentItem> DATA_STORAGE_COMPONENT_4K = ITEMS.register(
            "data_storage_component_4k",
            () -> new DataStorageComponentItem(new Item.Properties(), 4));
    public static final DeferredItem<DataStorageComponentItem> DATA_STORAGE_COMPONENT_16K = ITEMS.register(
            "data_storage_component_16k",
            () -> new DataStorageComponentItem(new Item.Properties(), 16));
    public static final DeferredItem<DataStorageComponentItem> DATA_STORAGE_COMPONENT_64K = ITEMS.register(
            "data_storage_component_64k",
            () -> new DataStorageComponentItem(new Item.Properties(), 64));
    public static final DeferredItem<DataStorageComponentItem> DATA_STORAGE_COMPONENT_256K = ITEMS.register(
            "data_storage_component_256k",
            () -> new DataStorageComponentItem(new Item.Properties(), 256));
    public static final DeferredItem<DataStorageComponentItem> DATA_STORAGE_COMPONENT_1M = ITEMS.register(
            "data_storage_component_1m",
            () -> new DataStorageComponentItem(new Item.Properties(), 1024));
    public static final DeferredItem<DataStorageComponentItem> DATA_STORAGE_COMPONENT_4M = ITEMS.register(
            "data_storage_component_4m",
            () -> new DataStorageComponentItem(new Item.Properties(), 4096));
    public static final DeferredItem<DataStorageComponentItem> DATA_STORAGE_COMPONENT_16M = ITEMS.register(
            "data_storage_component_16m",
            () -> new DataStorageComponentItem(new Item.Properties(), 16384));
    public static final DeferredItem<DataStorageComponentItem> DATA_STORAGE_COMPONENT_64M = ITEMS.register(
            "data_storage_component_64m",
            () -> new DataStorageComponentItem(new Item.Properties(), 65536));
    public static final DeferredItem<DataStorageComponentItem> DATA_STORAGE_COMPONENT_256M = ITEMS.register(
            "data_storage_component_256m",
            () -> new DataStorageComponentItem(new Item.Properties(), 262144));

    private static final StorageTier DIGITAL_STORAGE_SIZE_1M = portableDigitalStorageTier(6, "1m", 1_048_576, 3.0);
    private static final StorageTier DIGITAL_STORAGE_SIZE_4M = portableDigitalStorageTier(7, "4m", 4_194_304, 3.5);
    private static final StorageTier DIGITAL_STORAGE_SIZE_16M = portableDigitalStorageTier(8, "16m", 16_777_216, 4.0);
    private static final StorageTier DIGITAL_STORAGE_SIZE_64M = portableDigitalStorageTier(9, "64m", 67_108_864, 4.5);
    private static final StorageTier DIGITAL_STORAGE_SIZE_256M = portableDigitalStorageTier(10, "256m", 268_435_456, 5.0);

    public static final DeferredItem<PortableDigitalStorageCellItem> PORTABLE_DIGITAL_STORAGE_CELL_1M = registerPortableDigitalStorageCell("portable_digital_storage_cell_1m", DIGITAL_STORAGE_SIZE_1M, 0x68D9FF);
    public static final DeferredItem<PortableDigitalStorageCellItem> PORTABLE_DIGITAL_STORAGE_CELL_4M = registerPortableDigitalStorageCell("portable_digital_storage_cell_4m", DIGITAL_STORAGE_SIZE_4M, 0x70F0C0);
    public static final DeferredItem<PortableDigitalStorageCellItem> PORTABLE_DIGITAL_STORAGE_CELL_16M = registerPortableDigitalStorageCell("portable_digital_storage_cell_16m", DIGITAL_STORAGE_SIZE_16M, 0xB0EE78);
    public static final DeferredItem<PortableDigitalStorageCellItem> PORTABLE_DIGITAL_STORAGE_CELL_64M = registerPortableDigitalStorageCell("portable_digital_storage_cell_64m", DIGITAL_STORAGE_SIZE_64M, 0xFFAB6C);
    public static final DeferredItem<PortableDigitalStorageCellItem> PORTABLE_DIGITAL_STORAGE_CELL_256M = registerPortableDigitalStorageCell("portable_digital_storage_cell_256m", DIGITAL_STORAGE_SIZE_256M, 0xFF82D8);
    public static final DeferredItem<RadixContainmentSphereItem> RADIX_CONTAINMENT_SPHERE = ITEMS.register(
            "radix_containment_sphere",
            () -> new RadixContainmentSphereItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<MatterConvergingCrossbowItem> MATTER_CONVERGING_CROSSBOW = ITEMS.register(
            "matter_converging_crossbow",
            () -> new MatterConvergingCrossbowItem(new Item.Properties()));
    public static final DeferredItem<DataRipperPartItem> DATA_RIPPER = ITEMS.register("data_ripper",
            () -> new DataRipperPartItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> FISH_DAN = ITEMS.register(
            "fish_dan_",
            () -> new DollBlockItem(DEBlocks.FISH_DAN.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> QIUYEQAQ2024 = ITEMS.registerSimpleBlockItem(DEBlocks.QIUYEQAQ2024);
    public static final DeferredItem<BlockItem> TED_XENON = ITEMS.registerSimpleBlockItem(DEBlocks.TED_XENON);

    private DEItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static ItemStack wrappedDataFlow() {
        return GenericStack.wrapInItemStack(DataFlowKey.of(), 1);
    }

    private static DeferredItem<DigitalStorageCellItem> registerDigitalStorageCell(String id, double idleDrain, int bytes) {
        return ITEMS.register(id, () -> new DigitalStorageCellItem(new Item.Properties(), idleDrain, bytes));
    }

    private static DeferredItem<PortableDigitalStorageCellItem> registerPortableDigitalStorageCell(String id, StorageTier tier, int color) {
        return ITEMS.register(id, () -> new PortableDigitalStorageCellItem(tier, new Item.Properties(), color));
    }

    private static StorageTier portableDigitalStorageTier(int index, String namePrefix, int bytes, double idleDrain) {
        return new StorageTier(index, namePrefix, bytes, idleDrain,
                () -> BuiltInRegistries.ITEM.get(Data_Energistics.id("data_storage_component_" + namePrefix)));
    }

    private static Item.Properties handheldProperties(int durability, ItemAttributeModifiers attributes) {
        Item.Properties properties = new Item.Properties().stacksTo(1).attributes(attributes).setNoRepair();
        if (durability > 0) {
            properties = properties.durability(durability);
        }
        return properties;
    }
}
