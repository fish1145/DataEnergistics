package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Request-local owner of the monotonic optimality budget and cooperative cancellation source.
 * <p>
 * Structural compilation and feasibility fallback use cancellation-only controls. Optional objective refinement uses
 * a bounded control created from the remaining request budget. This object is thread-confined and is never cached.
 */
public final class TrinityPlanningSession {

    /**
     * Creates one request session whose budget starts immediately.
     */
    public static TrinityPlanningSession create(
                                                BooleanSupplier cancellation,
                                                LongSupplier nanoClock,
                                                long optimisationBudgetNanos) {
        if (cancellation == null || nanoClock == null || optimisationBudgetNanos <= 0L) {
            throw new IllegalArgumentException("A Trinity planning session requires cancellation, a clock and a positive budget");
        }
        return new TrinityPlanningSession(cancellation, nanoClock, optimisationBudgetNanos);
    }

    private final BooleanSupplier cancellation;
    private final LongSupplier nanoClock;
    private final long optimisationBudgetNanos;
    private final long startedNanos;
    private final TrinityPlanningMetrics metrics;

    private TrinityPlanningSession(
                                   BooleanSupplier cancellation,
                                   LongSupplier nanoClock,
                                   long optimisationBudgetNanos) {
        this.cancellation = cancellation;
        this.nanoClock = nanoClock;
        this.optimisationBudgetNanos = optimisationBudgetNanos;
        this.startedNanos = nanoClock.getAsLong();
        this.metrics = TrinityPlanningMetrics.create();
    }

    /**
     * @return cancellation-only control used for compilation and first-feasible fallback
     */
    public TrinityPlanningControl feasibilityControl() {
        return TrinityPlanningControl.unbounded(this.cancellation, this.metrics);
    }

    /**
     * Creates a bounded control from the remaining request budget. An empty result means compilation already consumed
     * the complete optimisation allowance and the caller must enter first-feasible mode directly.
     */
    public Optional<TrinityPlanningControl> optimizationControl() {
        long remaining = remainingOptimizationNanos();
        return remaining == 0L ? Optional.empty() :
                Optional.of(TrinityPlanningControl.create(
                        this.cancellation,
                        this.nanoClock,
                        remaining,
                        this.metrics));
    }

    /** @return time spent in actual ojAlgo passes across the complete request */
    public long mipNanos() {
        return this.metrics.mipNanos();
    }

    /** @return actual ojAlgo minimise, maximise and probe passes across the complete request */
    public int solverPasses() {
        return this.metrics.solverPasses();
    }

    /** @return base or encoded solver models assembled across the complete request */
    public int solverModels() {
        return this.metrics.solverModels();
    }

    /** @return joint branch-and-bound states charged across the complete request */
    public int jointStates() {
        return this.metrics.jointStates();
    }

    /** @return DAG or mixed-graph route states charged across the complete request */
    public int routeStates() {
        return this.metrics.routeStates();
    }

    /**
     * @return remaining non-negative optimality budget
     */
    public long remainingOptimizationNanos() {
        long now = this.nanoClock.getAsLong();
        if (now < this.startedNanos) {
            throw new IllegalStateException("The Trinity planning session clock moved backwards");
        }
        long elapsed = now - this.startedNanos;
        return elapsed >= this.optimisationBudgetNanos ? 0L : this.optimisationBudgetNanos - elapsed;
    }
}
