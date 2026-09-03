package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

/**
 * Describes how strongly the planner proved a successfully executable plan.
 * <p>
 * Both values represent plans that already passed exact conservation, inventory, seed, scheduling, quantity and
 * boundary validation. The distinction only records whether the complete lexicographic optimum was also proved.
 */
public enum TrinityPlanQuality {

    /** Every configured lexicographic objective was proved globally optimal. */
    PROVED_OPTIMAL,

    /** The plan is exactly executable, but the complete optimality proof was intentionally stopped. */
    VERIFIED_FEASIBLE;

    /**
     * Combines independent plan regions without overstating the weakest proof carried by either region.
     */
    public TrinityPlanQuality combine(TrinityPlanQuality other) {
        return this == VERIFIED_FEASIBLE || other == VERIFIED_FEASIBLE ? VERIFIED_FEASIBLE : PROVED_OPTIMAL;
    }
}
