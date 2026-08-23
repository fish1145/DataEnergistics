package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class DataAsynchronousProcessingFactoryBlockEntity extends DataRipperReassemblerBlockEntity {

    public DataAsynchronousProcessingFactoryBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_ASYNCHRONOUS_PROCESSING_FACTORY_BLOCK_ENTITY.get(),
                DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(), blockPos, blockState);
    }

    @Override
    protected Block getMachineBlock() {
        return DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get();
    }
}
