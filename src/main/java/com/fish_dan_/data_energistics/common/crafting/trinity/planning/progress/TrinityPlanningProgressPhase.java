package com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress;

/**
 * Stable coarse-grained lifecycle boundaries of one Trinity initial planning request.
 *
 * <p>
 * These values describe real planner boundaries, rather than weighted portions of a synthetic global percentage.
 * The client must inspect the accompanying {@link TrinityPlanningProgressMeasure} before drawing any percentage.
 * </p>
 */
public enum TrinityPlanningProgressPhase {

    QUEUED,
    CAPTURING_INPUT,
    DELEGATED_TO_AE2,
    REACHABLE_SUBGRAPH,
    EXPANDING_PATTERNS,
    COMPACTING_VARIANTS,
    ANALYZING_TOPOLOGY,
    BUILDING_STRUCTURAL_PROOFS,
    LOADING_ROUTE_HINTS,
    PROJECTING_REQUEST,
    SOLVING_BOUNDED,
    SOLVING_FALLBACK,
    ASSEMBLING_PLAN,
    VALIDATING_CPU_CAPACITY,
    AWAITING_MENU_RESULT,
    WAITING_FOR_SHARED_RESULT,
    READY,
    DIAGNOSTIC,
    CANCELLED;

    /** @return whether this phase permanently terminates one confirmation-menu planning revision. */
    public boolean terminal() {
        return switch (this) {
            case DELEGATED_TO_AE2, READY, DIAGNOSTIC, CANCELLED -> true;
            default -> false;
        };
    }
}
