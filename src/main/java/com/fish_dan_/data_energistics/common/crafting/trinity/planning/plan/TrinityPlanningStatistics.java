package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import java.math.BigInteger;

/**
 * Immutable algorithm counters used for acceptance tests and structured planning logs.
 *
 * @param sccCount              strongly connected components visited
 * @param variantCount          legal binding variants considered
 * @param planningNanos         complete duration of the current Trinity planning request
 * @param firstFeasibleNanos    duration before the final complete publishable plan was available; this currently does
 *                              not expose an earlier solver witness
 * @param mipNanos              time spent inside MIP solves by the current request; zero for an exact cache hit
 * @param scheduleStates        compressed scheduler states explored
 * @param solverPasses          actual ojAlgo minimise, maximise and probe calls made by the current request
 * @param solverModels          base or encoded solver models assembled by the current request
 * @param jointStates           joint branch-and-bound states charged to the current request
 * @param routeStates           DAG or mixed-graph route states charged to the current request
 * @param quality               exact proof strength of the executable plan
 * @param seedRetentionKinds    number of exact internal keys retained for a later cycle start
 * @param seedRetentionRequired aggregate retained restart seed
 * @param seedRetentionFinal    aggregate final balance on retained seed keys
 * @param seedRefinementPasses  additional cycle selection passes used to establish restart safety
 */
public record TrinityPlanningStatistics(
                                        int sccCount,
                                        int variantCount,
                                        long planningNanos,
                                        long firstFeasibleNanos,
                                        long mipNanos,
                                        int scheduleStates,
                                        int solverPasses,
                                        int solverModels,
                                        int jointStates,
                                        int routeStates,
                                        TrinityPlanQuality quality,
                                        int seedRetentionKinds,
                                        BigInteger seedRetentionRequired,
                                        BigInteger seedRetentionFinal,
                                        int seedRefinementPasses) {

    /**
     * Compatibility constructor for existing exact planning paths while richer counters are introduced.
     */
    public TrinityPlanningStatistics(
                                     int sccCount,
                                     int variantCount,
                                     long planningNanos,
                                     long mipNanos,
                                     int scheduleStates) {
        this(
                sccCount,
                variantCount,
                planningNanos,
                planningNanos,
                mipNanos,
                scheduleStates,
                0,
                0,
                0,
                0,
                TrinityPlanQuality.PROVED_OPTIMAL,
                0,
                BigInteger.ZERO,
                BigInteger.ZERO,
                0);
    }

    /**
     * @return zeroed statistics for plans that did not require graph solving
     */
    public static TrinityPlanningStatistics empty() {
        return new TrinityPlanningStatistics(0, 0, 0L, 0L, 0);
    }

    /**
     * Replaces all request-local timing and solver counters while retaining structural plan metadata.
     */
    public TrinityPlanningStatistics withRequestMetrics(
                                                        long requestPlanningNanos,
                                                        long requestFirstFeasibleNanos,
                                                        long requestMipNanos,
                                                        int requestSolverPasses,
                                                        int requestSolverModels,
                                                        int requestJointStates,
                                                        int requestRouteStates) {
        return new TrinityPlanningStatistics(
                this.sccCount,
                this.variantCount,
                requestPlanningNanos,
                requestFirstFeasibleNanos,
                requestMipNanos,
                this.scheduleStates,
                requestSolverPasses,
                requestSolverModels,
                requestJointStates,
                requestRouteStates,
                this.quality,
                this.seedRetentionKinds,
                this.seedRetentionRequired,
                this.seedRetentionFinal,
                this.seedRefinementPasses);
    }
}
