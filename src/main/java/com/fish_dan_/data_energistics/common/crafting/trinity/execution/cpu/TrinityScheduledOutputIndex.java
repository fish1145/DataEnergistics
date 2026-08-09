package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.util.LongAmountMath;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates outputs that have not yet been dispatched by one Trinity CPU job.
 *
 * <p>
 * The index is derived from authoritative task progress and is deliberately excluded from persistence.
 * </p>
 *
 */
final class TrinityScheduledOutputIndex {

    /**
     * Largest amount exposed by AE2's long-based counters.
     */
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    /**
     * Exact derived totals retained so a later decrement remains correct after public saturation.
     */
    private final Map<AEKey, BigInteger> amounts = new HashMap<>();
    /**
     * Immutable key view replaced only when membership changes.
     */
    private Set<AEKey> keys = Set.of();

    /**
     * Adds every output produced by a positive number of logical pattern executions.
     *
     * @param pattern    pattern whose outputs are being scheduled
     * @param craftCount positive logical execution count
     */
    public void add(IPatternDetails pattern, long craftCount) {
        Map<AEKey, BigInteger> additions = contributions(pattern, craftCount);
        boolean keySetChanged = false;
        for (Map.Entry<AEKey, BigInteger> addition : additions.entrySet()) {
            keySetChanged |= !this.amounts.containsKey(addition.getKey());
            this.amounts.merge(addition.getKey(), addition.getValue(), BigInteger::add);
        }
        if (keySetChanged) {
            rebuildKeys();
        }
    }

    /**
     * Removes outputs after a positive number of logical pattern executions has been dispatched.
     *
     * @param pattern    pattern whose outputs were dispatched
     * @param craftCount positive logical execution count
     */
    public void remove(IPatternDetails pattern, long craftCount) {
        Map<AEKey, BigInteger> removals = contributions(pattern, craftCount);
        for (Map.Entry<AEKey, BigInteger> removal : removals.entrySet()) {
            BigInteger current = this.amounts.get(removal.getKey());
            if (current == null || current.compareTo(removal.getValue()) < 0) {
                throw new IllegalStateException(
                        "Scheduled Trinity output underflow for " + removal.getKey() + ": removing " +
                                removal.getValue() + " from " + (current == null ? BigInteger.ZERO : current));
            }
        }

        boolean keySetChanged = false;
        for (Map.Entry<AEKey, BigInteger> removal : removals.entrySet()) {
            BigInteger remaining = this.amounts.get(removal.getKey()).subtract(removal.getValue());
            if (remaining.signum() == 0) {
                this.amounts.remove(removal.getKey());
                keySetChanged = true;
            } else {
                this.amounts.put(removal.getKey(), remaining);
            }
        }
        if (keySetChanged) {
            rebuildKeys();
        }
    }

    /**
     * Returns the scheduled amount for one key, saturated to AE2's public long amount range.
     *
     * @param key output key
     * @return non-negative scheduled amount
     */
    public long amount(AEKey key) {
        BigInteger amount = this.amounts.get(key);
        if (amount == null) {
            return 0L;
        }
        return amount.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : amount.longValueExact();
    }

    /**
     * Adds the current scheduled snapshot to an AE2 counter without allowing positive overflow.
     *
     * @param output destination counter
     */
    public void addTo(KeyCounter output) {
        for (AEKey key : this.keys) {
            long existing = output.get(key);
            if (existing < 0L) {
                throw new IllegalArgumentException("Destination counter contains a negative amount for " + key);
            }
            output.set(key, LongAmountMath.saturatingAddNonNegative(existing, amount(key)));
        }
    }

    /**
     * Returns the immutable output-key snapshot owned by the current aggregate.
     *
     * @return immutable scheduled key set
     */
    public Set<AEKey> keys() {
        return this.keys;
    }

    /**
     * Clears all derived scheduled-output state.
     */
    public void clear() {
        if (this.amounts.isEmpty()) {
            return;
        }
        this.amounts.clear();
        this.keys = Set.of();
    }

    /**
     * Builds one atomic exact delta before mutating the authoritative index.
     */
    private static Map<AEKey, BigInteger> contributions(IPatternDetails pattern, long craftCount) {
        if (craftCount <= 0L) {
            throw new IllegalArgumentException("Scheduled Trinity craft count must be positive: " + craftCount);
        }
        BigInteger count = BigInteger.valueOf(craftCount);
        Map<AEKey, BigInteger> contributions = new HashMap<>();
        for (GenericStack output : pattern.getOutputs()) {
            if (output.amount() <= 0L) {
                throw new IllegalArgumentException(
                        "Scheduled Trinity pattern output must be positive for " + output.what());
            }
            BigInteger amount = BigInteger.valueOf(output.amount()).multiply(count);
            contributions.merge(output.what(), amount, BigInteger::add);
        }
        return contributions;
    }

    /**
     * Replaces the immutable key snapshot after a key enters or leaves the aggregate.
     */
    private void rebuildKeys() {
        this.keys = this.amounts.isEmpty() ? Set.of() : Set.copyOf(this.amounts.keySet());
    }
}
