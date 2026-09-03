package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

/**
 * Separates pure Trinity computations while retaining one shared per-Grid LRU capacity.
 */
public enum TrinityComputationNamespace {

    /** Caller-owned planning orchestration tracked for cancellation but never retained in the shared LRU. */
    PLANNING_REQUEST(RevisionDomain.SEMANTIC),

    /** Target-reachable graph derived from one publication revision. */
    REACHABLE_GRAPH(RevisionDomain.PLANNING),

    /** Complete binding expansion of one immutable pattern semantic. */
    PATTERN_EXPANSION(RevisionDomain.SEMANTIC),

    /** Target closure topology assembled from semantic pattern expansions. */
    TARGET_STRUCTURE(RevisionDomain.SEMANTIC),

    /** Quantity-independent producer family for one exact DAG output axis. */
    DAG_ROUTE_PROOF(RevisionDomain.SEMANTIC),

    /** Previously verified quantity-free choices for one multi-producer route family. */
    DAG_ROUTE_HINT(RevisionDomain.SEMANTIC),

    /** Exact deterministic unit route and restart seed for one semantic cyclic component. */
    CYCLE_UNIT_PROOF(RevisionDomain.SEMANTIC),

    /** Sparse immutable conservation coefficients for one semantic cyclic component. */
    MIP_COEFFICIENT_TEMPLATE(RevisionDomain.SEMANTIC),

    /** Exact quantity and inventory request shared only while its calculation remains in flight. */
    REQUEST_IN_FLIGHT(RevisionDomain.PLANNING),

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
        return this.revisionDomain != RevisionDomain.SEMANTIC;
    }

    /**
     * Keeps graph publication revisions independent from provider-capacity publication revisions while both families
     * still share the same per-Grid LRU.
     *
     * @return revision family or the explicit semantic domain
     */
    RevisionDomain revisionDomain() {
        return this.revisionDomain;
    }

    /** Independent monotonic revision sources represented inside one Grid partition. */
    enum RevisionDomain {
        SEMANTIC,
        PLANNING,
        DISPATCH
    }
}
