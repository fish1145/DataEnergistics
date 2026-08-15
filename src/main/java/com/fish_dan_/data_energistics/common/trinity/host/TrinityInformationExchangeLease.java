package com.fish_dan_.data_energistics.common.trinity.host;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import org.jspecify.annotations.Nullable;

/**
 * Defines the persistent information-exchange-depot identity and ephemeral AE grid binding that exclusively expose a
 * Trinity host.
 */
public final class TrinityInformationExchangeLease {

    /** Persistent world position used to recover the elected hatch after host reconstruction. */
    private final BlockPos hatchPosition;
    /** Ephemeral AE grid reference resolved only while the elected hatch is online. */
    @Nullable
    private final IGrid grid;
    /** Persisted election generation used to retain monotonic lease history. */
    private final long epoch;

    /**
     * Captures one lease generation.
     *
     * @param depotPosition persistent elected depot position
     * @param grid          ephemeral runtime grid, or {@code null} after deserialization or unload
     * @param epoch         non-negative election generation
     */
    private TrinityInformationExchangeLease(BlockPos depotPosition, @Nullable IGrid grid, long epoch) {
        if (epoch < 0L) {
            throw new IllegalArgumentException("Trinity access lease epoch must not be negative");
        }
        this.hatchPosition = depotPosition.immutable();
        this.grid = grid;
        this.epoch = epoch;
    }

    /**
     * Creates a newly elected lease that is already bound to its hatch's current runtime grid.
     *
     * @param depotPosition persistent world position of the elected depot
     * @param grid          current AE grid reached through that hatch
     * @param epoch         monotonically increasing election generation
     * @return immutable elected lease
     */
    public static TrinityInformationExchangeLease elect(BlockPos depotPosition, IGrid grid, long epoch) {
        return new TrinityInformationExchangeLease(depotPosition, grid, epoch);
    }

    /**
     * Restores a persisted lease identity without guessing an AE grid instance.
     *
     * @param depotPosition persisted world position of the elected depot
     * @param epoch         persisted election generation
     * @return immutable lease awaiting a runtime grid binding
     */
    public static TrinityInformationExchangeLease restore(BlockPos depotPosition, long epoch) {
        return new TrinityInformationExchangeLease(depotPosition, null, epoch);
    }

    /** @return immutable position that identifies the elected hatch across host reloads */
    public BlockPos hatchPosition() {
        return this.hatchPosition;
    }

    /** @return current runtime grid binding, or {@code null} while the elected hatch is offline */
    @Nullable
    public IGrid grid() {
        return this.grid;
    }

    /** @return monotonically increasing generation assigned when this hatch was elected */
    public long epoch() {
        return this.epoch;
    }

    /**
     * @param position candidate hatch position
     * @return whether the candidate carries this lease's persistent identity
     */
    public boolean identifies(BlockPos position) {
        return this.hatchPosition.equals(position);
    }

    /**
     * Returns whether this lease belongs to the given hatch and its current runtime grid.
     *
     * @param position      candidate hatch position
     * @param candidateGrid candidate hatch's current grid
     * @return whether both the persistent hatch identity and runtime grid binding match
     */
    public boolean matches(BlockPos position, @Nullable IGrid candidateGrid) {
        return this.grid != null && this.hatchPosition.equals(position) && this.grid == candidateGrid;
    }

    /**
     * Rebinds the same persistent lease identity after its elected hatch returns online.
     *
     * @param grid current grid reached through the elected hatch
     * @return this lease when already bound, otherwise a new immutable binding
     */
    public TrinityInformationExchangeLease bind(IGrid grid) {
        return this.grid == grid ? this : new TrinityInformationExchangeLease(this.hatchPosition, grid, this.epoch);
    }

    /**
     * Drops only the non-persistent runtime grid reference while retaining hatch position and epoch.
     *
     * @return this lease when already unbound, otherwise a new immutable unbound lease
     */
    public TrinityInformationExchangeLease unbind() {
        return this.grid == null ? this : new TrinityInformationExchangeLease(this.hatchPosition, null, this.epoch);
    }
}
