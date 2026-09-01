package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

/**
 * Selects whether a planning pass must prove the complete lexicographic optimum or may stop at the first exactly
 * verified executable solution.
 */
public enum TrinityPlanningMode {

    /** Continue every objective and search branch until optimality is proved or the optimisation budget expires. */
    OPTIMAL,

    /** Skip optional objective refinement after obtaining the first executable solution. */
    FIRST_FEASIBLE
}
