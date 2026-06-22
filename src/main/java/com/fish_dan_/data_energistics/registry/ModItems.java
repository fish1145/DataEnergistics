package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.config.FlatteningTntConfig;
import com.fish_dan_.data_energistics.item.AdaptivePatternProviderUpgradeItem;
import com.fish_dan_.data_energistics.item.BiologyDataCarrierItem;
import com.fish_dan_.data_energistics.item.ConfigurableTntBlockItem;
import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.item.DataDistributionConnectorItem;
import com.fish_dan_.data_energistics.item.DataFlowPortableCellItem;
import com.fish_dan_.data_energistics.item.DataFlowStorageCellItem;
import com.fish_dan_.data_energistics.item.DataMeteoriteCompassItem;
import com.fish_dan_.data_energistics.item.DataRipperPartItem;
import com.fish_dan_.data_energistics.item.DataSanctumInterfaceUpgradeItem;
import com.fish_dan_.data_energistics.item.DataStorageComponentItem;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.item.InfiniteDataCellItem;
import com.fish_dan_.data_energistics.item.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.PoweredAxeItem;
import com.fish_dan_.data_energistics.item.PoweredCuttingKnifeItem;
import com.fish_dan_.data_energistics.item.PoweredHoeItem;
import com.fish_dan_.data_energistics.item.PoweredPickaxeItem;
import com.fish_dan_.data_energistics.item.PoweredShovelItem;
import com.fish_dan_.data_energistics.item.PoweredSwordItem;
import com.fish_dan_.data_energistics.item.UniversalTerminalPartItem;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.part.DataSanctumInterfacePart;
import com.fish_dan_.data_energistics.part.MeSolarPanelPart;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

public final class ModItems {

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

    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_1K = registerDataFlowCell("data_flow_cell_1k", 0.5, 1);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_4K = registerDataFlowCell("data_flow_cell_4k", 1.0, 4);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_16K = registerDataFlowCell("data_flow_cell_16k", 1.5, 16);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_64K = registerDataFlowCell("data_flow_cell_64k", 2.5, 65);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_256K = registerDataFlowCell("data_flow_cell_256k", 3.0, 262);
    public static final DeferredItem<InfiniteDataCellItem> DATA_CELL_INFINITY = ITEMS.register(
            "data_cell_infinity",
            () -> new InfiniteDataCellItem(new Item.Properties()));

    public static final DeferredItem<DataFlowPortableCellItem> PORTABLE_DATA_FLOW_CELL_1K = registerPortableDataFlowCell("portable_data_flow_cell_1k", StorageTier.SIZE_1K, 0x4FD8FF);
    public static final DeferredItem<DataFlowPortableCellItem> PORTABLE_DATA_FLOW_CELL_4K = registerPortableDataFlowCell("portable_data_flow_cell_4k", StorageTier.SIZE_4K, 0x56F0B5);
    public static final DeferredItem<DataFlowPortableCellItem> PORTABLE_DATA_FLOW_CELL_16K = registerPortableDataFlowCell("portable_data_flow_cell_16k", StorageTier.SIZE_16K, 0xA0EE68);
    public static final DeferredItem<DataFlowPortableCellItem> PORTABLE_DATA_FLOW_CELL_64K = registerPortableDataFlowCell("portable_data_flow_cell_64k", StorageTier.SIZE_64K, 0xFF9B5C);
    public static final DeferredItem<DataFlowPortableCellItem> PORTABLE_DATA_FLOW_CELL_256K = registerPortableDataFlowCell("portable_data_flow_cell_256k", StorageTier.SIZE_256K, 0xFF72C8);

