package com.fish_dan_.data_energistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * One of four manually advanced resonance-crystal cluster stages.
 */
public class ResonanceCrystalClusterBlock extends AmethystClusterBlock {

    @Nullable
    private final Supplier<? extends Block> nextStage;

    public ResonanceCrystalClusterBlock(float height, float aabbOffset, BlockBehaviour.Properties properties,
                                        @Nullable Supplier<? extends Block> nextStage) {
        super(height, aabbOffset, properties);
        this.nextStage = nextStage;
    }

    /**
     * Advances this stage while preserving the vanilla facing and waterlogged properties.
     */
    public boolean advance(Level level, BlockPos pos, BlockState state) {
        if (!state.is(this)) {
            throw new IllegalArgumentException("Cannot advance a different resonance crystal block: " + state);
        }
        if (this.nextStage == null) {
            return false;
        }
        BlockState advancedState = this.nextStage.get().defaultBlockState()
                .setValue(FACING, state.getValue(FACING))
                .setValue(WATERLOGGED, state.getValue(WATERLOGGED));
        return level.setBlock(pos, advancedState, Block.UPDATE_ALL);
    }
}
