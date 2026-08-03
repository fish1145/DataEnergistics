package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

/**
 * Immutable recent physical-operation distribution used only by dispatch diagnostics.
 *
 * @param workerCount       retained workers represented by the sample
 * @param totalOperations   recent physical operations across those workers
 * @param busiestOperations recent physical operations assigned to the busiest worker
 */
public record TrinityWorkerDispatchActivity(
        int workerCount,
        long totalOperations,
        long busiestOperations) {

    public static final TrinityWorkerDispatchActivity EMPTY = new TrinityWorkerDispatchActivity(0, 0L, 0L);

    public TrinityWorkerDispatchActivity {
        if (workerCount < 0 || busiestOperations < 0L || totalOperations < busiestOperations) {
            throw new IllegalArgumentException("Trinity worker dispatch activity is out of range");
        }
    }

    /**
     * @return combined immutable activity without losing the global busiest worker
     */
    public TrinityWorkerDispatchActivity combine(TrinityWorkerDispatchActivity other) {
        if (other == null) {
            throw new IllegalArgumentException("Trinity worker dispatch activity is required");
        }
        return new TrinityWorkerDispatchActivity(
                Math.addExact(this.workerCount, other.workerCount),
                Math.addExact(this.totalOperations, other.totalOperations),
                Math.max(this.busiestOperations, other.busiestOperations));
    }

    /**
     * @return fraction of recent physical operations assigned to the busiest worker
     */
    public double busiestShare() {
        return this.totalOperations == 0L ?
                0.0D :
                (double) this.busiestOperations / (double) this.totalOperations;
    }
}
