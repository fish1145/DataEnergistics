package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProvider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import org.jspecify.annotations.Nullable;

/**
 * Counted provider contract whose preparation is bound to an explicit target from a capacity snapshot.
 *
 * <p>
 * This compile-time boundary prevents the dispatcher from guessing provider internals. Implementations must retain
 * the existing one-shot ownership semantics of {@link CountedCraftingAdmission}; capacity capture remains read-only.
 * </p>
 */
public interface TargetedCountedCraftingProvider extends CountedCraftingProvider, ProviderCapacityView {

    /**
     * Prepares one counted submission for the exact current provider target.
     *
     * @param patternDetails exact pattern selected by the crafting plan
     * @param prototype      one exact per-craft input prototype
     * @param requestedCount positive maximum logical craft count
     * @param target         provider-local route selected from the current capacity snapshot
     * @return one target-bound admission, or {@code null} when that target cannot accept work
     */
    @Nullable
    CountedCraftingAdmission prepareBatchForTarget(
                                                   IPatternDetails patternDetails,
                                                   KeyCounter[] prototype,
                                                   long requestedCount,
                                                   CraftingDispatchTarget target);
}
