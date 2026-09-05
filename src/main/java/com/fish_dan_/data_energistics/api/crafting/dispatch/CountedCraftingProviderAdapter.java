package com.fish_dan_.data_energistics.api.crafting.dispatch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Optional server-thread adapter for one provider instance to admit counted Trinity dispatches.
 *
 * <p>
 * Preparation is read-only: it may inspect current provider state but must not consume inputs, reserve capacity or
 * mutate the supplied prototype. Target selection, retry windows and planning remain private to DataEnergistics.
 * </p>
 */
@FunctionalInterface
public interface CountedCraftingProviderAdapter {

    /**
     * Prepares one physical submission containing up to the requested number of identical logical crafts.
     *
     * <p>
     * {@code requestedCount} is always positive. A returned admission must report a count in the inclusive range
     * {@code 1..requestedCount}; violating this contract fails the current provider attempt. Returning {@code null}
     * means that the provider cannot currently accept any craft.
     * </p>
     *
     * @param patternDetails exact pattern selected by the crafting plan
     * @param prototype      read-only, exact per-craft input prototype for every pattern input slot
     * @param requestedCount positive maximum logical craft count available to this provider
     * @return one one-shot admission, or {@code null} when no craft can currently be accepted
     */
    @Nullable
    CountedCraftingAdmission prepareBatch(
                                          IPatternDetails patternDetails,
                                          KeyCounter[] prototype,
                                          long requestedCount);

    /**
     * Captures every currently usable dispatch target without reserving capacity, consuming inputs or advancing a
     * provider routing cursor.
     *
     * <p>
     * The default preserves existing adapter behavior as one aggregate provider route with unknown numeric bounds.
     * Implementations that expose exact routes must return a non-null immutable list of immutable observations. An
     * empty list means that the provider currently has no usable route.
     * </p>
     *
     * @param patternDetails exact pattern selected by the crafting plan
     * @param prototype      read-only exact per-craft input prototype; it must not be retained or mutated
     * @param requestedCount positive logical craft count still eligible for dispatch
     * @return immutable capacity observations, or an empty immutable list when no target is currently usable
     */
    default List<CountedCraftingCapacity> captureCapacity(
                                                          IPatternDetails patternDetails,
                                                          KeyCounter[] prototype,
                                                          long requestedCount) {
        if (requestedCount <= 0L) {
            throw new IllegalArgumentException("Requested counted crafting capacity must be positive");
        }
        return List.of(CountedCraftingCapacity.aggregateUnknown());
    }

    /**
     * Prepares one counted submission for the exact target selected from {@link #captureCapacity}.
     *
     * <p>
     * The default keeps source compatibility for aggregate adapters and rejects any target they did not publish.
     * Preparation is read-only until the returned one-shot admission is committed.
     * </p>
     *
     * @param patternDetails exact pattern selected by the crafting plan
     * @param prototype      read-only exact per-craft input prototype
     * @param requestedCount positive maximum logical craft count offered to this target
     * @param target         exact previously captured target
     * @return one one-shot admission, or {@code null} when the target is no longer available
     */
    @Nullable
    default CountedCraftingAdmission prepareBatchForTarget(
                                                           IPatternDetails patternDetails,
                                                           KeyCounter[] prototype,
                                                           long requestedCount,
                                                           CountedCraftingTarget target) {
        if (!target.providerScoped()) {
            return null;
        }
        return prepareBatch(patternDetails, prototype, requestedCount);
    }
}
