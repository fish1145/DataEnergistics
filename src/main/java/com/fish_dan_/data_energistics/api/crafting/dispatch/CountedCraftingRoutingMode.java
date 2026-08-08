package com.fish_dan_.data_energistics.api.crafting.dispatch;

/**
 * Declares how a counted crafting adapter selects the physical target represented by one capacity observation.
 */
public enum CountedCraftingRoutingMode {

    /**
     * The dispatcher selects and later addresses the exact target reported by the adapter.
     */
    TARGETED,

    /**
     * The adapter retains target selection while preserving its own stable routing order.
     */
    ORDERED,

    /**
     * The provider accepts a counted logical batch through one aggregate physical submission.
     */
    AGGREGATE
}
