package com.fish_dan_.data_energistics.worldgen.meteorite;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MeteoriteBlockPutter {

    public boolean put(LevelAccessor level, BlockPos pos, BlockState blk) {
        BlockState original = level.getBlockState(pos);
        if (original.getBlock() != Blocks.BEDROCK && original != blk) {
            level.setBlock(pos, blk, 3);
            return true;
        }
        return false;
    }
}
