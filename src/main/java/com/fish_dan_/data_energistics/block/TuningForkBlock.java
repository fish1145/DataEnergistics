package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.TuningForkBlockEntity;
import com.fish_dan_.data_energistics.common.resonance.TuningForkVariant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * A durable tuning fork that can only stand directly on a tuning-fork base.
 */
@Getter
public class TuningForkBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape NORTH_SOUTH_SHAPE = Shapes.or(
            Block.box(7, 0, 7, 9, 6, 9),
            Block.box(6, 6, 6, 10, 10, 10),
            Block.box(4.1502d, 7, 6.998d, 6.7658d, 9.6132d, 9.002d),
            Block.box(9.2342d, 7, 6.998d, 11.8498d, 9.6132d, 9.002d),
            Block.box(4.1502d, 7.7633d, 6.998d, 6.1543d, 15.7674d, 9.002d),
            Block.box(9.8457d, 7.7633d, 6.998d, 11.8498d, 15.7674d, 9.002d));
    private static final VoxelShape EAST_WEST_SHAPE = Shapes.or(
            Block.box(7, 0, 7, 9, 6, 9),
            Block.box(6, 6, 6, 10, 10, 10),
            Block.box(6.998d, 7, 4.1502d, 9.002d, 9.6132d, 6.7658d),
            Block.box(6.998d, 7, 9.2342d, 9.002d, 9.6132d, 11.8498d),
            Block.box(6.998d, 7.7633d, 4.1502d, 9.002d, 15.7674d, 6.1543d),
            Block.box(6.998d, 7.7633d, 9.8457d, 9.002d, 15.7674d, 11.8498d));

    private final TuningForkVariant variant;

    public TuningForkBlock(TuningForkVariant variant, BlockBehaviour.Properties properties) {
        super(properties);
        this.variant = variant;
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? EAST_WEST_SHAPE : NORTH_SOUTH_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return getShape(state, level, pos, context);
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
