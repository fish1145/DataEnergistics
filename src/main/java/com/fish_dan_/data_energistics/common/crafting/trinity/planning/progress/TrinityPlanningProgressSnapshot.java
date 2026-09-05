package com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress;

/**
 * Immutable worker-to-menu progress value for one planning revision.
 *
 * <p>
 * The snapshot contains neither a plan nor a game object. It is therefore safe to publish through the asynchronous
 * planning channel and to serialize after the owning menu accepts it on the server thread.
 * </p>
 *
 * @param phase           current stable planner boundary
 * @param measure         meaning of the completed and total fields
 * @param completedUnits  completed exact units or observed counter value
 * @param totalUnits      exact total or a counter limit; never a percentage source unless measure is EXACT
 * @param routeStates     observed route-search state count
 * @param routeStateLimit configured route-search safety cap, or zero when unavailable
 * @param solverPasses    completed solver invocations
 * @param solverModels    assembled solver models
 * @param jointStates     observed joint-search state count
 * @param solverNanos     observed solver duration
 */
public record TrinityPlanningProgressSnapshot(
                                              TrinityPlanningProgressPhase phase,
                                              TrinityPlanningProgressMeasure measure,
                                              int completedUnits,
                                              int totalUnits,
                                              int routeStates,
                                              int routeStateLimit,
                                              int solverPasses,
                                              int solverModels,
                                              int jointStates,
                                              long solverNanos) {

    public TrinityPlanningProgressSnapshot {
        if (completedUnits < 0 || totalUnits < 0 || routeStates < 0 || routeStateLimit < 0 || solverPasses < 0 ||
                solverModels < 0 || jointStates < 0 || solverNanos < 0L) {
            throw new IllegalArgumentException("A Trinity planning progress snapshot contains invalid values");
        }
        if (measure == TrinityPlanningProgressMeasure.EXACT && (totalUnits == 0 || completedUnits > totalUnits)) {
            throw new IllegalArgumentException("Exact Trinity planning progress requires completed units within a positive total");
        }
        if (measure != TrinityPlanningProgressMeasure.EXACT && completedUnits > totalUnits && totalUnits != 0) {
            throw new IllegalArgumentException("Trinity planning progress counter exceeds its declared limit");
        }
        if (routeStateLimit != 0 && routeStates > routeStateLimit) {
            throw new IllegalArgumentException("Trinity planning route-state counter exceeds its safety limit");
        }
    }

    /** Creates a no-counter snapshot for a queue, indeterminate, or terminal phase. */
    public static TrinityPlanningProgressSnapshot withoutUnits(TrinityPlanningProgressPhase phase,
                                                               TrinityPlanningProgressMeasure measure) {
        if (measure == TrinityPlanningProgressMeasure.EXACT) {
            throw new IllegalArgumentException("An exact planning snapshot requires a finite work total");
        }
        return new TrinityPlanningProgressSnapshot(phase, measure, 0, 0, 0, 0, 0, 0, 0, 0L);
    }

    /** Creates one exact finite-stage snapshot. */
    public static TrinityPlanningProgressSnapshot exact(TrinityPlanningProgressPhase phase, int completed, int total) {
        return new TrinityPlanningProgressSnapshot(
                phase,
                TrinityPlanningProgressMeasure.EXACT,
                completed,
                total,
                0,
                0,
                0,
                0,
                0,
                0L);
    }

    /**
     * Creates an indeterminate solver snapshot. Route-state bounds remain visible as counters and must not be mapped
     * to a completion percentage.
     */
    public static TrinityPlanningProgressSnapshot solving(TrinityPlanningProgressPhase phase,
                                                          int routeStates,
                                                          int routeStateLimit,
                                                          int solverPasses,
                                                          int solverModels,
                                                          int jointStates,
                                                          long solverNanos) {
        int observedStates = Math.max(routeStates, jointStates);
        int comparableLimit = routeStateLimit >= observedStates ? routeStateLimit : 0;
        return new TrinityPlanningProgressSnapshot(
                phase,
                comparableLimit == 0 ? TrinityPlanningProgressMeasure.INDETERMINATE : TrinityPlanningProgressMeasure.COUNTER,
                observedStates,
                comparableLimit,
                routeStates,
                comparableLimit,
                solverPasses,
                solverModels,
                jointStates,
                solverNanos);
    }
}
