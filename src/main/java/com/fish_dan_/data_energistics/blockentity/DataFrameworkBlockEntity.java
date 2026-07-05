package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.block.DataFrameworkBlock;
import com.fish_dan_.data_energistics.block.DataFrameworkMainBlock;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockDefinition;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class DataFrameworkBlockEntity extends AbstractVerticalMultiBlockBlockEntity {

    public DataFrameworkBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_FRAMEWORK_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_FRAMEWORK_MAIN.get())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public void onReady() {
        super.onReady();
        updatePoweredState();
        onVerticalMultiBlockReady();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        updatePoweredState();
        serverTickVerticalMultiBlock();
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public static void requestRecheckAround(Level level, BlockPos origin) {
        requestVerticalMultiBlockRecheckAround(
                level,
                origin,
                ModVerticalMultiBlocks.DATA_FRAMEWORK_COLUMN_MAX_HEIGHT,
                blockEntity -> blockEntity instanceof DataFrameworkBlockEntity);
    }

    @Override
    public String verticalMultiBlock$getDefinitionId() {
        return ModVerticalMultiBlocks.DATA_FRAMEWORK_COLUMN_ID;
    }

    @Override
    protected VerticalMultiBlockDefinition<BlockState> verticalMultiBlock$getDefinition() {
        return ModVerticalMultiBlocks.VERTICAL_MULTI_BLOCKS
                .get(verticalMultiBlock$getDefinitionId())
                .orElseThrow(() -> new IllegalStateException("Missing vertical multiblock definition: " + verticalMultiBlock$getDefinitionId()));
    }

    @Override
    protected boolean verticalMultiBlock$isControllerBlockedBy(BlockState belowState) {
        return belowState.is(ModBlocks.DATA_FRAMEWORK.get()) || belowState.is(ModBlocks.DATA_FRAMEWORK_MAIN.get());
    }

    @Override
    protected String verticalMultiBlock$getControllerBlockedInvalidReason() {
        return "Data Framework column controller is no longer the bottom block";
    }

    private void updatePoweredState() {
        if (this.level == null) {
            return;
        }
        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof DataFrameworkMainBlock)) {
            return;
        }
        boolean online = this.getMainNode().isOnline();
        if (state.getValue(DataFrameworkBlock.POWERED) != online) {
            this.level.setBlock(this.worldPosition, state.setValue(DataFrameworkBlock.POWERED, online), 3);
        }
    }
}
