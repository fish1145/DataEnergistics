package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Cooperative cancellation and monotonic deadline boundary shared by MIP and compressed scheduling.
 */
public interface TrinityPlanningControl {

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
     * @return whether the injected monotonic budget has been exhausted
     */
    boolean deadlineExceeded();

    /**
     * @return remaining non-negative nanoseconds
     */
    long remainingNanos();
}
