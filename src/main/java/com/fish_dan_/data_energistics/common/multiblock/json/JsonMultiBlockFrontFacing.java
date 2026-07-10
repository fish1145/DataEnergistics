package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * Resolves structure-facing directions for JSON multiblocks imported from WorldEdit schematics.
 */
public final class JsonMultiBlockFrontFacing {

    private JsonMultiBlockFrontFacing() {}

    /**
     * Returns the initial horizontal scan direction from the placed host state.
     */
    public static Direction fromPlacedHost(BlockState state,
                                           DirectionProperty facingProperty,
                                           BlockPos pos,
                                           String hostName) {
        if (!state.hasProperty(facingProperty)) {
            throw new IllegalStateException(hostName + " is missing facing property at " + pos);
        }
        return state.getValue(facingProperty);
    }
}
