package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

/**
 * Request-private counters shared by every control created from one planning session.
 */
final class TrinityPlanningMetrics {

    private static final TrinityPlanningMetrics NO_OP = new TrinityPlanningMetrics(false);

    static TrinityPlanningMetrics create() {
        return new TrinityPlanningMetrics(true);
    }

    static TrinityPlanningMetrics noOp() {
        return NO_OP;
    }

    private final boolean enabled;
    private long mipNanos;
    private int solverPasses;
    private int solverModels;
    private int jointStates;
    private int routeStates;

    private TrinityPlanningMetrics(boolean enabled) {
        this.enabled = enabled;
    }

    void recordSolverModel() {
        if (this.enabled) {
            this.solverModels = saturatingAdd(this.solverModels, 1);
        }
    }

    void recordSolverPass(long nanos) {
        if (nanos < 0L) {
            throw new IllegalArgumentException("A Trinity solver duration cannot be negative");
        }
        if (this.enabled) {
            this.solverPasses = saturatingAdd(this.solverPasses, 1);
            this.mipNanos = saturatingAdd(this.mipNanos, nanos);
        }
    }

    void recordJointStates(int states) {
        if (states < 0) {
            throw new IllegalArgumentException("Trinity joint states cannot be negative");
        }
        if (this.enabled) {
            this.jointStates = saturatingAdd(this.jointStates, states);
        }
    }

    void recordRouteStates(int states) {
        if (states < 0) {
            throw new IllegalArgumentException("Trinity route states cannot be negative");
        }
        if (this.enabled) {
            this.routeStates = saturatingAdd(this.routeStates, states);
        }
    }

    long mipNanos() {
        return this.mipNanos;
    }

    int solverPasses() {
        return this.solverPasses;
    }

    int solverModels() {
        return this.solverModels;
    }

    int jointStates() {
        return this.jointStates;
    }

    int routeStates() {
        return this.routeStates;
    }

    private static int saturatingAdd(int current, int added) {
        return added > Integer.MAX_VALUE - current ? Integer.MAX_VALUE : current + added;
    }

    private static long saturatingAdd(long current, long added) {
        return added > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + added;
    }
}
