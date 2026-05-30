package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class DataCrystalBuddingBlock extends BuddingAmethystBlock {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int DECAY_CHANCE = 12;

    public DataCrystalBuddingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) {
            return;
        }

        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos targetPos = pos.relative(direction);
        BlockState targetState = level.getBlockState(targetPos);
        Block block = null;

        if (canClusterGrowAtState(targetState)) {
            block = ModBlocks.SMALL_DATA_CRYSTAL_BUD.get();
        } else if (targetState.is(ModBlocks.SMALL_DATA_CRYSTAL_BUD.get()) && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            block = ModBlocks.MEDIUM_DATA_CRYSTAL_BUD.get();
        } else if (targetState.is(ModBlocks.MEDIUM_DATA_CRYSTAL_BUD.get()) && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            block = ModBlocks.LARGE_DATA_CRYSTAL_BUD.get();
        } else if (targetState.is(ModBlocks.LARGE_DATA_CRYSTAL_BUD.get()) && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            block = ModBlocks.DATA_CRYSTAL_CLUSTER.get();
        }

        if (block == null) {
            return;
        }

        BlockState grownState = block.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, targetState.getFluidState().getType() == Fluids.WATER);
        level.setBlockAndUpdate(targetPos, grownState);
        this.tryDecay(level, pos, state, random);
    }

    private void tryDecay(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.is(ModBlocks.BUDDING_DATA_CRYSTAL_4.get()) || random.nextInt(DECAY_CHANCE) != 0) {
            return;
        }

        Block nextBlock;
        if (state.is(ModBlocks.BUDDING_DATA_CRYSTAL_3.get())) {
            nextBlock = ModBlocks.BUDDING_DATA_CRYSTAL_2.get();
        } else if (state.is(ModBlocks.BUDDING_DATA_CRYSTAL_2.get())) {
            nextBlock = ModBlocks.BUDDING_DATA_CRYSTAL_1.get();
        } else if (state.is(ModBlocks.BUDDING_DATA_CRYSTAL_1.get())) {
            nextBlock = ModBlocks.BUDDING_DATA_CRYSTAL_0.get();
        } else {
            throw new IllegalStateException("Unexpected data crystal motherrock: " + state);
        }

        level.setBlockAndUpdate(pos, nextBlock.defaultBlockState());
    }
}
