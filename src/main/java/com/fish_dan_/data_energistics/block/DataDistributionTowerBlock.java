package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.util.BlockMemoryCardInteractionHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import appeng.block.AEBaseBlock;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.util.InteractionUtil;
import appeng.util.SettingsFrom;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = Data_Energistics.MODID)
public class DataDistributionTowerBlock extends AEBaseBlock implements EntityBlock {

    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape BOTTOM_SHAPE_NORTH = Shapes.or(
            Block.box(2, 0, 2, 14, 9, 14),
            Block.box(0, 1, 0, 16, 5, 16),
            Block.box(2, 4.0433d, 0.4619d, 5, 8.5042d, 3.8404d),
            Block.box(11, 4.0433d, 0.4619d, 14, 8.5042d, 3.8404d),
            Block.box(12.1596d, 4.0433d, 11, 15.5381d, 8.5042d, 14),
            Block.box(12.1596d, 4.0433d, 2, 15.5381d, 8.5042d, 5),
            Block.box(11, 4.0433d, 12.1596d, 14, 8.5042d, 15.5381d),
            Block.box(2, 4.0433d, 12.1596d, 5, 8.5042d, 15.5381d),
            Block.box(0.4619d, 4.0433d, 2, 3.8404d, 8.5042d, 5),
            Block.box(0.4619d, 4.0433d, 11, 3.8404d, 8.5042d, 14),
            Block.box(5, 5, 0, 11, 11, 2),
            Block.box(0, 5, 5, 2, 11, 11),
            Block.box(5, 5, 14, 11, 11, 16),
            Block.box(14, 5, 5, 16, 11, 11),
            Block.box(7, 1, 0, 9, 3, 1),
            Block.box(0, 1, 7, 1, 3, 9),
            Block.box(7, 1, 15, 9, 3, 16),
            Block.box(15, 1, 7, 16, 3, 9),
            Block.box(5, 9, 2, 11, 11, 5),
            Block.box(5, 9, 11, 11, 11, 14),
            Block.box(2, 9, 5, 14, 13, 11),
            Block.box(4, 13, 5, 12, 16, 11),
            Block.box(0, 11, 6, 4, 16, 10),
            Block.box(12, 11, 6, 16, 15, 10),
            Block.box(1, 15, 7, 4, 16, 9),
            Block.box(12, 15, 7, 15, 16, 9));
    private static final VoxelShape BOTTOM_SHAPE_EAST = Shapes.or(
            Block.box(2, 0, 2, 14, 9, 14),
            Block.box(0, 1, 0, 16, 5, 16),
            Block.box(0.4619d, 4.0433d, 11, 3.8404d, 8.5042d, 14),
            Block.box(0.4619d, 4.0433d, 2, 3.8404d, 8.5042d, 5),
            Block.box(11, 4.0433d, 0.4619d, 14, 8.5042d, 3.8404d),
            Block.box(2, 4.0433d, 0.4619d, 5, 8.5042d, 3.8404d),
            Block.box(12.1596d, 4.0433d, 2, 15.5381d, 8.5042d, 5),
            Block.box(12.1596d, 4.0433d, 11, 15.5381d, 8.5042d, 14),
            Block.box(2, 4.0433d, 12.1596d, 5, 8.5042d, 15.5381d),
            Block.box(11, 4.0433d, 12.1596d, 14, 8.5042d, 15.5381d),
            Block.box(0, 5, 5, 2, 11, 11),
            Block.box(5, 5, 14, 11, 11, 16),
            Block.box(14, 5, 5, 16, 11, 11),
            Block.box(5, 5, 0, 11, 11, 2),
            Block.box(0, 1, 7, 1, 3, 9),
            Block.box(7, 1, 15, 9, 3, 16),
            Block.box(15, 1, 7, 16, 3, 9),
            Block.box(7, 1, 0, 9, 3, 1),
            Block.box(2, 9, 5, 5, 11, 11),
            Block.box(11, 9, 5, 14, 11, 11),
            Block.box(5, 9, 2, 11, 13, 14),
            Block.box(5, 13, 4, 11, 16, 12),
            Block.box(6, 11, 12, 10, 15, 16),
            Block.box(6, 11, 0, 10, 15, 4),
            Block.box(7, 15, 12, 9, 16, 15),
            Block.box(7, 15, 1, 9, 16, 4));
    private static final VoxelShape BOTTOM_SHAPE_SOUTH = Shapes.or(
            Block.box(2, 0, 2, 14, 9, 14),
            Block.box(0, 1, 0, 16, 5, 16),
            Block.box(11, 4.0433d, 12.1596d, 14, 8.5042d, 15.5381d),
            Block.box(2, 4.0433d, 12.1596d, 5, 8.5042d, 15.5381d),
            Block.box(0.4619d, 4.0433d, 2, 3.8404d, 8.5042d, 5),
            Block.box(0.4619d, 4.0433d, 11, 3.8404d, 8.5042d, 14),
            Block.box(2, 4.0433d, 0.4619d, 5, 8.5042d, 3.8404d),
            Block.box(11, 4.0433d, 0.4619d, 14, 8.5042d, 3.8404d),
            Block.box(12.1596d, 4.0433d, 11, 15.5381d, 8.5042d, 14),
            Block.box(12.1596d, 4.0433d, 2, 15.5381d, 8.5042d, 5),
            Block.box(5, 5, 14, 11, 11, 16),
            Block.box(14, 5, 5, 16, 11, 11),
            Block.box(5, 5, 0, 11, 11, 2),
            Block.box(0, 5, 5, 2, 11, 11),
            Block.box(7, 1, 15, 9, 3, 16),
            Block.box(15, 1, 7, 16, 3, 9),
            Block.box(7, 1, 0, 9, 3, 1),
            Block.box(0, 1, 7, 1, 3, 9),
            Block.box(5, 9, 11, 11, 11, 14),
            Block.box(5, 9, 2, 11, 11, 5),
            Block.box(2, 9, 5, 14, 13, 11),
            Block.box(4, 13, 5, 12, 16, 11),
            Block.box(12, 11, 6, 16, 15, 10),
            Block.box(0, 11, 6, 4, 15, 10),
            Block.box(12, 15, 7, 15, 16, 9),
            Block.box(1, 15, 7, 4, 16, 9));
    private static final VoxelShape BOTTOM_SHAPE_WEST = Shapes.or(
            Block.box(2, 0, 2, 14, 9, 14),
            Block.box(0, 1, 0, 16, 5, 16),
            Block.box(12.1596d, 4.0433d, 2, 15.5381d, 8.5042d, 5),
            Block.box(12.1596d, 4.0433d, 11, 15.5381d, 8.5042d, 14),
            Block.box(2, 4.0433d, 12.1596d, 5, 8.5042d, 15.5381d),
            Block.box(11, 4.0433d, 12.1596d, 14, 8.5042d, 15.5381d),
            Block.box(0.4619d, 4.0433d, 11, 3.8404d, 8.5042d, 14),
            Block.box(0.4619d, 4.0433d, 2, 3.8404d, 8.5042d, 5),
            Block.box(11, 4.0433d, 0.4619d, 14, 8.5042d, 3.8404d),
            Block.box(2, 4.0433d, 0.4619d, 5, 8.5042d, 3.8404d),
            Block.box(14, 5, 5, 16, 11, 11),
            Block.box(5, 5, 0, 11, 11, 2),
            Block.box(0, 5, 5, 2, 11, 11),
            Block.box(5, 5, 14, 11, 11, 16),
            Block.box(15, 1, 7, 16, 3, 9),
            Block.box(7, 1, 0, 9, 3, 1),
            Block.box(0, 1, 7, 1, 3, 9),
            Block.box(7, 1, 15, 9, 3, 16),
            Block.box(11, 9, 5, 14, 11, 11),
            Block.box(2, 9, 5, 5, 11, 11),
            Block.box(5, 9, 2, 11, 13, 14),
            Block.box(5, 13, 4, 11, 16, 12),
            Block.box(6, 11, 0, 10, 15, 4),
            Block.box(6, 11, 12, 10, 15, 16),
            Block.box(7, 15, 1, 9, 16, 4),
            Block.box(7, 15, 12, 9, 16, 15));
    private static final VoxelShape MIDDLE_SHAPE_NORTH = Shapes.or(
            Block.box(1, 0, 7, 4, 10, 9),
            Block.box(0, 1, 6, 3, 11, 10),
            Block.box(1, 10, 7.5d, 4, 16, 8.5d),
            Block.box(0, 4, 6, 1, 8, 10),
            Block.box(12, 0, 7, 15, 10, 9),
            Block.box(13, 1, 6, 16, 11, 10),
            Block.box(12, 10, 7.5d, 15, 16, 8.5d),
            Block.box(15, 4, 6, 16, 8, 10),
            Block.box(6, 0, 6, 10, 14, 10),
            Block.box(5, 0, 5, 8, 7, 11),
            Block.box(9, 0, 5, 11, 6, 11),
            Block.box(7, 8, 5, 11, 14, 11),
            Block.box(5, 7, 7, 6, 13, 9),
            Block.box(5, 14, 5, 11, 16, 11));
    private static final VoxelShape MIDDLE_SHAPE_EAST = Shapes.or(
            Block.box(7, 0, 12, 9, 10, 15),
            Block.box(6, 1, 13, 10, 11, 16),
            Block.box(7.5d, 10, 12, 8.5d, 16, 15),
            Block.box(6, 4, 15, 10, 8, 16),
            Block.box(7, 0, 1, 9, 10, 4),
            Block.box(6, 1, 0, 10, 11, 3),
            Block.box(7.5d, 10, 1, 8.5d, 16, 4),
            Block.box(6, 4, 0, 10, 8, 1),
            Block.box(6, 0, 6, 10, 14, 10),
            Block.box(5, 0, 8, 11, 7, 11),
            Block.box(5, 0, 5, 11, 6, 7),
            Block.box(5, 8, 5, 11, 14, 9),
            Block.box(7, 7, 10, 9, 13, 11),
            Block.box(5, 14, 5, 11, 16, 11));
    private static final VoxelShape MIDDLE_SHAPE_SOUTH = Shapes.or(
            Block.box(12, 0, 7, 15, 10, 9),
            Block.box(13, 1, 6, 16, 11, 10),
            Block.box(12, 10, 7.5d, 15, 16, 8.5d),
            Block.box(15, 4, 6, 16, 8, 10),
            Block.box(1, 0, 7, 4, 10, 9),
            Block.box(0, 1, 6, 3, 11, 10),
            Block.box(1, 10, 7.5d, 4, 16, 8.5d),
            Block.box(0, 4, 6, 1, 8, 10),
            Block.box(6, 0, 6, 10, 14, 10),
            Block.box(8, 0, 5, 11, 7, 11),
            Block.box(5, 0, 5, 7, 6, 11),
            Block.box(5, 8, 5, 9, 14, 11),
            Block.box(10, 7, 7, 11, 13, 9),
            Block.box(5, 14, 5, 11, 16, 11));
    private static final VoxelShape MIDDLE_SHAPE_WEST = Shapes.or(
            Block.box(7, 0, 1, 9, 10, 4),
            Block.box(6, 1, 0, 10, 11, 3),
            Block.box(7.5d, 10, 1, 8.5d, 16, 4),
            Block.box(6, 4, 0, 10, 8, 1),
            Block.box(7, 0, 12, 9, 10, 15),
            Block.box(6, 1, 13, 10, 11, 16),
            Block.box(7.5d, 10, 12, 8.5d, 16, 15),
            Block.box(6, 4, 15, 10, 8, 16),
            Block.box(6, 0, 6, 10, 14, 10),
            Block.box(5, 0, 5, 11, 7, 8),
            Block.box(5, 0, 9, 11, 6, 11),
            Block.box(5, 8, 7, 11, 14, 11),
            Block.box(7, 7, 5, 9, 13, 6),
            Block.box(5, 14, 5, 11, 16, 11));
    private static final VoxelShape TOP_SHAPE_NORTH = Shapes.or(
            Block.box(6, 4, 7, 8, 6, 9),
            Block.box(6, 0, 7, 10, 4, 9),
            Block.box(2, 0, 7.5d, 4, 4, 8.5d),
            Block.box(12, 0, 7.5d, 14, 4, 8.5d),
            Block.box(8, 6, 7, 10, 8, 9),
            Block.box(6, 13, 7, 8, 15, 9),
            Block.box(6, 8, 7, 10, 13, 9));
    private static final VoxelShape TOP_SHAPE_EAST = Shapes.or(
            Block.box(7, 4, 8, 9, 6, 10),
            Block.box(7, 0, 6, 9, 4, 10),
            Block.box(7.5d, 0, 12, 8.5d, 4, 14),
            Block.box(7.5d, 0, 2, 8.5d, 4, 4),
            Block.box(7, 6, 6, 9, 8, 8),
            Block.box(7, 13, 8, 9, 15, 10),
            Block.box(7, 8, 6, 9, 13, 10));
    private static final VoxelShape TOP_SHAPE_SOUTH = Shapes.or(
            Block.box(8, 4, 7, 10, 6, 9),
            Block.box(6, 0, 7, 10, 4, 9),
            Block.box(12, 0, 7.5d, 14, 4, 8.5d),
            Block.box(2, 0, 7.5d, 4, 4, 8.5d),
            Block.box(6, 6, 7, 8, 8, 9),
            Block.box(8, 13, 7, 10, 15, 9),
            Block.box(6, 8, 7, 10, 13, 9));
    private static final VoxelShape TOP_SHAPE_WEST = Shapes.or(
            Block.box(7, 4, 6, 9, 6, 8),
            Block.box(7, 0, 6, 9, 4, 10),
            Block.box(7.5d, 0, 2, 8.5d, 4, 4),
            Block.box(7.5d, 0, 12, 8.5d, 4, 14),
            Block.box(7, 6, 8, 9, 8, 10),
            Block.box(7, 13, 6, 9, 15, 8),
            Block.box(7, 8, 6, 9, 13, 10));
    private static final VoxelShape BOTTOM_INTERACTION_SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape MIDDLE_INTERACTION_SHAPE = Block.box(0, 0, 5, 16, 16, 11);
    private static final VoxelShape TOP_INTERACTION_SHAPE = Block.box(2, 0, 6, 14, 15, 10);
    private static final Map<Direction, VoxelShape> BOTTOM_SHAPES = Map.of(
            Direction.NORTH, BOTTOM_SHAPE_NORTH,
            Direction.EAST, BOTTOM_SHAPE_WEST,
            Direction.SOUTH, BOTTOM_SHAPE_SOUTH,
            Direction.WEST, BOTTOM_SHAPE_EAST);
    private static final Map<Direction, VoxelShape> MIDDLE_SHAPES = Map.of(
            Direction.NORTH, MIDDLE_SHAPE_NORTH,
            Direction.EAST, MIDDLE_SHAPE_WEST,
            Direction.SOUTH, MIDDLE_SHAPE_SOUTH,
            Direction.WEST, MIDDLE_SHAPE_EAST);
    private static final Map<Direction, VoxelShape> TOP_SHAPES = Map.of(
            Direction.NORTH, TOP_SHAPE_NORTH,
            Direction.EAST, TOP_SHAPE_WEST,
            Direction.SOUTH, TOP_SHAPE_SOUTH,
            Direction.WEST, TOP_SHAPE_EAST);

