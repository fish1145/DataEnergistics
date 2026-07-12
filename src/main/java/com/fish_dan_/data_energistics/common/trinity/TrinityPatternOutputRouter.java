package com.fish_dan_.data_energistics.common.trinity;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;

/**
 * Routes ordered Trinity crafting output batches without allowing CPU-reserved items to leak into general storage.
 */
public interface TrinityPatternOutputRouter {

    /**
     * Describes the externally visible effects committed during one routing pass.
     *
     * @param progressed whether any pending output was durably consumed
     * @param storageChanged whether main storage accepted at least one item
     */
    record RouteResult(boolean progressed, boolean storageChanged) {}

    /**
     * Supplies the amount that crafting CPUs are currently waiting for.
     */
    @FunctionalInterface
    interface RequestedAmount {

        /**
         * @param key crafted item key
         * @return current requested amount across the lease grid's crafting CPUs
         */
        long get(AEItemKey key);
    }

    /**
     * Inserts an item amount into either crafting CPUs or the host's main storage.
     */
    @FunctionalInterface
    interface OutputSink {

        /**
         * @param key    crafted item key
         * @param amount positive amount offered
         * @param mode   side-effect-free simulation or mutating insertion
         * @return accepted amount in the inclusive range from zero to {@code amount}
         */
        long insert(AEItemKey key, long amount, Actionable mode);
    }

    /** Mutable cursor over one route's authoritative ordered pending outputs. */
    interface PendingOutputCursor extends AutoCloseable {

        /**
         * Advances past the previously retained entry and selects the next pending entry.
         *
         * @return whether {@link #current()} now exposes an entry
         */
        boolean advance();

        /**
         * @return selected immutable entry; valid only after {@link #advance()} returned true
         */
        TrinityItemAmount current();

        /**
         * Atomically consumes an externally inserted amount from the selected entry and checkpoints the new state.
         * This method must durably mark its owner changed before it returns.
         *
         * @param amount positive amount no greater than the selected entry amount
         */
        void consumeCurrent(long amount);

        /** Releases the route's exclusive cursor state. */
        @Override
        void close();
    }

    /**
     * Routes each pending entry to waiting CPUs first and only offers its non-requested portion to main storage.
     * Pending order is significant: when an entry retains a CPU-requested amount, the router ends the pass without
     * advancing the cursor. The current entry's non-requested portion may still enter main storage before that barrier.
     * A remainder caused only by main-storage capacity does not block later entries.
     *
     * @param pending         exclusive cursor over authoritative route-owned outputs
     * @param requestedAmount lease-grid CPU request lookup
     * @param cpuSink         lease-grid crafting CPU insertion
     * @param storageSink     Trinity main storage insertion
     * @return committed routing effects, including whether main storage actually changed
     */
    RouteResult route(PendingOutputCursor pending,
                      RequestedAmount requestedAmount,
                      OutputSink cpuSink,
                      OutputSink storageSink);
}
