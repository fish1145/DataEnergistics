package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataFlowGeneratorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataFrameworkBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Data_Energistics.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataFlowGeneratorBlockEntity>> DATA_FLOW_GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "data_flow_generator",
                    () -> BlockEntityType.Builder.of(DataFlowGeneratorBlockEntity::new, ModBlocks.DATA_FLOW_GENERATOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataExtractorBlockEntity>> DATA_EXTRACTOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "data_extractor",
                    () -> BlockEntityType.Builder.of(DataExtractorBlockEntity::new, ModBlocks.DATA_EXTRACTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataFrameworkBlockEntity>> DATA_FRAMEWORK_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "data_framework",
                    () -> BlockEntityType.Builder.of(DataFrameworkBlockEntity::new, ModBlocks.DATA_FRAMEWORK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataDistributionTowerBlockEntity>> DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "data_distribution_tower",
                    () -> BlockEntityType.Builder.of(DataDistributionTowerBlockEntity::new, ModBlocks.DATA_DISTRIBUTION_TOWER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataMimeticFieldBlockEntity>> DATA_MIMETIC_FIELD_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "data_mimetic_field",
                    () -> BlockEntityType.Builder.of(DataMimeticFieldBlockEntity::new, ModBlocks.DATA_MIMETIC_FIELD.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