    public DataDistributionTowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART, FACING, ACTIVE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getPartShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getPartShape(state);
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getInteractionShapeForPart(state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 2) {
            return null;
        }

        if (!level.getBlockState(pos.above()).canBeReplaced(context) || !level.getBlockState(pos.above(2)).canBeReplaced(context)) {
            return null;
        }

        return this.defaultBlockState()
                .setValue(PART, 0)
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ACTIVE, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.setBlock(pos.above(), state.setValue(PART, 1), Block.UPDATE_ALL);
        level.setBlock(pos.above(2), state.setValue(PART, 2), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult memoryCardResult = BlockMemoryCardInteractionHelper.useOnBlockEntity(
                heldItem,
                level,
                getBasePos(pos, state),
                player);
        if (memoryCardResult.consumesAction()) {
            return memoryCardResult;
        }
        if (canDisassembleWithWrench(heldItem)) {
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
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (containerId, playerInventory, menuPlayer) -> new com.fish_dan_.data_energistics.menu.DataDistributionTowerMenu(
                                containerId,
                                playerInventory,
                                tower),
                        Component.empty()), buffer -> buffer.writeBlockPos(basePos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!level.isClientSide()) {
            boolean showing = tower.toggleRangeDisplay();
            player.displayClientMessage(Component.translatable(
                    showing ? "message.data_energistics.data_distribution_tower.range.enabled" : "message.data_energistics.data_distribution_tower.range.disabled"), true);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && state.getValue(PART) == 0) {
                dropAdditionalBlockEntityContents(level, pos);
            }
            removeOtherParts(level, pos, state);
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(PART) != 0) {
            breakTower(level, getBasePos(pos, state), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
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

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity blockEntity = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        net.minecraft.world.entity.Entity looter = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY);
        Player player = looter instanceof Player lootPlayer ? lootPlayer : null;
        return List.of(createTowerItemDrop(blockEntity, player));
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

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(ModBlocks.DATA_DISTRIBUTION_TOWER.get()) || !event.getEntity().isShiftKeyDown() || !canDisassembleWithWrench(event.getItemStack())) {
            return;
        }

