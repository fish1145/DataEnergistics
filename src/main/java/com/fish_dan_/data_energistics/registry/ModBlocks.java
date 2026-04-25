package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataFlowGeneratorBlock;
import com.fish_dan_.data_energistics.block.DataFrameworkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Data_Energistics.MODID);

    public static final DeferredBlock<Block> DATA_FLOW_GENERATOR = BLOCKS.registerBlock(
            "data_flow_generator",
            DataFlowGeneratorBlock::new,
            BlockBehaviour.Properties.ofLegacyCopy(Blocks.IRON_BLOCK));

    public static final DeferredBlock<Block> DATA_FRAMEWORK = BLOCKS.registerBlock(
            "data_framework",
            DataFrameworkBlock::new,
            BlockBehaviour.Properties.ofLegacyCopy(Blocks.QUARTZ_BLOCK));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
