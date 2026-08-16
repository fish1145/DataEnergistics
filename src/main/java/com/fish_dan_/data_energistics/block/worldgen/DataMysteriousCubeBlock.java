package com.fish_dan_.data_energistics.block.worldgen;

import com.fish_dan_.data_energistics.world.meteorite.DataMeteoriteSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marks the reward at the center of a digitized meteorite without changing AE2's mysterious cube behavior.
 * Placing a collected block intentionally does not register a new meteorite compass target.
 */
public final class DataMysteriousCubeBlock extends Block {

    public DataMysteriousCubeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (newState.getBlock() == state.getBlock()) {
            return;
        }

        super.onRemove(state, level, pos, newState, isMoving);
        if (level instanceof ServerLevel serverLevel) {
            DataMeteoriteSavedData.get(serverLevel).remove(pos);
        }
    }
}
