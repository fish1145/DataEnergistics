package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.TuningForkBaseBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.block.AEBaseBlock;
import org.jetbrains.annotations.Nullable;

/**
 * AE-connected support for one tuning fork.
 */
public class TuningForkBaseBlock extends AEBaseBlock implements EntityBlock {

    public static final BooleanProperty ONLINE = BooleanProperty.create("online");
    private static final VoxelShape SHAPE = Shapes.or(
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
            Block.box(2, 8, 5, 5, 11, 11),
            Block.box(11, 9, 5, 14, 11, 11),
            Block.box(5, 9, 5, 11, 14, 11),
            Block.box(2, 9, 11, 5, 11, 14),
            Block.box(11, 9, 2, 14, 11, 5),
            Block.box(11, 9, 11, 14, 11, 14),
            Block.box(2, 9, 2, 5, 11, 5),
            Block.box(3.5d, 10.2346d, 6, 7.8352d, 17.0052d, 10),
            Block.box(8.1648d, 10.2346d, 6, 12.5d, 17.0052d, 10),
            Block.box(6, 10.2346d, 3.5d, 10, 17.0052d, 7.8352d),
            Block.box(6, 10.2346d, 8.1648d, 10, 17.0052d, 12.5d),
            Block.box(6, 14, 6, 10, 17, 7),
            Block.box(6, 14, 9, 10, 17, 10),
            Block.box(6, 14, 7, 7, 17, 9),
            Block.box(7, 14, 7, 9, 16, 9),
            Block.box(9, 14, 7, 10, 17, 9));

    public TuningForkBaseBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ONLINE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ONLINE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TuningForkBaseBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            BlockPos forkPos = pos.above();
            if (level.getBlockState(forkPos).getBlock() instanceof TuningForkBlock) {
                level.destroyBlock(forkPos, false);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
