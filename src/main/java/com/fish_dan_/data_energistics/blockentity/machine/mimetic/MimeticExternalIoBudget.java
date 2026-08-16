package com.fish_dan_.data_energistics.blockentity.machine.mimetic;

import java.util.function.LongSupplier;

/**
 * Shares an operation and wall-clock budget across every accelerated invocation in one real server tick.
 *
 * <p>
 * The time window starts at the first external operation, so internal production work performed beforehand does not
 * consume the I/O allowance. A slow external call may exceed the deadline, but no later call can start afterward.
 * </p>
 */
public final class MimeticExternalIoBudget {

    private final int maximumOperations;
    private final long maximumNanos;
    private final LongSupplier nanoTime;
    private long gameTime = Long.MIN_VALUE;
    private long deadlineNanos;
    private int remainingOperations;
    private boolean timingStarted;

    /**
     * Creates a production budget backed by the monotonic system clock.
     *
     * @param maximumOperations positive operation limit per real tick
     * @param maximumNanos      positive time allowance per real tick
     */
    public MimeticExternalIoBudget(int maximumOperations, long maximumNanos) {
        this(maximumOperations, maximumNanos, System::nanoTime);
    }

    MimeticExternalIoBudget(int maximumOperations, long maximumNanos, LongSupplier nanoTime) {
        if (maximumOperations <= 0) {
            throw new IllegalArgumentException("maximumOperations must be positive");
        }
        if (maximumNanos <= 0L) {
            throw new IllegalArgumentException("maximumNanos must be positive");
        }
        this.maximumOperations = maximumOperations;
        this.maximumNanos = maximumNanos;
        this.nanoTime = nanoTime;
    }

    /**
     * Selects the real server tick whose calls share this allowance.
     *
     * @param currentGameTime current server-level game time
     */
    public void begin(long currentGameTime) {
        if (this.gameTime == currentGameTime) {
            return;
        }
        this.gameTime = currentGameTime;
        this.remainingOperations = this.maximumOperations;
        this.timingStarted = false;
    }

    /**
     * Reserves one external call if both limits still allow it.
     *
     * @return {@code true} when the caller may start one external operation
     */
    public boolean tryAcquire() {
        if (this.remainingOperations <= 0) {
            return false;
        }

        long now = this.nanoTime.getAsLong();
        if (!this.timingStarted) {
            this.deadlineNanos = now + this.maximumNanos;
            this.timingStarted = true;
        } else if (now - this.deadlineNanos >= 0L) {
            return false;
        }
        this.remainingOperations--;
        return true;
    }
}
