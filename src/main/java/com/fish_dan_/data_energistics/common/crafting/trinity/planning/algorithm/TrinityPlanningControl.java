package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Cooperative cancellation with an optional injected monotonic deadline for deterministic tests and callers that
 * explicitly require one.
 */
public interface TrinityPlanningControl {

    /**
     * Creates the production control for a Future whose cancellation interrupts the planner thread.
     *
     * @return unbounded interrupt-aware control
     */
    static TrinityPlanningControl unbounded() {
        return TrinityPlanningControlImpl.unbounded(() -> false);
    }

    /**
     * Creates the production control used by Trinity planning. It has no wall-clock deadline; cancellation is driven
     * only by the owning future or caller.
     *
     * @param cancellation explicit request cancellation
     * @return unbounded cooperative control
     */
    static TrinityPlanningControl unbounded(BooleanSupplier cancellation) {
        return TrinityPlanningControlImpl.unbounded(cancellation);
    }

    /**
     * Creates a bounded control using an injected monotonic clock.
     *
     * @param cancellation explicit request cancellation
     * @param nanoClock    monotonic nanosecond source
     * @param budgetNanos  positive total algorithm budget
     * @return validated control
     */
    static TrinityPlanningControl create(BooleanSupplier cancellation,
                                         LongSupplier nanoClock,
                                         long budgetNanos) {
        if (cancellation == null || nanoClock == null || budgetNanos <= 0L) {
            throw new IllegalArgumentException(
                    "A Trinity planning control requires complete sources and a positive budget");
        }
        return new TrinityPlanningControlImpl(cancellation, nanoClock, budgetNanos);
    }

    /**
     * @return process-interruption or explicit cooperative cancellation
     */
    boolean cancellationRequested();

    /**
     * @return whether this control has an actual wall-clock deadline
     */
    boolean deadlineConfigured();

    /**
     * @return whether the injected monotonic budget has been exhausted
     */
    boolean deadlineExceeded();

    /**
     * @return remaining non-negative nanoseconds
     */
    long remainingNanos();
}
