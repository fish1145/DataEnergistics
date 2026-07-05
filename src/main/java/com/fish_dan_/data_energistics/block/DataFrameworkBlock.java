package com.fish_dan_.data_energistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.block.AEBaseBlock;
import org.jetbrains.annotations.NotNull;

public class DataFrameworkBlock extends AEBaseBlock {

    public DataFrameworkBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter blockGetter, @NotNull BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter blockGetter,
                                          @NotNull BlockPos pos) {
        return true;
    }

    @Override
    protected @NotNull VoxelShape getVisualShape(@NotNull BlockState state, @NotNull BlockGetter world,
                                                 @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean skipRendering(@NotNull BlockState state, @NotNull BlockState adjacentBlockState,
                                    @NotNull Direction side) {
        if (adjacentBlockState.is(this) && adjacentBlockState.getRenderShape() == state.getRenderShape()) {
            return true;
        }
        return super.skipRendering(state, adjacentBlockState, side);
    }
}
