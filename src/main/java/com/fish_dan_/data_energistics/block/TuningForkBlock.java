package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.TuningForkBlockEntity;
import com.fish_dan_.data_energistics.common.resonance.TuningForkVariant;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * A durable tuning fork that can only stand directly on a tuning-fork base.
 */
@Getter
public class TuningForkBlock extends Block implements EntityBlock {

    private final TuningForkVariant variant;

    public TuningForkBlock(TuningForkVariant variant, BlockBehaviour.Properties properties) {
        super(properties);
        this.variant = variant;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof TuningForkBaseBlock;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TuningForkBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof TuningForkBlockEntity tuningFork) {
            tuningFork.setDamage(stack.getDamageValue());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && level.getBlockEntity(pos) instanceof TuningForkBlockEntity tuningFork) {
            popResource(level, pos, tuningFork.createDrop());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
