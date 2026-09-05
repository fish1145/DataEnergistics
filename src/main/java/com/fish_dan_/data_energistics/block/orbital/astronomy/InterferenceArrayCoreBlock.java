package com.fish_dan_.data_energistics.block.orbital.astronomy;

import com.fish_dan_.data_energistics.blockentity.orbital.astronomy.InterferenceArrayCoreBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import appeng.block.AEBaseBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import org.jspecify.annotations.Nullable;

/**
 * AE-connected controller at the bottom center of a 5x5x3 high-tier interference array base.
 */
public final class InterferenceArrayCoreBlock extends AEBaseBlock implements EntityBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public InterferenceArrayCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InterferenceArrayCoreBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public void setPlacedBy(
                            Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player &&
                level.getBlockEntity(pos) instanceof InterferenceArrayCoreBlockEntity core) {
            core.setOwner(player);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
                                                                  Level level,
                                                                  BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != DEBlockEntities.INTERFERENCE_ARRAY_CORE_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof InterferenceArrayCoreBlockEntity core) {
                core.serverTick();
            }
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) &&
                level.getBlockEntity(pos) instanceof InterferenceArrayCoreBlockEntity core) {
            core.releaseMirrorClaims();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
