package com.fish_dan_.data_energistics.block.patternprovider;

import com.fish_dan_.data_energistics.accessor.patternprovider.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.blockentity.patternprovider.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.common.memorycard.BlockMemoryCardInteractionHelper;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import appeng.util.Platform;

public class AdaptivePatternProviderBlock<T extends AdaptivePatternProviderBlockEntity> extends AEBaseEntityBlock<T> {

    public AdaptivePatternProviderBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.ALL));
    }

    public void bindBlockEntity() {
        bindBlockEntity((Class<T>) AdaptivePatternProviderBlockEntity.class, (BlockEntityType<T>) DEBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get());
    }

    protected void bindBlockEntity(
                                   Class<T> blockEntityClass,
                                   BlockEntityType<T> blockEntityType) {
        this.setBlockEntity(blockEntityClass, blockEntityType, null, null);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdaptivePatternProviderBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PatternProviderBlock.PUSH_DIRECTION);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof RedstoneTuningAwareHost host && host.dataEnergistics$isRedstoneTuningPulseActive() ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.getSignal(state, level, pos, direction);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        T blockEntity = this.getBlockEntity(level, pos);
        if (blockEntity instanceof RedstoneTuningAwareHost host) {
            host.dataEnergistics$serverTick();
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        T blockEntity = this.getBlockEntity(level, pos);
        if (blockEntity != null) {
            blockEntity.getLogic().updateRedstoneState();
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        ItemInteractionResult memoryCardResult = BlockMemoryCardInteractionHelper.useOnBlockEntity(stack, level, pos, player);
        if (memoryCardResult.consumesAction()) {
            return memoryCardResult;
        }
        if (InteractionUtil.canWrenchRotate(stack)) {
            this.setSide(level, pos, hitResult.getDirection());
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        T blockEntity = this.getBlockEntity(level, pos);
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
            nextDirection = PushDirection.fromDirection(Platform.rotateAround(currentDirection, side));
        }
        level.setBlockAndUpdate(pos, state.setValue(PatternProviderBlock.PUSH_DIRECTION, nextDirection));
    }
}
