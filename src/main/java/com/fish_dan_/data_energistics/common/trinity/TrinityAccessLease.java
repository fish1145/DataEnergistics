package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the persistent access-hatch identity and ephemeral AE grid binding that exclusively expose a Trinity host.
 */
public interface TrinityAccessLease {

    /**
     * Creates a newly elected lease that is already bound to its hatch's current runtime grid.
     *
     * @param hatchPosition persistent world position of the elected hatch
     * @param grid          current AE grid reached through that hatch
     * @param epoch         monotonically increasing election generation
     * @return immutable elected lease
     */
    static TrinityAccessLease elect(BlockPos hatchPosition, IGrid grid, long epoch) {
        return new TrinityAccessLeaseImpl(hatchPosition, grid, epoch);
    }

    /**
     * Restores a persisted lease identity without guessing an AE grid instance.
     *
     * @param hatchPosition persisted world position of the elected hatch
     * @param epoch         persisted election generation
     * @return immutable lease awaiting a runtime grid binding
     */
    static TrinityAccessLease restore(BlockPos hatchPosition, long epoch) {
        return new TrinityAccessLeaseImpl(hatchPosition, null, epoch);
    }

    /** @return immutable position that identifies the elected hatch across host reloads */
    BlockPos hatchPosition();

    /** @return current runtime grid binding, or {@code null} while the elected hatch is offline */
    @Nullable
    IGrid grid();

    /** @return monotonically increasing generation assigned when this hatch was elected */
    long epoch();

    /**
     * @param position candidate hatch position
     * @return whether the candidate carries this lease's persistent identity
     */
    boolean identifies(BlockPos position);

    /**
     * Returns whether this lease belongs to the given hatch and its current runtime grid.
     *
     * @param position      candidate hatch position
     * @param candidateGrid candidate hatch's current grid
     * @return whether both the persistent hatch identity and runtime grid binding match
     */
    boolean matches(BlockPos position, @Nullable IGrid candidateGrid);

    /**
     * Rebinds the same persistent lease identity after its elected hatch returns online.
     *
     * @param grid current grid reached through the elected hatch
     * @return this lease when already bound, otherwise a new immutable binding
     */
    TrinityAccessLease bind(IGrid grid);

    /**
     * Drops only the non-persistent runtime grid reference while retaining hatch position and epoch.
     *
     * @return this lease when already unbound, otherwise a new immutable unbound lease
     */
    TrinityAccessLease unbind();
}