    public static final DeferredItem<BlockItem> DATA_SOLAR_PANEL = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_SOLAR_PANEL);
    public static final DeferredItem<BlockItem> DATA_EXTRACTOR = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_EXTRACTOR);
    public static final DeferredItem<BlockItem> DATA_RIPPER_REASSEMBLER = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_RIPPER_REASSEMBLER);
    public static final DeferredItem<BlockItem> DATA_FRAMEWORK = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_FRAMEWORK);
    public static final DeferredItem<BlockItem> DATA_DISTRIBUTION_TOWER = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_DISTRIBUTION_TOWER);
    public static final DeferredItem<BlockItem> DATA_MIMETIC_FIELD = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_MIMETIC_FIELD);
    public static final DeferredItem<BlockItem> DATA_TELEPORT_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_TELEPORT_ANCHOR);
    public static final DeferredItem<BlockItem> DATA_SANCTUM = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_SANCTUM);
    public static final DeferredItem<BlockItem> DATA_SANCTUM_INTERFACE = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_SANCTUM_INTERFACE);
    public static final DeferredItem<BlockItem> DATA_CHARGER = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_CHARGER);
    public static final DeferredItem<BlockItem> EXTENDED_DATA_CHARGER = ITEMS.registerSimpleBlockItem(ModBlocks.EXTENDED_DATA_CHARGER);
    public static final DeferredItem<DataSanctumInterfaceUpgradeItem> DATA_SANCTUM_INTERFACE_UPGRADE = ITEMS.register(
            "data_sanctum_interface_upgrade",
            () -> new DataSanctumInterfaceUpgradeItem(new Item.Properties()));
    public static final DeferredItem<PartItem<DataSanctumInterfacePart>> DATA_SANCTUM_INTERFACE_PART = ITEMS.register(
            "data_sanctum_interface_part",
            () -> new PartItem<>(new Item.Properties(), DataSanctumInterfacePart.class, DataSanctumInterfacePart::new));
    public static final DeferredItem<BlockItem> ADAPTIVE_PATTERN_PROVIDER = ITEMS.registerSimpleBlockItem(ModBlocks.ADAPTIVE_PATTERN_PROVIDER);
    public static final DeferredItem<AdaptivePatternProviderUpgradeItem> ADAPTIVE_PATTERN_PROVIDER_UPGRADE = ITEMS.register(
            "adaptive_pattern_provider_upgrade",
            () -> new AdaptivePatternProviderUpgradeItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> TNT_CONFIGURABLE = ITEMS.register(
            "tnt_configurable",
            () -> new ConfigurableTntBlockItem(ModBlocks.TNT_CONFIGURABLE.get(), new Item.Properties(),
                    () -> FlatteningTntConfig.configurableTntDisplayName));
    public static final DeferredItem<BlockItem> DATA_NUKE = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_NUKE);
    public static final DeferredItem<BlockItem> RESIDUAL_DATA_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.RESIDUAL_DATA_ORE);
    public static final DeferredItem<BlockItem> ENDER_COHESION_METEORITE_0 = ITEMS.registerSimpleBlockItem(ModBlocks.ENDER_COHESION_METEORITE_0);
    public static final DeferredItem<BlockItem> ENDER_COHESION_METEORITE_1 = ITEMS.registerSimpleBlockItem(ModBlocks.ENDER_COHESION_METEORITE_1);
    public static final DeferredItem<BlockItem> ENDER_COHESION_METEORITE_2 = ITEMS.registerSimpleBlockItem(ModBlocks.ENDER_COHESION_METEORITE_2);
    public static final DeferredItem<DataMeteoriteCompassItem> DATA_METEORITE_COMPASS = ITEMS.register(
            "data_meteorite_compass",
            () -> new DataMeteoriteCompassItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> DATA_CRYSTAL_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_CRYSTAL_BLOCK);
    public static final DeferredItem<BlockItem> DIGITAL_STORAGE_DEPOT = ITEMS.register(
            "digital_storage_depot",
            () -> new DigitalStorageDepotBlockItem(ModBlocks.DIGITAL_STORAGE_DEPOT.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> BUDDING_DATA_CRYSTAL_0 = ITEMS.registerSimpleBlockItem(ModBlocks.BUDDING_DATA_CRYSTAL_0);
    public static final DeferredItem<BlockItem> BUDDING_DATA_CRYSTAL_1 = ITEMS.registerSimpleBlockItem(ModBlocks.BUDDING_DATA_CRYSTAL_1);
    public static final DeferredItem<BlockItem> BUDDING_DATA_CRYSTAL_2 = ITEMS.registerSimpleBlockItem(ModBlocks.BUDDING_DATA_CRYSTAL_2);
    public static final DeferredItem<BlockItem> BUDDING_DATA_CRYSTAL_3 = ITEMS.registerSimpleBlockItem(ModBlocks.BUDDING_DATA_CRYSTAL_3);
    public static final DeferredItem<BlockItem> BUDDING_DATA_CRYSTAL_4 = ITEMS.registerSimpleBlockItem(ModBlocks.BUDDING_DATA_CRYSTAL_4);
    public static final DeferredItem<BlockItem> SMALL_DATA_CRYSTAL_BUD = ITEMS.registerSimpleBlockItem(ModBlocks.SMALL_DATA_CRYSTAL_BUD);
    public static final DeferredItem<BlockItem> MEDIUM_DATA_CRYSTAL_BUD = ITEMS.registerSimpleBlockItem(ModBlocks.MEDIUM_DATA_CRYSTAL_BUD);
    public static final DeferredItem<BlockItem> LARGE_DATA_CRYSTAL_BUD = ITEMS.registerSimpleBlockItem(ModBlocks.LARGE_DATA_CRYSTAL_BUD);
    public static final DeferredItem<BlockItem> DATA_CRYSTAL_CLUSTER = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_CRYSTAL_CLUSTER);
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
    public static final DeferredItem<Item> DATA_CAPTURE_BALL = ITEMS.register(
            "data_capture_ball",
            () -> new DataCaptureBallItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<MatterConvergingCrossbowItem> MATTER_CONVERGING_CROSSBOW = ITEMS.register(
            "matter_converging_crossbow",
            () -> new MatterConvergingCrossbowItem(new Item.Properties()));
    public static final DeferredItem<DataRipperPartItem> DATA_RIPPER = ITEMS.register("data_ripper",
            () -> new DataRipperPartItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> FISH_DAN = ITEMS.registerSimpleBlockItem(ModBlocks.FISH_DAN);
    public static final DeferredItem<BlockItem> QIUYEQAQ2024 = ITEMS.registerSimpleBlockItem(ModBlocks.QIUYEQAQ2024);
    public static final DeferredItem<BlockItem> TED_XENON = ITEMS.registerSimpleBlockItem(ModBlocks.TED_XENON);

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static ItemStack wrappedDataFlow() {
        return GenericStack.wrapInItemStack(DataFlowKey.of(), 1);
    }

    private static DeferredItem<DataFlowStorageCellItem> registerDataFlowCell(String id, double idleDrain, int bytes) {
        return ITEMS.register(id, () -> new DataFlowStorageCellItem(new Item.Properties(), idleDrain, bytes));
    }

    private static DeferredItem<DataFlowPortableCellItem> registerPortableDataFlowCell(String id, StorageTier tier, int color) {
        return ITEMS.register(id, () -> new DataFlowPortableCellItem(tier, new Item.Properties(), color));
    }

    private static Item.Properties handheldProperties(int durability, ItemAttributeModifiers attributes) {
        Item.Properties properties = new Item.Properties().stacksTo(1).attributes(attributes).setNoRepair();
        if (durability > 0) {
            properties = properties.durability(durability);
        }
        return properties;
    }
}
