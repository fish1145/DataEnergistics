package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

/**
 * Immutable algorithm counters used for acceptance tests and structured planning logs.
 *
 * @param sccCount       strongly connected components visited
 * @param variantCount   legal binding variants considered
 * @param planningNanos  complete Trinity planning duration
 * @param mipNanos       time spent inside MIP solves
 * @param scheduleStates compressed scheduler states explored
 */
public record TrinityPlanningStatistics(
                                        int sccCount,
                                        int variantCount,
                                        long planningNanos,
                                        long mipNanos,
                                        int scheduleStates) {

    /** Rejects negative counters before metrics can misrepresent planner behavior. */
    public TrinityPlanningStatistics {
        if (sccCount < 0 || variantCount < 0 || planningNanos < 0L || mipNanos < 0L || scheduleStates < 0 ||
                mipNanos > planningNanos) {
            throw new IllegalArgumentException("Trinity planning statistics must be non-negative and consistent");
        }
    }

    /**
     * @return zeroed statistics for plans that did not require graph solving
     */
    public static TrinityPlanningStatistics empty() {
        return new TrinityPlanningStatistics(0, 0, 0L, 0L, 0);
    }
}
