package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

/**
 * Immutable algorithm counters used for acceptance tests and structured planning logs.
 *
 * @param sccCount           strongly connected components visited
 * @param variantCount       legal binding variants considered
 * @param planningNanos      complete duration of the current Trinity planning request
 * @param firstFeasibleNanos duration before the first executable plan was available
 * @param mipNanos           time spent inside MIP solves by the current request; zero for an exact cache hit
 * @param solverPasses       completed ojAlgo passes retained by the selected plan
 * @param solverModels       ojAlgo models built or copied for the selected request
 * @param scheduleStates     compressed scheduler states explored
 * @param quality            exact proof strength of the executable plan
 */
public record TrinityPlanningStatistics(
                                        int sccCount,
                                        int variantCount,
                                        long planningNanos,
                                        long firstFeasibleNanos,
                                        long mipNanos,
                                        int solverPasses,
                                        int solverModels,
                                        int scheduleStates,
                                        TrinityPlanQuality quality) {

    /**
     * Rejects negative counters before metrics can misrepresent planner behavior.
     */
    public TrinityPlanningStatistics {
        if (sccCount < 0 || variantCount < 0 || planningNanos < 0L || firstFeasibleNanos < 0L ||
                firstFeasibleNanos > planningNanos || mipNanos < 0L || mipNanos > planningNanos ||
                solverPasses < 0 || solverModels < 0 || scheduleStates < 0 || quality == null) {
            throw new IllegalArgumentException("Trinity planning statistics must be non-negative and consistent");
        }
    }

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
                0,
                0,
                scheduleStates,
                TrinityPlanQuality.PROVED_OPTIMAL);
    }

    /**
     * @return zeroed statistics for plans that did not require graph solving
     */
    public static TrinityPlanningStatistics empty() {
        return new TrinityPlanningStatistics(0, 0, 0L, 0L, 0);
    }

    /**
     * Replaces request-local timing while retaining solver proof metadata from a cached immutable plan.
     */
    public TrinityPlanningStatistics withRequestTiming(
                                                       long requestPlanningNanos,
                                                       long requestFirstFeasibleNanos,
                                                       long requestMipNanos) {
        return new TrinityPlanningStatistics(
                this.sccCount,
                this.variantCount,
                requestPlanningNanos,
                requestFirstFeasibleNanos,
                requestMipNanos,
                this.solverPasses,
                this.solverModels,
                this.scheduleStates,
                this.quality);
    }
}
