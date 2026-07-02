package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.Objects;

/**
 * Resolves structure-facing directions for JSON multiblocks imported from WorldEdit schematics.
 */
public final class JsonMultiBlockFrontFacing {

    private JsonMultiBlockFrontFacing() {}

    /**
     * Returns the WorldEdit player-facing direction represented by a placed host block.
     * <p>
     * Minecraft horizontal machine blocks usually face back toward the player after placement, while
     * WorldEdit records the player's look direction as the structure front.
     */
    public static Direction fromPlacedHost(BlockState state,
                                           DirectionProperty facingProperty,
                                           BlockPos pos,
                                           String hostName) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(facingProperty, "facingProperty");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(hostName, "hostName");
        if (!state.hasProperty(facingProperty)) {
            throw new IllegalStateException(hostName + " is missing facing property at " + pos);
        }
        return state.getValue(facingProperty).getOpposite();
    }

    /**
     * Returns the stored horizontal block facing that represents a given WorldEdit structure front.
     */
    public static Direction toPlacedHostFacing(Direction structureFront) {
        Objects.requireNonNull(structureFront, "structureFront");
        if (structureFront.getAxis() == Direction.Axis.Y) {
            throw new IllegalArgumentException("Structure front must be horizontal: " + structureFront);
        }
        return structureFront.getOpposite();
    }
}
