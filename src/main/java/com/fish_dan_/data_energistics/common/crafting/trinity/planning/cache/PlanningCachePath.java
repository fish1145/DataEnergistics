package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

/**
 * Diagnoses which pure planning stage supplied a request without influencing dispatch governance.
 */
public enum PlanningCachePath {

    /** An identical quantity, inventory, limits, and revision request joined a calculation already in progress. */
    IN_FLIGHT_SHARED,

    /** Target structure matched, while quantity and inventory were instantiated for this request. */
    STRUCTURE_HIT,

    /** Target-reachable semantics required binding expansion and topology analysis. */
    MISS
}
