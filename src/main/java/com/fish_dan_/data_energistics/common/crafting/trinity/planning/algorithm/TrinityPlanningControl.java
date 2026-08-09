package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Cooperative cancellation with an optional injected monotonic deadline for deterministic tests and callers that
 * explicitly require one.
 * <p>
 * Immutable-start control that fails fast when an injected clock moves backwards.
 */
public final class TrinityPlanningControl {

    /**
     * Creates the production control for a Future whose cancellation interrupts the planner thread.
     *
     * @return unbounded interrupt-aware control
     */
    public static TrinityPlanningControl unbounded() {
        return TrinityPlanningControl.unbounded(() -> false);
    }

    /**
     * Creates a bounded control using an injected monotonic clock.
     *
     * @param cancellation explicit request cancellation
     * @param nanoClock    monotonic nanosecond source
     * @param budgetNanos  positive total algorithm budget
     * @return validated control
     */
    public static TrinityPlanningControl create(BooleanSupplier cancellation,
                                                LongSupplier nanoClock,
                                                long budgetNanos) {
        if (cancellation == null || nanoClock == null || budgetNanos <= 0L) {
            throw new IllegalArgumentException(
                    "A Trinity planning control requires complete sources and a positive budget");
        }
        return new TrinityPlanningControl(cancellation, nanoClock, budgetNanos);
    }

    private final BooleanSupplier cancellation;
    private final LongSupplier nanoClock;
    private final long budgetNanos;
    private final long startedNanos;
    private final boolean deadlineConfigured;

    TrinityPlanningControl(BooleanSupplier cancellation,
                           LongSupplier nanoClock,
                           long budgetNanos) {
        this(cancellation, nanoClock, budgetNanos, true);
    }

    private TrinityPlanningControl(BooleanSupplier cancellation,
                                   LongSupplier nanoClock,
                                   long budgetNanos,
                                   boolean deadlineConfigured) {
        this.cancellation = cancellation;
        this.nanoClock = nanoClock;
        this.budgetNanos = budgetNanos;
        this.startedNanos = nanoClock.getAsLong();
        this.deadlineConfigured = deadlineConfigured;
    }

    /**
     * Creates the production control used by Trinity planning. It has no wall-clock deadline; cancellation is driven
     * only by the owning future or caller.
     *
     * @param cancellation explicit request cancellation
     * @return unbounded cooperative control
     */
    public static TrinityPlanningControl unbounded(BooleanSupplier cancellation) {
        return new TrinityPlanningControl(cancellation, () -> 0L, Long.MAX_VALUE, false);
    }

    /**
     * @return process-interruption or explicit cooperative cancellation
     */
    public boolean cancellationRequested() {
        return this.cancellation.getAsBoolean() || Thread.currentThread().isInterrupted();
    }

    /**
     * @return whether this control has an actual wall-clock deadline
     */
    public boolean deadlineConfigured() {
        return this.deadlineConfigured;
    }

    /**
     * @return whether the injected monotonic budget has been exhausted
     */
    public boolean deadlineExceeded() {
        return this.deadlineConfigured && remainingNanos() == 0L;
    }

    /**
     * @return remaining non-negative nanoseconds
     */
    public long remainingNanos() {
        if (!this.deadlineConfigured) {
            return Long.MAX_VALUE;
        }
        long now = this.nanoClock.getAsLong();
        if (now < this.startedNanos) {
            throw new IllegalStateException("The Trinity planning clock moved backwards");
        }
        long elapsed = now - this.startedNanos;
        return elapsed >= this.budgetNanos ? 0L : this.budgetNanos - elapsed;
    }
}
