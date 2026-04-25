package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import appeng.blockentity.AEBaseBlockEntity;

public class DataFrameworkBlockEntity extends AEBaseBlockEntity {
    public DataFrameworkBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_FRAMEWORK_BLOCK_ENTITY.get(), blockPos, blockState);
    }
}
