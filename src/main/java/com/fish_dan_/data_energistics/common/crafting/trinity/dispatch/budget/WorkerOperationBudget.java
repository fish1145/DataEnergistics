package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget;

/**
 * Owns the rolling physical-operation allowance for one Trinity crafting worker.
 *
 * <p>
 * A separate instance is attached to every worker so one worker's recent submissions cannot consume another worker's
 * co-processor allowance.
 */
public interface WorkerOperationBudget {

    /**
     * Creates an empty three-tick operation window for one worker.
     *
     * @return independent worker operation budget
     */
    static WorkerOperationBudget create() {
        return new WorkerOperationBudgetImpl();
    }

    /**
     * Calculates how many physical pattern submissions the worker may start in the current tick.
     *
     * @param coProcessors complete co-processor count owned by this worker
     * @param currentTick  current monotonic server tick
     * @return available physical submissions
     */
    int availableOperations(int coProcessors, long currentTick);

    /**
     * Returns the physical submissions retained in this worker's time-aware rolling window for load-aware CPU
     * selection.
     *
     * @param currentTick current monotonic server tick
     * @return recent physical-operation load
     */
    long recentOperations(long currentTick);

    /**
     * Adds the worker's completed physical submissions for the current server tick. Multiple execution slices in the
     * same tick accumulate into one window slot.
     *
     * @param currentTick       current monotonic server tick
     * @param startedOperations physical pattern submissions started by this worker
     */
    void recordTickUsage(long currentTick, int startedOperations);
}
