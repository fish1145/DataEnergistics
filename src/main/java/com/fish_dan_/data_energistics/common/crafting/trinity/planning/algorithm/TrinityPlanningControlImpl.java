package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Immutable-start control that fails fast when an injected clock moves backwards.
 */
final class TrinityPlanningControlImpl implements TrinityPlanningControl {

    private final BooleanSupplier cancellation;
    private final LongSupplier nanoClock;
    private final long budgetNanos;
    private final long startedNanos;
    private final boolean deadlineConfigured;

    TrinityPlanningControlImpl(BooleanSupplier cancellation,
                               LongSupplier nanoClock,
                               long budgetNanos) {
        this(cancellation, nanoClock, budgetNanos, true);
    }

    private TrinityPlanningControlImpl(BooleanSupplier cancellation,
                                       LongSupplier nanoClock,
                                       long budgetNanos,
                                       boolean deadlineConfigured) {
        this.cancellation = cancellation;
        this.nanoClock = nanoClock;
        this.budgetNanos = budgetNanos;
        this.startedNanos = nanoClock.getAsLong();
        this.deadlineConfigured = deadlineConfigured;
    }

    static TrinityPlanningControl unbounded(BooleanSupplier cancellation) {
        return new TrinityPlanningControlImpl(cancellation, () -> 0L, Long.MAX_VALUE, false);
    }

    @Override
    public boolean cancellationRequested() {
        return this.cancellation.getAsBoolean() || Thread.currentThread().isInterrupted();
    }

    @Override
    public boolean deadlineConfigured() {
        return this.deadlineConfigured;
    }

    @Override
    public boolean deadlineExceeded() {
        return this.deadlineConfigured && remainingNanos() == 0L;
    }

    @Override
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
