package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DataSanctumReturnPortalBlockEntity extends BlockEntity {

    public DataSanctumReturnPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_SANCTUM_RETURN_PORTAL_BLOCK_ENTITY.get(), pos, state);
    }
}
