package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

/**
 * Separates pure Trinity computations while retaining one shared per-Grid LRU capacity.
 */
public enum TrinityComputationNamespace {

    /** Target-reachable graph derived from one publication revision. */
    REACHABLE_GRAPH(true),

    /** Binding and topology structure keyed entirely by pattern semantics. */
    COMPILED_GRAPH(false),

    /** Quantity and inventory-sensitive solved planning result. */
    SOLVED_PLAN(true),

    /** Provider capacity snapshot captured for one publication and capacity epoch. */
    CAPACITY_CAPTURE(true),

    /** Pure capacity allocation derived from a captured snapshot. */
    CAPACITY_SLICE(true),

    /** Immutable proposal candidate ordering derived from a capacity slice. */
    PROPOSAL_CANDIDATE(true);

    private final boolean revisionBound;

    TrinityComputationNamespace(boolean revisionBound) {
        this.revisionBound = revisionBound;
    }

    /**
     * @return whether publication revision changes invalidate entries in this namespace
     */
    public boolean revisionBound() {
        return this.revisionBound;
    }
}
