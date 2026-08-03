package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

/**
 * Converts immutable per-grid dispatch metrics into one deterministic physical budget.
 */
public interface CraftingDispatchGovernor {

    /**
     * @return independent per-grid Governor
     */
    static CraftingDispatchGovernor create(CraftingDispatchGovernorSettings settings) {
        return new CraftingDispatchGovernorImpl(settings);
    }

    /**
     * @return budget that must be captured before creating the next grid dispatch window
     */
    CraftingDispatchBudget budget();

    /**
     * Records one completed grid tick without mutating crafting resources.
     *
     * @param metrics immutable measured facts
     */
    void observe(CraftingDispatchMetrics metrics);

    /**
     * @return current read-only diagnostics
     */
    CraftingDispatchGovernorSnapshot snapshot();
}
