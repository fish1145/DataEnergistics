package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

/**
 * Hard admission bounds for independent virtual-thread dispatch proposals.
 *
 * @param maxOutstanding     maximum accepted tickets across all grids
 * @param perGridOutstanding maximum accepted tickets for one process-local grid generation
 * @param shardCount         fixed logical provider-shard count
 */
public record DispatchProposalLimits(
                                     int maxOutstanding,
                                     int perGridOutstanding,
                                     int shardCount) {

    public static final int DEFAULT_MAX_OUTSTANDING = 1024;
    public static final int DEFAULT_PER_GRID_OUTSTANDING = 256;
    public static final int DEFAULT_SHARD_COUNT = 16;

    public DispatchProposalLimits {
        if (maxOutstanding <= 0) {
            throw new IllegalArgumentException("Dispatch proposal global outstanding limit must be positive");
        }
        if (perGridOutstanding <= 0) {
            throw new IllegalArgumentException("Dispatch proposal per-grid limit must be positive");
        }
        if (shardCount <= 0) {
            throw new IllegalArgumentException("Dispatch proposal shard count must be positive");
        }
    }

    /**
     * Creates the architecture defaults for virtual-thread proposal execution.
     *
     * @return immutable default hard limits
     */
    public static DispatchProposalLimits defaults() {
        return new DispatchProposalLimits(
                DEFAULT_MAX_OUTSTANDING,
                DEFAULT_PER_GRID_OUTSTANDING,
                DEFAULT_SHARD_COUNT);
    }
}
