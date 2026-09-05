package com.fish_dan_.data_energistics.accessor.patternprovider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTargetAvailability;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import org.jspecify.annotations.Nullable;

/**
 * Bridge for explicitly invoking AE2's ordinary external-inventory counted dispatch from approved subclasses.
 *
 * <p>
 * The base Mixin rejects unknown {@code PatternProviderLogic} subclasses because they may override routing semantics.
 * Adaptive provider logic uses this bridge only after proving that no addon-specific route is active.
 * </p>
 */
public interface PatternProviderBatchBridge {

    /**
     * Prepares AE2's standard external-inventory batch and preserves the caller's dispatch-success callback.
     *
     * @param patternDetails exact pattern selected by the crafting plan
     * @param prototype      one exact per-craft input prototype
     * @param requestedCount positive maximum logical craft count
     * @param afterCommit    callback invoked after AE2 has accepted and flushed the physical submission
     * @return fixed counted admission, or {@code null} when no ordinary target has capacity
     */
    @Nullable
    CountedCraftingAdmission dataEnergistics$prepareStandardBatch(
                                                                  IPatternDetails patternDetails,
                                                                  KeyCounter[] prototype,
                                                                  long requestedCount,
                                                                  Runnable afterCommit);

    /**
     * Prepares AE2's standard route with target filtering and explicit rejection facts.
     *
     * @param patternDetails     exact pattern selected by the crafting plan
     * @param prototype          one exact per-craft input prototype
     * @param requestedCount     positive maximum logical craft count
     * @param afterCommit        callback invoked after AE2 has accepted and flushed the physical submission
     * @param targetAvailability current-window target filter
     * @return accepted admission or explicit rejection facts
     */
    CountedCraftingPreparation dataEnergistics$prepareStandardBatch(
                                                                    IPatternDetails patternDetails,
                                                                    KeyCounter[] prototype,
                                                                    long requestedCount,
                                                                    Runnable afterCommit,
                                                                    CraftingDispatchTargetAvailability targetAvailability);
}
