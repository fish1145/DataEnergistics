package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

/**
 * Diagnoses which pure planning stage supplied a request without influencing dispatch governance.
 */
public enum PlanningCachePath {

    /** Quantity, mode, relevant inventory, structure, and revision matched a completed solved result. */
    EXACT_HIT,

    /** Target structure matched, while quantity, inventory, or revision required dynamic demand solving. */
    STRUCTURE_HIT,

    /** Target-reachable semantics required binding expansion and topology analysis. */
    MISS
}
