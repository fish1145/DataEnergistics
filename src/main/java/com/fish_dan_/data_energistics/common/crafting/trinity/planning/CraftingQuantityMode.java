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
    FINAL_TOTAL;

    /**
     * Determines whether AE2's equals-prefix adjustment may subtract target items already stored in the network.
     *
     * <p>
     * {@link #FINAL_TOTAL} owns that interpretation in the planning layer and therefore must receive the amount
     * entered by the player unchanged.
     * </p>
     *
     * @param requested whether the amount field used AE2's equals prefix
     * @return whether the amount menu should apply AE2's existing-stock subtraction
     */
    public boolean appliesAe2MissingAmountAdjustment(boolean requested) {
        return this == NET_NEW && requested;
    }

    /**
     * @return the other player-selectable quantity interpretation
     */
    public CraftingQuantityMode next() {
        return this == NET_NEW ? FINAL_TOTAL : NET_NEW;
    }

    /**
     * Resolves a synchronized menu ordinal and rejects corrupted client actions.
     *
     * @param ordinal synchronized enum ordinal
     * @return exact quantity mode
     */
    public static CraftingQuantityMode fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Unknown Trinity crafting quantity mode ordinal " + ordinal);
        }
        return values()[ordinal];
    }
}
