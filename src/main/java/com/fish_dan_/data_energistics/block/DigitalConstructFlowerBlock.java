package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.DigitalConstructFlowerBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

public class DigitalConstructFlowerBlock extends DataRipperReassemblerBlock implements EntityBlock {

    public DigitalConstructFlowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new DigitalConstructFlowerBlockEntity(blockPos, blockState);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        DigitalConstructFlowerBlockEntity.requestRecheckAround(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            DigitalConstructFlowerBlockEntity.requestRecheckAround(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        DigitalConstructFlowerBlockEntity.requestRecheckAround(level, pos);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
                                                                            BlockState state,
                                                                            BlockEntityType<T> blockEntityType) {
        if (!(level instanceof ServerLevel)) {
            return null;
        }
        if (blockEntityType != ModBlockEntities.DIGITAL_CONSTRUCT_FLOWER_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickerLevel, tickerPos, tickerState, tickerBlockEntity) -> ((DigitalConstructFlowerBlockEntity) tickerBlockEntity).serverTick();
    }
}
