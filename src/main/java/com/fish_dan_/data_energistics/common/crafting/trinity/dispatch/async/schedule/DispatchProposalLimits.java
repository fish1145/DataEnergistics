package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

/**
 * Hard resource bounds for the independent dispatch-proposal executor.
 *
 * @param workerThreads      fixed background worker count
 * @param queueCapacity      global bounded executor queue capacity
 * @param perGridOutstanding maximum accepted tickets for one process-local grid generation
 * @param shardCount         fixed logical provider-shard count
 */
public record DispatchProposalLimits(
                                     int workerThreads,
                                     int queueCapacity,
                                     int perGridOutstanding,
                                     int shardCount) {

    public static final int DEFAULT_QUEUE_CAPACITY = 1024;
    public static final int DEFAULT_PER_GRID_OUTSTANDING = 256;
    public static final int DEFAULT_SHARD_COUNT = 16;

    public DispatchProposalLimits {
        if (workerThreads <= 0) {
            throw new IllegalArgumentException("Dispatch proposal worker count must be positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("Dispatch proposal queue capacity must be positive");
        }
        if (perGridOutstanding <= 0) {
            throw new IllegalArgumentException("Dispatch proposal per-grid limit must be positive");
        }
        if (shardCount <= 0) {
            throw new IllegalArgumentException("Dispatch proposal shard count must be positive");
        }
    }

    /**
     * Derives the architecture defaults from the current process CPU count.
     *
     * @return immutable default hard limits
     */
    public static DispatchProposalLimits defaults() {
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 4));
        return new DispatchProposalLimits(
                threads,
                DEFAULT_QUEUE_CAPACITY,
                DEFAULT_PER_GRID_OUTSTANDING,
                DEFAULT_SHARD_COUNT);
    }
}
