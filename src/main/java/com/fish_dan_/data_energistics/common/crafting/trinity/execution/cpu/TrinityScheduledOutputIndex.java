package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import java.util.Set;

/**
 * Aggregates outputs that have not yet been dispatched by one Trinity CPU job.
 *
 * <p>
 * The index is derived from authoritative task progress and is deliberately excluded from persistence.
 * </p>
 */
interface TrinityScheduledOutputIndex {

    /**
     * Adds every output produced by a positive number of logical pattern executions.
     *
     * @param pattern    pattern whose outputs are being scheduled
     * @param craftCount positive logical execution count
     */
    void add(IPatternDetails pattern, long craftCount);

    /**
     * Removes outputs after a positive number of logical pattern executions has been dispatched.
     *
     * @param pattern    pattern whose outputs were dispatched
     * @param craftCount positive logical execution count
     */
    void remove(IPatternDetails pattern, long craftCount);

    /**
     * Returns the scheduled amount for one key, saturated to AE2's public long amount range.
     *
     * @param key output key
     * @return non-negative scheduled amount
     */
    long amount(AEKey key);

    /**
     * Adds the current scheduled snapshot to an AE2 counter without allowing positive overflow.
     *
     * @param output destination counter
     */
    void addTo(KeyCounter output);

    /**
     * Returns the immutable output-key snapshot owned by the current aggregate.
     *
     * @return immutable scheduled key set
     */
    Set<AEKey> keys();

    /** Clears all derived scheduled-output state. */
    void clear();
}
