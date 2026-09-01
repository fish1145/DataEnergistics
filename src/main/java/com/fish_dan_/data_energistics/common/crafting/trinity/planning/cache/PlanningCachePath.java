package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

/**
 * Diagnoses which pure planning stage supplied a request without influencing dispatch governance.
 */
public enum PlanningCachePath {

    /** An identical quantity, inventory, limits, and revision request joined a calculation already in progress. */
    IN_FLIGHT_SHARED,

    /** A previously verified multi-producer identity route supplied the first exact incumbent. */
    ROUTE_HINT_HIT,

    /** Quantity-independent DAG producer proofs were reused across a target closure. */
    ROUTE_PROOF_HIT,

    /** A deterministic cycle unit order and restart seed were reused. */
    CYCLE_UNIT_HIT,

    /** More than one semantic route, cycle, or coefficient proof family was reused. */
    MIXED_PROOF_HIT,

    /** Target structure matched, while quantity and inventory were instantiated for this request. */
    STRUCTURE_HIT,

    /** Target-reachable semantics required binding expansion and topology analysis. */
    MISS
}
