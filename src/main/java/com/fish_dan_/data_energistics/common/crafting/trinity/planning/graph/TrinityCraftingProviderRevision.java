package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

/**
 * Exposes a true monotonic mutation revision for AE2's grid-local crafting-provider index.
 *
 * <p>
 * AE2's public last-modified tick can repeat when several provider mutations happen in one tick. A runtime bridge
 * implements this contract so graph invalidation cannot miss those same-tick changes.
 * </p>
 */
public interface TrinityCraftingProviderRevision {

    /**
     * @return non-negative revision incremented exactly once for every provider-index mutation
     */
    long trinityCraftingProviderRevision();
}
