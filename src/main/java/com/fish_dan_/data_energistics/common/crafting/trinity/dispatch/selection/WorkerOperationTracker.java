package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection;

import java.util.Arrays;

/**
 * Tracks the rolling physical-operation history for one Trinity crafting worker.
 *
 * <p>
 * A separate instance is attached to every worker so load-aware CPU selection observes that worker's own recent
 * submissions. Dispatch authorization remains exclusively owned by the configured grid, provider and server-time
 * windows.
 */
public final class WorkerOperationTracker {

    /**
     * Creates an empty three-tick operation history for one worker.
     *
     * @return independent worker operation tracker
     */
    public static WorkerOperationTracker create() {
        return new WorkerOperationTracker();
    }

    private static final int WINDOW_TICKS = 3;

    /**
     * Physical submissions started in the current server tick and its two predecessors.
     */
    private final long[] recentOperations = new long[WINDOW_TICKS];

    /**
     * Tick represented by index zero, or {@link Long#MIN_VALUE} before the first observation.
     */
    private long currentTick = Long.MIN_VALUE;

    /**
     * Returns the physical submissions retained in this worker's time-aware rolling window for load-aware CPU
     * selection.
     *
     * @param currentTick current monotonic server tick
     * @return recent physical-operation load
     */
    public long recentOperations(long currentTick) {
        advanceTo(currentTick);
        return sumRecentOperations();
    }

    /**
     * Adds the worker's completed physical submissions for the current server tick. Multiple execution slices in the
     * same tick accumulate into one window slot.
     *
     * @param currentTick       current monotonic server tick
     * @param startedOperations physical pattern submissions started by this worker
     */
    public void recordTickUsage(long currentTick, int startedOperations) {
        if (startedOperations < 0) {
            throw new IllegalArgumentException("startedOperations must not be negative");
        }
        advanceTo(currentTick);
        this.recentOperations[0] = Math.addExact(this.recentOperations[0], startedOperations);
    }

    /**
     * Advances the window over skipped server ticks so paused, offline and idle workers shed stale load.
     */
    private void advanceTo(long requestedTick) {
        if (requestedTick < 0L) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
        if (this.currentTick == Long.MIN_VALUE) {
            this.currentTick = requestedTick;
            return;
        }
        if (requestedTick < this.currentTick) {
            throw new IllegalArgumentException(
                    "currentTick must not move backwards from " + this.currentTick + " to " + requestedTick);
        }

        long elapsedTicks = requestedTick - this.currentTick;
        if (elapsedTicks == 0L) {
            return;
        }
        if (elapsedTicks >= WINDOW_TICKS) {
            Arrays.fill(this.recentOperations, 0L);
        } else {
            for (int shift = 0; shift < (int) elapsedTicks; shift++) {
                for (int index = this.recentOperations.length - 1; index > 0; index--) {
                    this.recentOperations[index] = this.recentOperations[index - 1];
                }
                this.recentOperations[0] = 0;
            }
        }
        this.currentTick = requestedTick;
    }

    /**
     * Sums the fixed-size window without exposing its mutable slots.
     */
    private long sumRecentOperations() {
        long recentlyUsed = 0L;
        for (long used : this.recentOperations) {
            recentlyUsed = Math.addExact(recentlyUsed, used);
        }
        return recentlyUsed;
    }
}
