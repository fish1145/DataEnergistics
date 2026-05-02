package com.fish_dan_.data_energistics.block;

import appeng.block.AEBaseEntityBlock;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public class AdaptivePatternProviderBlock extends AEBaseEntityBlock<AdaptivePatternProviderBlockEntity> {
    public AdaptivePatternProviderBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.ALL));
    }

    public void bindBlockEntity() {
        this.setBlockEntity(
                AdaptivePatternProviderBlockEntity.class,
                ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(),
                null,
                null
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PatternProviderBlock.PUSH_DIRECTION);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        AdaptivePatternProviderBlockEntity blockEntity = this.getBlockEntity(level, pos);
        if (blockEntity != null) {
            blockEntity.getLogic().updateRedstoneState();
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (InteractionUtil.canWrenchRotate(stack)) {
            this.setSide(level, pos, hitResult.getDirection());
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        AdaptivePatternProviderBlockEntity blockEntity = this.getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            blockEntity.openMenu(player, MenuLocators.forBlockEntity(blockEntity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public void setSide(Level level, BlockPos pos, Direction side) {
        BlockState state = level.getBlockState(pos);
        Direction currentDirection = state.getValue(PatternProviderBlock.PUSH_DIRECTION).getDirection();
        PushDirection nextDirection;
        if (currentDirection == side.getOpposite()) {
            nextDirection = PushDirection.fromDirection(side);
        } else if (currentDirection == side) {
            nextDirection = PushDirection.ALL;
        } else if (currentDirection == null) {
            nextDirection = PushDirection.fromDirection(side.getOpposite());
        } else {
            nextDirection = PushDirection.fromDirection(appeng.util.Platform.rotateAround(currentDirection, side));
        }
        level.setBlockAndUpdate(pos, state.setValue(PatternProviderBlock.PUSH_DIRECTION, nextDirection));
    }
}
