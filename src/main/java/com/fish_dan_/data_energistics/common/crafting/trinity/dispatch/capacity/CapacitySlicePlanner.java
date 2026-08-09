package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import java.math.BigInteger;
import java.util.List;

/**
 * Pure planner that divides logical work into a bounded number of fair physical target calls.
 *
 * <p>
 * The planner never reads providers or world state. Input order and cursor define stable round-robin order;
 * planning cost depends on target count, not requested logical quantity.
 * </p>
 */
public interface CapacitySlicePlanner {

    /**
     * Creates the default startup-first max-min planner.
     *
     * @return independent stateless planner
     */
    static CapacitySlicePlanner create() {
        return new StartupFirstMaxMinCapacitySlicePlanner();
    }

    /**
     * Plans at most one physical call per selected snapshot.
     *
     * <p>
     * Known zero capacity is skipped. Unknown capacity, unknown single-batch capacity, and routing modes other than
     * {@code TARGETED} are limited to one logical craft so snapshot uncertainty never invents counted semantics.
     * </p>
     *
     * @param snapshots         immutable provider target observations in stable provider order
     * @param remainingCrafts   non-negative logical work still requested
     * @param physicalCallLimit non-negative maximum number of returned slices
     * @param cursor            non-negative round-robin cursor into the original snapshot order
     * @return immutable slices and the next cursor
     */
    CapacitySlicePlan plan(
                           List<ProviderCapacitySnapshot> snapshots,
                           BigInteger remainingCrafts,
                           int physicalCallLimit,
                           int cursor);
}
