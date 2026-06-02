package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.block.DataFrameworkBlock;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import appeng.blockentity.grid.AENetworkedBlockEntity;

public class DataFrameworkBlockEntity extends AENetworkedBlockEntity {

    public DataFrameworkBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_FRAMEWORK_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_FRAMEWORK.get())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public void onReady() {
        super.onReady();
        updatePoweredState();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        updatePoweredState();
    }

    private void updatePoweredState() {
        if (this.level == null) {
            return;
        }
        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof DataFrameworkBlock)) {
            return;
        }
        boolean online = this.getMainNode().isOnline();
        if (state.getValue(DataFrameworkBlock.POWERED) != online) {
            this.level.setBlock(this.worldPosition, state.setValue(DataFrameworkBlock.POWERED, online), 3);
        }
    }
}
