package com.fish_dan_.data_energistics.worldgen.meteorite.fallout;

import com.fish_dan_.data_energistics.worldgen.meteorite.MeteoriteBlockPutter;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class FalloutSand extends FalloutCopy {

    private final MeteoriteBlockPutter putter;

    public FalloutSand(LevelAccessor level, BlockPos pos, MeteoriteBlockPutter putter, BlockState skyStone, RandomSource random) {
        super(level, pos, putter, skyStone, random);
        this.putter = putter;
    }

    @Override
    public int adjustCrater() {
        return 2;
    }

    @Override
    public void getOther(LevelAccessor level, BlockPos pos, float a) {
        if (a > 0.66F) {
            this.putter.put(level, pos, Blocks.GLASS.defaultBlockState());
        }
    }
}
