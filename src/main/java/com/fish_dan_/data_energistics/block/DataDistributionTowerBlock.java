package com.fish_dan_.data_energistics.block;

import appeng.block.AEBaseBlock;
import appeng.core.definitions.AEItems;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DataDistributionTowerBlock extends AEBaseBlock implements EntityBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);

    public DataDistributionTowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 2) {
            return null;
        }

        if (!level.getBlockState(pos.above()).canBeReplaced(context)
                || !level.getBlockState(pos.above(2)).canBeReplaced(context)) {
            return null;
        }

        return this.defaultBlockState().setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.setBlock(pos.above(), state.setValue(PART, 1), Block.UPDATE_ALL);
        level.setBlock(pos.above(2), state.setValue(PART, 2), Block.UPDATE_ALL);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (isAeWrench(heldItem)) {
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide()) {
                    dismantleTower(level, getBasePos(pos, state), player);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }

            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos basePos = getBasePos(pos, state);
        if (!(level.getBlockEntity(basePos) instanceof DataDistributionTowerBlockEntity tower)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                MenuOpener.open(ModMenus.DATA_DISTRIBUTION_TOWER.get(), player, MenuLocators.forBlockEntity(tower));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!level.isClientSide()) {
            boolean showing = tower.toggleRangeDisplay();
            player.displayClientMessage(Component.translatable(
                    showing
                            ? "message.data_energistics.data_distribution_tower.range.enabled"
                            : "message.data_energistics.data_distribution_tower.range.disabled"
            ), true);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            removeOtherParts(level, pos, state);
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        int part = state.getValue(PART);
        if (direction.getAxis() == Direction.Axis.Y) {
            if (part == 0 && direction == Direction.UP) {
                if (!neighborState.is(this) || neighborState.getValue(PART) != 1) {
                    return Blocks.AIR.defaultBlockState();
                }
            } else if (part == 1) {
                if (direction == Direction.DOWN && (!neighborState.is(this) || neighborState.getValue(PART) != 0)) {
                    return Blocks.AIR.defaultBlockState();
                }
                if (direction == Direction.UP && (!neighborState.is(this) || neighborState.getValue(PART) != 2)) {
                    return Blocks.AIR.defaultBlockState();
                }
            } else if (part == 2 && direction == Direction.DOWN) {
                if (!neighborState.is(this) || neighborState.getValue(PART) != 1) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
        }

        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new DataDistributionTowerBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || state.getValue(PART) != 0 || blockEntityType != ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
                tower.serverTick();
            }
        };
    }

    public static BlockPos getBasePos(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(PART));
    }

    private void removeOtherParts(Level level, BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        if (part == 0) {
            destroyPart(level, pos.above());
            destroyPart(level, pos.above(2));
        } else if (part == 1) {
            destroyPart(level, pos.below());
            destroyPart(level, pos.above());
        } else {
            destroyPart(level, pos.below());
            destroyPart(level, pos.below(2));
        }
    }

    private void destroyPart(Level level, BlockPos targetPos) {
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.is(this)) {
            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    private void dismantleTower(Level level, BlockPos basePos, Player player) {
        BlockState baseState = level.getBlockState(basePos);
        if (!baseState.is(this) || baseState.getValue(PART) != 0) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(basePos);
        if (player.getAbilities().instabuild) {
            level.setBlock(basePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            return;
        }

        List<ItemStack> drops = Block.getDrops(baseState, serverLevel, basePos, blockEntity, player, player.getMainHandItem());
        level.setBlock(basePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }

            ItemStack remaining = drop.copy();
            if (!player.addItem(remaining)) {
                Block.popResource(level, player.blockPosition(), remaining);
            }
        }
    }

    private boolean isAeWrench(ItemStack stack) {
        return AEItems.CERTUS_QUARTZ_WRENCH.is(stack)
                || AEItems.NETHER_QUARTZ_WRENCH.is(stack)
                || AEItems.NETWORK_TOOL.is(stack);
    }
}
