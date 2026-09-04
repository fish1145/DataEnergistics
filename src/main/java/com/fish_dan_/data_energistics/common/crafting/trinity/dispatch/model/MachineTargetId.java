package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Names one physical machine independently from the provider route used to reach it.
 *
 * <p>
 * Adapters build this identity from stable physical facts such as dimension, position, face, or a documented addon
 * connection ID. Two providers reaching the same machine must produce equal IDs so later reservation logic cannot
 * oversell it.
 * </p>
 *
 * @param stableIdentity provider-independent physical machine identity
 */
public record MachineTargetId(String stableIdentity) {

    /**
     * Builds the provider-independent identity of one exact external block face.
     *
     * @param dimension dimension containing the target
     * @param position  target block position
     * @param side      target face receiving the pattern input
     * @return stable physical target identity
     */
    public static MachineTargetId forBlockTarget(ResourceKey<Level> dimension, BlockPos position, Direction side) {
        if (dimension == null || position == null || side == null) {
            throw new IllegalArgumentException("Block machine target context must not be null");
        }
        return new MachineTargetId(
                "block|" + dimension.location() + "|" + position.asLong() + "|" + side.getName());
    }

    /**
     * Builds the provider-independent identity of one complete external block entity, shared by every input face.
     *
     * @param dimension dimension containing the target
     * @param position  target block position
     * @return stable physical block-entity identity
     */
    public static MachineTargetId forBlockEntity(ResourceKey<Level> dimension, BlockPos position) {
        return new MachineTargetId("block|" + dimension.location() + "|" + position.asLong());
    }

    public MachineTargetId {
        if (stableIdentity == null || stableIdentity.isBlank()) {
            throw new IllegalArgumentException("Machine target identity must not be blank");
        }
    }
}