        if (state.getBlock() instanceof DataDistributionTowerBlock towerBlock && !event.getLevel().isClientSide()) {
            towerBlock.dismantleTower(event.getLevel(), getBasePos(event.getPos(), state), event.getEntity());
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        if (!(event.getLevel() instanceof Level level) || level.isClientSide() || !state.is(ModBlocks.DATA_DISTRIBUTION_TOWER.get())) {
            return;
        }

        if (state.getBlock() instanceof DataDistributionTowerBlock towerBlock && towerBlock.breakTower(level, getBasePos(event.getPos(), state), event.getPlayer())) {
            event.setCanceled(true);
        }
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

    private boolean breakTower(Level level, BlockPos basePos, Player player) {
        BlockState baseState = level.getBlockState(basePos);
        if (!baseState.is(this) || baseState.getValue(PART) != 0) {
            return false;
        }

        if (player.getAbilities().instabuild) {
            level.setBlock(basePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            return true;
        }

        if (level instanceof ServerLevel serverLevel) {
            BlockEntity blockEntity = level.getBlockEntity(basePos);
            List<ItemStack> drops = new ArrayList<>();
            drops.add(createTowerItemDrop(blockEntity, player));
            if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
                tower.addAdditionalDrops(level, basePos, drops);
                tower.clearContent();
            }
            level.setBlock(basePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    Block.popResource(level, basePos, drop);
                }
            }
        }
        return true;
    }

