package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.search;

/**
 * Accumulates solver passes and observational wall-clock time across every objective under one planning deadline.
 */
public final class TrinityRadixSolverMetrics {

    private int passes;
    private long nanos;

    /**
     * Records one completed ojAlgo invocation with exact overflow checks.
     */
    public void addPass(long elapsedNanos) {
        this.passes = Math.addExact(this.passes, 1);
        this.nanos = Math.addExact(this.nanos, elapsedNanos);
    }

    /** Includes an already verified feasibility pass before later objective refinement. */
    public void include(int completedPasses, long elapsedNanos) {
        this.passes = Math.addExact(this.passes, completedPasses);
        this.nanos = Math.addExact(this.nanos, elapsedNanos);
    }

    /**
     * @return total completed solver invocations
     */
    public int passes() {
        return this.passes;
    }

    /**
     * @return observational solver duration, never used as a correctness assertion
     */
    public long nanos() {
        return this.nanos;
    }
}
