package com.fish_dan_.data_energistics.common.resonance;

import com.fish_dan_.data_energistics.blockentity.TuningForkBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Resolves a tuning-fork block on an already-loaded path chunk and applies one wave hit.
 */
final class TuningForkWaveHit {

    private TuningForkWaveHit() {}

    static boolean process(LevelChunk chunk, BlockPos pos, RandomSource random, boolean wardenWave) {
        BlockEntity blockEntity = chunk.getBlockEntity(pos);
        if (!(blockEntity instanceof TuningForkBlockEntity tuningFork)) {
            throw new IllegalStateException("Tuning fork at " + pos + " has no matching block entity");
        }
        return tuningFork.processWave(random, wardenWave);
    }
}
