package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import org.jetbrains.annotations.Nullable;

/** Immutable default implementation that keeps serializable lease identity separate from its runtime grid binding. */
final class TrinityAccessLeaseImpl implements TrinityAccessLease {

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
     * @param hatchPosition persistent elected hatch position
     * @param grid          ephemeral runtime grid, or {@code null} after deserialization or unload
     * @param epoch         non-negative election generation
     */
    TrinityAccessLeaseImpl(BlockPos hatchPosition, @Nullable IGrid grid, long epoch) {
        if (epoch < 0L) {
            throw new IllegalArgumentException("Trinity access lease epoch must not be negative");
        }
        this.hatchPosition = hatchPosition.immutable();
        this.grid = grid;
        this.epoch = epoch;
    }

    @Override
    public BlockPos hatchPosition() {
        return this.hatchPosition;
    }

    @Nullable
    @Override
    public IGrid grid() {
        return this.grid;
    }

    @Override
    public long epoch() {
        return this.epoch;
    }

    @Override
    public boolean identifies(BlockPos position) {
        return this.hatchPosition.equals(position);
    }

    @Override
    public boolean matches(BlockPos position, @Nullable IGrid candidateGrid) {
        return this.grid != null && this.hatchPosition.equals(position) && this.grid == candidateGrid;
    }

    @Override
    public TrinityAccessLease bind(IGrid grid) {
        return this.grid == grid ? this : new TrinityAccessLeaseImpl(this.hatchPosition, grid, this.epoch);
    }

    @Override
    public TrinityAccessLease unbind() {
        return this.grid == null ? this : new TrinityAccessLeaseImpl(this.hatchPosition, null, this.epoch);
    }
}