    private void dropAdditionalBlockEntityContents(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof DataDistributionTowerBlockEntity tower)) {
            return;
        }

        List<ItemStack> drops = new ArrayList<>();
        tower.addAdditionalDrops(level, pos, drops);
        tower.clearContent();
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
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

        List<ItemStack> drops = new ArrayList<>();
        drops.add(createTowerItemDrop(blockEntity, player));
        if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
            tower.addAdditionalDrops(level, basePos, drops);
            tower.clearContent();
        }
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

    private static boolean canDisassembleWithWrench(ItemStack stack) {
        return InteractionUtil.canWrenchDisassemble(stack) || AEItems.NETWORK_TOOL.is(stack);
    }

    private static VoxelShape getPartShape(BlockState state) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(PART)) {
            case 1 -> MIDDLE_SHAPES.getOrDefault(facing, MIDDLE_SHAPE_NORTH);
            case 2 -> TOP_SHAPES.getOrDefault(facing, TOP_SHAPE_NORTH);
            default -> BOTTOM_SHAPES.getOrDefault(facing, BOTTOM_SHAPE_NORTH);
        };
    }

    private static VoxelShape getInteractionShapeForPart(BlockState state) {
        return switch (state.getValue(PART)) {
            case 1 -> MIDDLE_INTERACTION_SHAPE;
            case 2 -> TOP_INTERACTION_SHAPE;
            default -> BOTTOM_INTERACTION_SHAPE;
        };
    }

    private ItemStack createTowerItemDrop(@Nullable BlockEntity blockEntity, @Nullable Player player) {
        ItemStack towerItem = new ItemStack(this);
        if (blockEntity instanceof AEBaseBlockEntity aeBlockEntity) {
            towerItem.applyComponents(aeBlockEntity.exportSettings(SettingsFrom.DISMANTLE_ITEM, player));
        }
        return towerItem;
    }
}
