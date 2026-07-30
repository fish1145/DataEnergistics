package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch;

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
     * @return available physical submissions
     */
    int availableOperations(int coProcessors);

    /**
     * Advances the rolling window after the worker finishes its current crafting tick.
     *
     * @param startedOperations physical pattern submissions started by this worker
     */
    void recordTickUsage(int startedOperations);
}
