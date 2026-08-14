package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

/**
 * Separates pure Trinity computations while retaining one shared per-Grid LRU capacity.
 */
public enum TrinityComputationNamespace {

    /** Caller-owned planning orchestration tracked for cancellation but never retained in the shared LRU. */
    PLANNING_REQUEST(null),

    /** Target-reachable graph derived from one publication revision. */
    REACHABLE_GRAPH(RevisionDomain.PLANNING),

    /** Binding and topology structure keyed entirely by pattern semantics. */
    COMPILED_GRAPH(null),

    /** Quantity and inventory-sensitive solved planning result. */
    SOLVED_PLAN(RevisionDomain.PLANNING),

    /** Provider capacity snapshot captured for one publication and capacity epoch. */
    CAPACITY_CAPTURE(RevisionDomain.DISPATCH),

    /** Pure capacity allocation derived from a captured snapshot. */
    CAPACITY_SLICE(RevisionDomain.DISPATCH),

    /** Immutable proposal candidate ordering derived from a capacity slice. */
    PROPOSAL_CANDIDATE(RevisionDomain.DISPATCH);

    private final RevisionDomain revisionDomain;

    TrinityComputationNamespace(RevisionDomain revisionDomain) {
        this.revisionDomain = revisionDomain;
    }

    /**
     * @return whether publication revision changes invalidate entries in this namespace
     */
    public boolean revisionBound() {
        return this.revisionDomain != null;
    }

    /**
     * Keeps graph publication revisions independent from provider-capacity publication revisions while both families
     * still share the same per-Grid LRU.
     *
     * @return revision family, or {@code null} for a fully semantic namespace
     */
    RevisionDomain revisionDomain() {
        return this.revisionDomain;
    }

    /** Independent monotonic revision sources represented inside one Grid partition. */
    enum RevisionDomain {
        PLANNING,
        DISPATCH
    }
}
