package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies the single Trinity access hatch and AE grid currently allowed to expose a host.
 */
public record TrinityAccessLease(BlockPos hatchPosition, IGrid grid, long epoch) {

    public TrinityAccessLease {
        hatchPosition = hatchPosition.immutable();
        if (epoch < 0L) {
            throw new IllegalArgumentException("Trinity access lease epoch must not be negative");
        }
    }

    /**
     * Returns whether this lease belongs to the given hatch and its current grid instance.
     */
    public boolean matches(BlockPos position, @Nullable IGrid candidateGrid) {
        return this.hatchPosition.equals(position) && this.grid == candidateGrid;
    }
}
