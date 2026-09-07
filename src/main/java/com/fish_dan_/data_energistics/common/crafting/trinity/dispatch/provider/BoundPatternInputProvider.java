package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTargetAvailability;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import org.jspecify.annotations.Nullable;

/**
 * Internal provider capability for separating registered pattern identity from authorized CPU-side input bindings.
 *
 * <p>
 * Implementations must use {@code patternDetails} for publication, routing and machine validation. The extraction
 * pattern may only control input emission after an ordinary external-inventory route has accepted the registered
 * pattern.
 * </p>
 *
 * <p>
 * All calls run on the server menu/dispatch thread. Preparation must not consume the read-only prototype;
 * ownership passes only through a committed one-shot admission. This is not a third-party plugin API.
 * </p>
 */
public interface BoundPatternInputProvider {

    /** Prepares a positive craft count, returning rejection when no currently permitted route accepts the pattern. */
    CountedCraftingPreparation prepareBoundInputBatch(
                                                      IPatternDetails patternDetails,
                                                      IPatternDetails extractionDetails,
                                                      KeyCounter[] prototype,
                                                      long requestedCount,
                                                      CraftingDispatchTargetAvailability targetAvailability);

    /** Prepares the exact provider-local target; returns null if the captured target is no longer available. */
    @Nullable
    CountedCraftingAdmission prepareBoundInputBatchForTarget(
                                                             IPatternDetails patternDetails,
                                                             IPatternDetails extractionDetails,
                                                             KeyCounter[] prototype,
                                                             long requestedCount,
                                                             CraftingDispatchTarget target);
}
