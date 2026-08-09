package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import java.util.function.LongSupplier;

/**
 * Monotonic server-thread implementation that subtracts Trinity work before estimating the underlying MSPT.
 */
final class MeasuredCraftingServerDispatchBudget implements CraftingServerDispatchBudget {

    static final CraftingServerDispatchBudget UNBOUNDED = new CraftingServerDispatchBudget() {

        @Override
        public boolean canStart(long activeDispatchNanos) {
            if (activeDispatchNanos < 0L) {
                throw new IllegalArgumentException("Active dispatch duration must not be negative");
            }
            return true;
        }

        @Override
        public void record(long elapsedNanos) {
            if (elapsedNanos < 0L) {
                throw new IllegalArgumentException("Recorded dispatch duration must not be negative");
            }
        }
    };

    private final LongSupplier nanoClock;
    private final long targetTickNanos;
    private final long overloadedTrickleNanos;
    private long tickStartedAtNanos;
    private long previousNonTrinityNanos;
    private long currentDispatchNanos;
    private long lastCompletedDispatchNanos;
    private boolean tickActive;

    MeasuredCraftingServerDispatchBudget(
                                         LongSupplier nanoClock,
                                         long targetTickNanos,
                                         long overloadedTrickleNanos) {
        if (nanoClock == null) {
            throw new IllegalArgumentException("Server dispatch nano clock is required");
        }
        if (overloadedTrickleNanos <= 0L || targetTickNanos < overloadedTrickleNanos) {
            throw new IllegalArgumentException("Server dispatch tick budgets are out of range");
        }
        this.nanoClock = nanoClock;
        this.targetTickNanos = targetTickNanos;
        this.overloadedTrickleNanos = overloadedTrickleNanos;
    }

    @Override
    public boolean canStart(long activeDispatchNanos) {
        if (activeDispatchNanos < 0L) {
            throw new IllegalArgumentException("Active dispatch duration must not be negative");
        }
        if (!this.tickActive) {
            return true;
        }
        long totalDispatchNanos = Math.addExact(this.currentDispatchNanos, activeDispatchNanos);
        long elapsedTickNanos = elapsed(this.tickStartedAtNanos, this.nanoClock.getAsLong());
        long currentNonTrinityNanos = Math.max(0L, elapsedTickNanos - totalDispatchNanos);
        long baselineNanos = Math.max(this.previousNonTrinityNanos, currentNonTrinityNanos);
        long allowanceNanos = baselineNanos >= this.targetTickNanos ?
                this.overloadedTrickleNanos :
                this.targetTickNanos - baselineNanos;
        return totalDispatchNanos < allowanceNanos;
    }

    @Override
    public void record(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            throw new IllegalArgumentException("Recorded dispatch duration must not be negative");
        }
        if (this.tickActive) {
            this.currentDispatchNanos = Math.addExact(this.currentDispatchNanos, elapsedNanos);
        }
    }

    void beginTick() {
        this.tickStartedAtNanos = this.nanoClock.getAsLong();
        this.currentDispatchNanos = 0L;
        this.tickActive = true;
    }

    void completeTick(long completeTickNanos) {
        if (completeTickNanos < 0L) {
            throw new IllegalArgumentException("Complete server tick duration must not be negative");
        }
        this.lastCompletedDispatchNanos = this.currentDispatchNanos;
        this.previousNonTrinityNanos = Math.max(0L, completeTickNanos - this.currentDispatchNanos);
        this.tickActive = false;
    }

    void reset() {
        this.tickStartedAtNanos = 0L;
        this.previousNonTrinityNanos = 0L;
        this.currentDispatchNanos = 0L;
        this.lastCompletedDispatchNanos = 0L;
        this.tickActive = false;
    }

    long lastCompletedDispatchNanos() {
        return this.lastCompletedDispatchNanos;
    }

    private static long elapsed(long startNanos, long endNanos) {
        long elapsed = endNanos - startNanos;
        if (elapsed < 0L) {
            throw new IllegalStateException("Server dispatch nano clock moved backwards");
        }
        return elapsed;
    }
}
