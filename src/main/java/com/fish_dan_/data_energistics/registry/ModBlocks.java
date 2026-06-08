package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.AdaptivePatternProviderBlock;
import com.fish_dan_.data_energistics.block.DataChargerBlock;
import com.fish_dan_.data_energistics.block.DataCrystalBuddingBlock;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.block.DataExtractorBlock;
import com.fish_dan_.data_energistics.block.DataFrameworkBlock;
import com.fish_dan_.data_energistics.block.DataMimeticFieldBlock;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.block.DataSanctumInterfaceBlock;
import com.fish_dan_.data_energistics.block.DataSolarPanelBlock;
import com.fish_dan_.data_energistics.block.DataTeleportAnchorBlock;
import com.fish_dan_.data_energistics.block.DigitalStorageDepotBlock;
import com.fish_dan_.data_energistics.block.EnderCohesionMeteoriteBlock;
import com.fish_dan_.data_energistics.block.ResidualDataOreBlock;
import com.fish_dan_.data_energistics.block.TntConfigurableBlock;
import com.fish_dan_.data_energistics.block.decor.DollBlock;

import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Data_Energistics.MODID);

    public static final DeferredBlock<Block> DATA_SOLAR_PANEL = BLOCKS.registerBlock(
            "me_solar_panel",
            DataSolarPanelBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion());

    public static final DeferredBlock<Block> DATA_EXTRACTOR = BLOCKS.registerBlock(
            "data_extractor",
            DataExtractorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));

    public static final DeferredBlock<Block> DATA_RIPPER_REASSEMBLER = BLOCKS.registerBlock(
            "data_reassembler",
            DataRipperReassemblerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));

    public static final DeferredBlock<Block> DATA_FRAMEWORK = BLOCKS.registerBlock(
            "data_framework",
            DataFrameworkBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.QUARTZ_BLOCK)
                    .noOcclusion()
                    .isViewBlocking((state, blockGetter, pos) -> false));

    public static final DeferredBlock<Block> DATA_DISTRIBUTION_TOWER = BLOCKS.registerBlock(
            "data_distribution_tower",
            DataDistributionTowerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(DataDistributionTowerBlock.PART) == 2 && state.getValue(DataDistributionTowerBlock.ACTIVE) ? 15 : 0));

    public static final DeferredBlock<Block> DATA_MIMETIC_FIELD = BLOCKS.registerBlock(
            "data_mimetic_field",
            DataMimeticFieldBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));

    public static final DeferredBlock<Block> DATA_TELEPORT_ANCHOR = BLOCKS.registerBlock(
            "data_teleport_anchor",
            DataTeleportAnchorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));

    public static final DeferredBlock<Block> DATA_SANCTUM = BLOCKS.registerBlock(
            "data_sanctum",
            DataSanctumBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion());

    public static final DeferredBlock<Block> DATA_SANCTUM_INTERFACE = BLOCKS.registerBlock(
            "data_sanctum_interface",
            DataSanctumInterfaceBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));

    public static final DeferredBlock<Block> DATA_CHARGER = BLOCKS.registerBlock(
            "data_charger",
            DataChargerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion());

    public static final DeferredBlock<Block> EXTENDED_DATA_CHARGER = BLOCKS.registerBlock(
            "extended_data_charger",
            DataChargerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion());

    public static final DeferredBlock<Block> GUIDE_ENDER_DISPLAY = BLOCKS.registerBlock(
            "guide_ender_display",
            Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .noOcclusion());

    public static final DeferredBlock<Block> GUIDE_DATA_CORROSION_LIQUID_DISPLAY = BLOCKS.registerBlock(
            "guide_data_corrosion_liquid_display",
            Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .noOcclusion()
                    .lightLevel(state -> 4));

    public static final DeferredBlock<Block> ADAPTIVE_PATTERN_PROVIDER = BLOCKS.registerBlock(
            "adaptive_pattern_provider",
            properties -> new AdaptivePatternProviderBlock(properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));

    public static final DeferredBlock<Block> TNT_CONFIGURABLE = BLOCKS.registerBlock(
            "tnt_configurable",
            TntConfigurableBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.TNT));

    public static final DeferredBlock<Block> RESIDUAL_DATA_ORE = BLOCKS.registerBlock(
            "residual_data_ore",
            ResidualDataOreBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.ANCIENT_DEBRIS)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> ENDER_COHESION_METEORITE_0 = BLOCKS.registerBlock(
            "data_meteorite_0",
            properties -> new EnderCohesionMeteoriteBlock(properties, 0.05F, 0.10F, 0.00F, 0.00F),
            BlockBehaviour.Properties.ofFullCopy(Blocks.ANCIENT_DEBRIS)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> ENDER_COHESION_METEORITE_1 = BLOCKS.registerBlock(
            "data_meteorite_1",
            properties -> new EnderCohesionMeteoriteBlock(properties, 0.10F, 0.20F, 0.10F, 0.00F),
            BlockBehaviour.Properties.ofFullCopy(Blocks.ANCIENT_DEBRIS)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> ENDER_COHESION_METEORITE_2 = BLOCKS.registerBlock(
            "data_meteorite_2",
            properties -> new EnderCohesionMeteoriteBlock(properties, 0.15F, 0.25F, 0.15F, 0.15F),
            BlockBehaviour.Properties.ofFullCopy(Blocks.ANCIENT_DEBRIS)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> DATA_CRYSTAL_BLOCK = BLOCKS.registerBlock(
            "data_crystal_block",
            Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.REDSTONE_BLOCK)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> DIGITAL_STORAGE_DEPOT = BLOCKS.registerBlock(
            "digital_storage_depot",
            DigitalStorageDepotBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .isViewBlocking((state, blockGetter, pos) -> false)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> BUDDING_DATA_CRYSTAL_0 = BLOCKS.registerBlock(
            "budding_data_crystal_0",
            Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> BUDDING_DATA_CRYSTAL_1 = BLOCKS.registerBlock(
            "budding_data_crystal_1",
            DataCrystalBuddingBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> BUDDING_DATA_CRYSTAL_2 = BLOCKS.registerBlock(
            "budding_data_crystal_2",
            DataCrystalBuddingBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> BUDDING_DATA_CRYSTAL_3 = BLOCKS.registerBlock(
            "budding_data_crystal_3",
            DataCrystalBuddingBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> BUDDING_DATA_CRYSTAL_4 = BLOCKS.registerBlock(
            "budding_data_crystal_4",
            DataCrystalBuddingBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> SMALL_DATA_CRYSTAL_BUD = BLOCKS.registerBlock(
            "small_data_crystal_bud",
            properties -> new AmethystClusterBlock(3.0F, 4.0F, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMALL_AMETHYST_BUD)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> MEDIUM_DATA_CRYSTAL_BUD = BLOCKS.registerBlock(
            "medium_data_crystal_bud",
            properties -> new AmethystClusterBlock(4.0F, 3.0F, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.MEDIUM_AMETHYST_BUD)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> LARGE_DATA_CRYSTAL_BUD = BLOCKS.registerBlock(
            "large_data_crystal_bud",
            properties -> new AmethystClusterBlock(5.0F, 3.0F, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.LARGE_AMETHYST_BUD)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> DATA_CRYSTAL_CLUSTER = BLOCKS.registerBlock(
            "data_crystal_cluster",
            properties -> new AmethystClusterBlock(7.0F, 3.0F, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> FISH_DAN = BLOCKS.registerBlock(
            "fish_dan_",
            DollBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion());
    public static final DeferredBlock<Block> QIUYEQAQ2024 = BLOCKS.registerBlock(
            "qiuyeqaq2024",
            DollBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion());

    private ModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
