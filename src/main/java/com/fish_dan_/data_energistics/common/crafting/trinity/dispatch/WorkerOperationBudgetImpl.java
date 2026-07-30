package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch;

/**
 * Default rolling-window implementation used by each Trinity crafting worker.
 */
final class WorkerOperationBudgetImpl implements WorkerOperationBudget {

    /** Physical submissions started in each of the three preceding active crafting ticks. */
    private final int[] recentOperations = new int[3];

    @Override
    public int availableOperations(int coProcessors) {
        if (coProcessors < 0) {
            throw new IllegalArgumentException("coProcessors must not be negative");
        }
        long recentlyUsed = 0L;
        for (int used : this.recentOperations) {
            recentlyUsed += used;
        }
        long available = (long) coProcessors + 1L - recentlyUsed;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, available));
    }

    @Override
    public void recordTickUsage(int startedOperations) {
        if (startedOperations < 0) {
            throw new IllegalArgumentException("startedOperations must not be negative");
        }
        this.recentOperations[2] = this.recentOperations[1];
        this.recentOperations[1] = this.recentOperations[0];
        this.recentOperations[0] = startedOperations;
    }
}
