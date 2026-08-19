package com.fish_dan_.data_energistics.common.resonance;

import com.fish_dan_.data_energistics.block.ResonanceCrystalClusterBlock;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Applies the crystal-stage changes caused by Warden and ordinary vibration waves.
 */
final class ResonanceCrystalWaveTransformation {

    private static final float AMETHYST_RESONANCE_CHANCE = 0.25F;
    private static final float DATA_RESONANCE_CHANCE = 0.50F;

    private ResonanceCrystalWaveTransformation() {}

    static boolean isWardenChangeable(BlockState state) {
        return state.is(Blocks.AMETHYST_CLUSTER) ||
                state.is(DEBlocks.DATA_CRYSTAL_CLUSTER.get()) ||
                state.is(DEBlocks.SMALL_RESONANCE_CRYSTAL_BUD.get()) ||
                state.is(DEBlocks.MEDIUM_RESONANCE_CRYSTAL_BUD.get()) ||
                state.is(DEBlocks.LARGE_RESONANCE_CRYSTAL_BUD.get());
    }

    static boolean transformFromWarden(Level level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.AMETHYST_CLUSTER) || state.is(DEBlocks.DATA_CRYSTAL_CLUSTER.get())) {
            return replace(level, pos, state, DEBlocks.MEDIUM_RESONANCE_CRYSTAL_BUD.get());
        }
        if (state.getBlock() instanceof ResonanceCrystalClusterBlock resonanceCrystal) {
            return resonanceCrystal.advance(level, pos, state);
        }
        throw new IllegalArgumentException("Warden wave cannot transform non-candidate crystal state: " + state);
    }

    static boolean tryTransformFromVibration(Level level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.is(Blocks.AMETHYST_CLUSTER)) {
            return random.nextFloat() < AMETHYST_RESONANCE_CHANCE &&
                    replace(level, pos, state, DEBlocks.SMALL_RESONANCE_CRYSTAL_BUD.get());
        }
        if (state.is(DEBlocks.DATA_CRYSTAL_CLUSTER.get())) {
            return random.nextFloat() < DATA_RESONANCE_CHANCE &&
                    replace(level, pos, state, DEBlocks.SMALL_RESONANCE_CRYSTAL_BUD.get());
        }
        if (state.getBlock() instanceof ResonanceCrystalClusterBlock resonanceCrystal) {
            return resonanceCrystal.advance(level, pos, state);
        }
        return false;
    }

    private static boolean replace(Level level, BlockPos pos, BlockState state, Block replacement) {
        if (!(state.getBlock() instanceof AmethystClusterBlock)) {
            throw new IllegalArgumentException("Cannot preserve crystal properties from incompatible state: " + state);
        }
        BlockState replacementState = replacement.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, state.getValue(AmethystClusterBlock.FACING))
                .setValue(AmethystClusterBlock.WATERLOGGED, state.getValue(AmethystClusterBlock.WATERLOGGED));
        return level.setBlock(pos, replacementState, Block.UPDATE_ALL);
    }
}
