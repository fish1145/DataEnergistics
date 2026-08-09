package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.TuningForkBaseBlockEntity;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseBlock;
import org.jetbrains.annotations.Nullable;

/**
 * AE-connected support for one tuning fork and one removable digitalization core.
 */
public class TuningForkBaseBlock extends AEBaseBlock implements EntityBlock {

    public TuningForkBaseBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TuningForkBaseBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (!stack.is(DEItems.RESONANCE_DIGITALIZATION_CORE.get())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof TuningForkBaseBlockEntity base) || !base.installCore(stack)) {
            return ItemInteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof TuningForkBaseBlockEntity base)) {
            return InteractionResult.FAIL;
        }

        ItemStack core = base.removeCore();
        if (core.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!player.getInventory().add(core)) {
            popResource(level, pos, core);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            BlockPos forkPos = pos.above();
            if (level.getBlockState(forkPos).getBlock() instanceof TuningForkBlock) {
                level.destroyBlock(forkPos, false);
            }
            if (level.getBlockEntity(pos) instanceof TuningForkBaseBlockEntity base) {
                ItemStack core = base.removeCore();
                if (!core.isEmpty()) {
                    Block.popResource(level, pos, core);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
