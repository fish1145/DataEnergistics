package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.AdaptivePatternProviderBlock;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.block.DataChargerBlock;
import com.fish_dan_.data_energistics.block.DataCrystalBuddingBlock;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.block.DataExtractorBlock;
import com.fish_dan_.data_energistics.block.DataFrameworkBlock;
import com.fish_dan_.data_energistics.block.DataMimeticFieldBlock;
import com.fish_dan_.data_energistics.block.DataNukeBlock;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerMainBlock;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.block.DataSanctumInterfaceBlock;
import com.fish_dan_.data_energistics.block.DataSanctumReturnPortalBlock;
import com.fish_dan_.data_energistics.block.DataSolarPanelBlock;
import com.fish_dan_.data_energistics.block.DataTeleportAnchorBlock;
import com.fish_dan_.data_energistics.block.DigitalConstructFlowerBlock;
import com.fish_dan_.data_energistics.block.DigitalStorageDepotBlock;
import com.fish_dan_.data_energistics.block.EnderCohesionMeteoriteBlock;
import com.fish_dan_.data_energistics.block.ResidualDataOreBlock;
import com.fish_dan_.data_energistics.block.TntConfigurableBlock;
import com.fish_dan_.data_energistics.block.TrinityCoreBlock;
import com.fish_dan_.data_energistics.block.TrinityPatternCoreBlock;
import com.fish_dan_.data_energistics.block.decor.DollBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreTier;

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
            DataRipperReassemblerMainBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));

    public static final DeferredBlock<Block> TRINITY_DATA_CORE = BLOCKS.registerBlock(
            "trinity_data_core",
            DigitalConstructFlowerBlock::new,
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

    public static final DeferredBlock<Block> DATA_SANCTUM_RETURN_PORTAL = BLOCKS.registerBlock(
            "data_sanctum_return_portal",
            DataSanctumReturnPortalBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollission()
                    .noLootTable()
                    .noOcclusion()
                    .isViewBlocking((state, blockGetter, pos) -> false)
                    .lightLevel(state -> 10));

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
    public static final DeferredBlock<Block> DATA_NUKE = BLOCKS.registerBlock(
            "digital_annihilator",
            DataNukeBlock::new,
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

    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_STORAGE_CORE_1M = registerStorageCore("me_digital_storage_core_1m", TrinityCoreTier.SIZE_1M);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_STORAGE_CORE_4M = registerStorageCore("me_digital_storage_core_4m", TrinityCoreTier.SIZE_4M);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_STORAGE_CORE_16M = registerStorageCore("me_digital_storage_core_16m", TrinityCoreTier.SIZE_16M);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_STORAGE_CORE_64M = registerStorageCore("me_digital_storage_core_64m", TrinityCoreTier.SIZE_64M);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_STORAGE_CORE_256M = registerStorageCore("me_digital_storage_core_256m", TrinityCoreTier.SIZE_256M);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_STORAGE_CORE_1G = registerStorageCore("me_digital_storage_core_1g", TrinityCoreTier.SIZE_1G);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_STORAGE_CORE_4G = registerStorageCore("me_digital_storage_core_4g", TrinityCoreTier.SIZE_4G);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_STORAGE_CORE_16G = registerStorageCore("me_digital_storage_core_16g", TrinityCoreTier.SIZE_16G);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_STORAGE_CORE_64G = registerStorageCore("me_digital_storage_core_64g", TrinityCoreTier.SIZE_64G);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_STORAGE_CORE_256G = registerStorageCore("me_digital_storage_core_256g", TrinityCoreTier.SIZE_256G);

    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_MERGED_STORAGE_CORE_1M = registerParallelCore("me_digital_merged_storage_core_1m", TrinityCoreTier.SIZE_1M);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_MERGED_STORAGE_CORE_4M = registerParallelCore("me_digital_merged_storage_core_4m", TrinityCoreTier.SIZE_4M);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_MERGED_STORAGE_CORE_16M = registerParallelCore("me_digital_merged_storage_core_16m", TrinityCoreTier.SIZE_16M);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_MERGED_STORAGE_CORE_64M = registerParallelCore("me_digital_merged_storage_core_64m", TrinityCoreTier.SIZE_64M);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_MERGED_STORAGE_CORE_256M = registerParallelCore("me_digital_merged_storage_core_256m", TrinityCoreTier.SIZE_256M);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_MERGED_STORAGE_CORE_1G = registerParallelCore("me_digital_merged_storage_core_1g", TrinityCoreTier.SIZE_1G);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_MERGED_STORAGE_CORE_4G = registerParallelCore("me_digital_merged_storage_core_4g", TrinityCoreTier.SIZE_4G);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_MERGED_STORAGE_CORE_16G = registerParallelCore("me_digital_merged_storage_core_16g", TrinityCoreTier.SIZE_16G);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_MERGED_STORAGE_CORE_64G = registerParallelCore("me_digital_merged_storage_core_64g", TrinityCoreTier.SIZE_64G);
    public static final DeferredBlock<TrinityCoreBlock> ME_DIGITAL_MERGED_STORAGE_CORE_256G = registerParallelCore("me_digital_merged_storage_core_256g", TrinityCoreTier.SIZE_256G);

    public static final DeferredBlock<TrinityPatternCoreBlock> ME_DIGITAL_PATTERN_PROCESSING_CORE = registerPatternProcessingCore("me_digital_pattern_processing_core", 64);
    public static final DeferredBlock<TrinityPatternCoreBlock> EXTENDED_ME_DIGITAL_PATTERN_PROCESSING_CORE = registerPatternProcessingCore("extended_me_digital_pattern_processing_core", 128);
    public static final DeferredBlock<TrinityPatternCoreBlock> OVERLIMIT_ME_DIGITAL_PATTERN_PROCESSING_CORE = registerPatternProcessingCore("overlimit_me_digital_pattern_processing_core", 512);

    public static final DeferredBlock<CompartmentBlock> COMPOSITE_INPUT_WAREHOUSE = registerCompartment(
            "composite_input_warehouse",
            CompartmentType.INPUT);
    public static final DeferredBlock<CompartmentBlock> COMPOSITE_OUTPUT_WAREHOUSE = registerCompartment(
            "composite_output_warehouse",
            CompartmentType.OUTPUT);
    public static final DeferredBlock<CompartmentBlock> ME_COMPOSITE_INPUT_WAREHOUSE = registerCompartment(
            "me_composite_input_warehouse",
            CompartmentType.ME_INPUT);
    public static final DeferredBlock<CompartmentBlock> ME_COMPOSITE_OUTPUT_WAREHOUSE = registerCompartment(
            "me_composite_output_warehouse",
            CompartmentType.ME_OUTPUT);
    public static final DeferredBlock<CompartmentBlock> ME_PATTERN_BUFFER = registerCompartment(
            "me_pattern_buffer",
            CompartmentType.PATTERN_BUFFER);
    public static final DeferredBlock<CompartmentBlock> TRINITY_ACCESS_HATCH = registerCompartment(
            "trinity_access_hatch",
            CompartmentType.TRINITY_ACCESS);

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
    public static final DeferredBlock<Block> TED_XENON = BLOCKS.registerBlock(
            "tedxenon",
            DollBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion());

    private ModBlocks() {}

    private static DeferredBlock<CompartmentBlock> registerCompartment(String id, CompartmentType type) {
        return BLOCKS.registerBlock(
                id,
                properties -> new CompartmentBlock(type, properties),
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                        .noOcclusion()
                        .isViewBlocking((state, blockGetter, pos) -> false)
                        .requiresCorrectToolForDrops());
    }

    private static DeferredBlock<TrinityCoreBlock> registerStorageCore(String id, TrinityCoreTier tier) {
        return BLOCKS.registerBlock(
                id,
                properties -> TrinityCoreBlock.storageCore(properties, tier),
                trinityCoreProperties());
    }

    private static DeferredBlock<TrinityCoreBlock> registerParallelCore(String id, TrinityCoreTier tier) {
        return BLOCKS.registerBlock(
                id,
                properties -> TrinityCoreBlock.parallelCpuCore(properties, tier),
                trinityCoreProperties());
    }

    private static DeferredBlock<TrinityPatternCoreBlock> registerPatternProcessingCore(String id, int patternCapacity) {
        return BLOCKS.registerBlock(
                id,
                properties -> TrinityCoreBlock.patternProcessingCore(properties, patternCapacity),
                trinityCoreProperties());
    }

    private static BlockBehaviour.Properties trinityCoreProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .noOcclusion()
                .isViewBlocking((state, blockGetter, pos) -> false)
                .requiresCorrectToolForDrops();
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
