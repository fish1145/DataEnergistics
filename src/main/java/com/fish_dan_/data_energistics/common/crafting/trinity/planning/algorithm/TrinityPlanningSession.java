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

    private TrinityPlanningSession(
                                   BooleanSupplier cancellation,
                                   LongSupplier nanoClock,
                                   long optimisationBudgetNanos) {
        this.cancellation = cancellation;
        this.nanoClock = nanoClock;
        this.optimisationBudgetNanos = optimisationBudgetNanos;
        this.startedNanos = nanoClock.getAsLong();
    }

    /**
     * @return cancellation-only control used for compilation and first-feasible fallback
     */
    public TrinityPlanningControl feasibilityControl() {
        return TrinityPlanningControl.unbounded(this.cancellation);
    }

    /**
     * Creates a bounded control from the remaining request budget. An empty result means compilation already consumed
     * the complete optimisation allowance and the caller must enter first-feasible mode directly.
     */
    public Optional<TrinityPlanningControl> optimizationControl() {
        long remaining = remainingOptimizationNanos();
        return remaining == 0L ? Optional.empty() :
                Optional.of(TrinityPlanningControl.create(this.cancellation, this.nanoClock, remaining));
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
