package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

/**
 * Defines how a requested amount relates to pre-existing target items used to seed a cyclic plan.
 */
public enum CraftingQuantityMode {

    /**
     * The request is a net-new amount; all seed material and excess output are returned separately.
     */
    NET_NEW,

    /**
     * The request is the final delivered total and still executes at least one complete production cycle.
     */
    FINAL_TOTAL
}
