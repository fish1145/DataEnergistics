package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressPhase;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressReporter;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressSnapshot;

import org.jspecify.annotations.Nullable;

/**
 * Request-private counters shared by every control created from one planning session.
 */
final class TrinityPlanningMetrics {

    private static final int COUNTER_PUBLICATION_STEP = 32;
    private static final int SOLVER_PASS_PUBLICATION_STEP = 4;
    private static final TrinityPlanningMetrics NO_OP = new TrinityPlanningMetrics(
            false,
            TrinityPlanningProgressReporter.none());

    static TrinityPlanningMetrics create(TrinityPlanningProgressReporter progress) {
        return new TrinityPlanningMetrics(true, progress);
    }

    static TrinityPlanningMetrics noOp() {
        return NO_OP;
    }

    private final boolean enabled;
    private final TrinityPlanningProgressReporter progress;
    private long mipNanos;
    private int solverPasses;
    private int solverModels;
    private int jointStates;
    private int routeStates;
    private @Nullable TrinityPlanningProgressPhase progressPhase;
    private int routeStateLimit;
    private int publishedJointStates;
    private int publishedRouteStates;
    private int publishedSolverPasses;
    private int publishedSolverModels;

    private TrinityPlanningMetrics(boolean enabled, TrinityPlanningProgressReporter progress) {
        this.enabled = enabled;
        this.progress = progress;
    }

    void beginPhase(TrinityPlanningProgressPhase phase, int routeStateLimit) {
        if (routeStateLimit < 0) {
            throw new IllegalArgumentException("A Trinity route-state progress limit cannot be negative");
        }
        this.progressPhase = phase;
        this.routeStateLimit = routeStateLimit;
        publishProgress(true);
    }

    void recordSolverModel() {
        if (this.enabled) {
            this.solverModels = saturatingAdd(this.solverModels, 1);
            publishProgress(false);
        }
    }

    void recordSolverPass(long nanos) {
        if (nanos < 0L) {
            throw new IllegalArgumentException("A Trinity solver duration cannot be negative");
        }
        if (this.enabled) {
            this.solverPasses = saturatingAdd(this.solverPasses, 1);
            this.mipNanos = saturatingAdd(this.mipNanos, nanos);
            publishProgress(false);
        }
    }

    void recordJointStates(int states) {
        if (states < 0) {
            throw new IllegalArgumentException("Trinity joint states cannot be negative");
        }
        if (this.enabled) {
            this.jointStates = saturatingAdd(this.jointStates, states);
            publishProgress(false);
        }
    }

    void recordRouteStates(int states) {
        if (states < 0) {
            throw new IllegalArgumentException("Trinity route states cannot be negative");
        }
        if (this.enabled) {
            this.routeStates = saturatingAdd(this.routeStates, states);
            publishProgress(false);
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

    private void publishProgress(boolean force) {
        TrinityPlanningProgressPhase phase = this.progressPhase;
        if (phase == null || this.progress == TrinityPlanningProgressReporter.none()) {
            return;
        }
        if (!force && this.routeStates - this.publishedRouteStates < COUNTER_PUBLICATION_STEP &&
                this.jointStates - this.publishedJointStates < COUNTER_PUBLICATION_STEP &&
                this.solverPasses - this.publishedSolverPasses < SOLVER_PASS_PUBLICATION_STEP &&
                this.solverModels == this.publishedSolverModels) {
            return;
        }
        this.progress.publish(TrinityPlanningProgressSnapshot.solving(
                phase,
                this.routeStates,
                this.routeStateLimit,
                this.solverPasses,
                this.solverModels,
                this.jointStates,
                this.mipNanos));
        this.publishedRouteStates = this.routeStates;
        this.publishedJointStates = this.jointStates;
        this.publishedSolverPasses = this.solverPasses;
        this.publishedSolverModels = this.solverModels;
    }

    private static int saturatingAdd(int current, int added) {
        return added > Integer.MAX_VALUE - current ? Integer.MAX_VALUE : current + added;
    }

    private static long saturatingAdd(long current, long added) {
        return added > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + added;
    }
}
