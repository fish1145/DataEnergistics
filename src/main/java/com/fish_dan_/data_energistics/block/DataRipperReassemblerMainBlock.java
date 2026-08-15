package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEMenus;
import com.fish_dan_.data_energistics.util.BlockMemoryCardInteractionHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.hooks.WrenchHook;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import org.jspecify.annotations.Nullable;

public class DataRipperReassemblerMainBlock extends DataRipperReassemblerBlock implements EntityBlock {

    public DataRipperReassemblerMainBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new DataRipperReassemblerBlockEntity(blockPos, blockState);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hitResult) {
        ItemInteractionResult memoryCardResult = BlockMemoryCardInteractionHelper.useOnBlockEntity(stack, level, pos, player);
        if (memoryCardResult.consumesAction()) {
            return memoryCardResult;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DataRipperReassemblerBlockEntity reassembler) {
            MenuOpener.open(DEMenus.DATA_RIPPER_REASSEMBLER.get(), player, MenuLocators.forBlockEntity(reassembler));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && !WrenchHook.isDisassembling() && level.getBlockEntity(pos) instanceof DataRipperReassemblerBlockEntity reassembler) {
            reassembler.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != DEBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof DataRipperReassemblerBlockEntity reassembler) {
                reassembler.serverTick();
            }
        };
    }
}
