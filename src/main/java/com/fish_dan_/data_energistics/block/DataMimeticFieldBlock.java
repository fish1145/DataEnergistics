package com.fish_dan_.data_energistics.block;

import appeng.block.AEBaseBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class DataMimeticFieldBlock extends AEBaseBlock implements EntityBlock {
    public DataMimeticFieldBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new DataMimeticFieldBlockEntity(blockPos, blockState);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DataMimeticFieldBlockEntity field) {
            MenuOpener.open(ModMenus.DATA_MIMETIC_FIELD.get(), player, MenuLocators.forBlockEntity(field));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof DataMimeticFieldBlockEntity field) {
                field.serverTick();
            }
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof DataMimeticFieldBlockEntity field) {
            field.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
