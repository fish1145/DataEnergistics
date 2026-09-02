package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

/**
 * Request-private bound on actual ojAlgo solves performed by a cycle diagnostic.
 *
 * <p>
 * The budget does not record global telemetry itself. Its final used count is charged once by the owning joint
 * search so ordinary and radix backends cannot double-count the same solver state.
 * </p>
 */
public final class TrinityCycleSolveBudget {

    /**
     * Creates a bounded diagnostic budget.
     */
    public static TrinityCycleSolveBudget limited(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("A Trinity cycle solve budget must be positive");
        }
        return new TrinityCycleSolveBudget(limit);
    }

    /**
     * Creates the solver budget used by executable paths whose states are already bounded by branch search.
     */
    public static TrinityCycleSolveBudget unbounded() {
        return new TrinityCycleSolveBudget(Integer.MAX_VALUE);
    }

    private final int limit;
    private int used;

    private TrinityCycleSolveBudget(int limit) {
        this.limit = limit;
    }

    /**
     * Reserves one actual solver call before it can start.
     *
     * @return whether the call remains inside the configured bound
     */
    public boolean tryConsume() {
        if (this.used >= this.limit) {
            return false;
        }
        this.used = Math.incrementExact(this.used);
        return true;
    }

    /**
     * @return configured maximum actual solver calls
     */
    public int limit() {
        return this.limit;
    }

    /**
     * @return actual solver calls already reserved
     */
    public int used() {
        return this.used;
    }
}
