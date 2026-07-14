package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

/**
 * Opt-in crafting provider contract for combining identical logical crafts into one physical submission.
 *
 * <p>
 * Preparation may inspect target capacity and fixes the target used by the returned admission. It must not consume the
 * prototype. Providers that cannot accept any craft return {@code null}; providers without counted support continue to
 * use the single-craft {@link ICraftingProvider#pushPattern} contract.
 * </p>
 */
public interface CountedCraftingProvider extends ICraftingProvider {

    /**
     * Prepares one physical submission containing up to the requested number of identical logical crafts.
     *
     * <p>
     * {@code requestedCount} must be positive. A returned admission must report a count in the inclusive range
     * {@code 1..requestedCount}. The caller validates this invariant and fails fast if a provider violates it.
     * </p>
     *
     * @param patternDetails exact pattern selected by the crafting plan
     * @param prototype      one exact per-craft input prototype for every pattern input slot
     * @param requestedCount positive maximum logical craft count available to this provider
     * @return one one-shot admission, or {@code null} when the prepared target cannot accept any craft
     */
    @Nullable
    CountedCraftingAdmission prepareBatch(
                                          IPatternDetails patternDetails,
                                          KeyCounter[] prototype,
                                          long requestedCount);
}
