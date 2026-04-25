package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.DataFrameworkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import appeng.block.AEBaseBlock;

public class DataFrameworkBlock extends AEBaseBlock implements EntityBlock {
    public DataFrameworkBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new DataFrameworkBlockEntity(blockPos, blockState);
    }
}
