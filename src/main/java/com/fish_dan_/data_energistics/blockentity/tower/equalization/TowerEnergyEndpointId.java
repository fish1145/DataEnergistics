package com.fish_dan_.data_energistics.blockentity.tower.equalization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * Stable identity of one side-specific tower energy endpoint without retaining its mutable capability.
 *
 * @param dimensionId     dimension containing the endpoint
 * @param pos             immutable position of the endpoint owner
 * @param side            queried capability side, or {@code null} for unsided access
 * @param storageIdentity deterministic identity ordinal for distinct storages exposed at the same side
 */
public record TowerEnergyEndpointId(ResourceLocation dimensionId,
                                    BlockPos pos,
                                    @Nullable Direction side,
                                    int storageIdentity) {

    /**
     * Freezes mutable position subclasses before the identifier is used as a value or map key.
     *
     * @param pos  position of the endpoint owner
     * @param side queried capability side, or {@code null} for unsided access
     */
    public TowerEnergyEndpointId {
        pos = pos.immutable();
        if (storageIdentity < 0) {
            throw new IllegalArgumentException("Energy storage identity must be non-negative");
        }
    }

    /**
     * Creates a compatibility identifier for an Overworld endpoint with the first storage identity.
     *
     * @param pos  endpoint position
     * @param side queried side
     */
    public TowerEnergyEndpointId(BlockPos pos, @Nullable Direction side) {
        this(Level.OVERWORLD.location(), pos, side, 0);
    }
}
