package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import java.util.function.LongSupplier;

/**
 * Server-wide current-tick admission boundary shared by every Trinity grid.
 *
 * <p>
 * It limits measured server-thread capacity capture and provider submission together. A provider call that already
 * owns the server thread cannot be interrupted, but no later physical call may begin after this boundary is reached.
 * </p>
 */
public interface CraftingServerDispatchBudget {

    /**
     * Creates the lifecycle-owned server budget.
     *
     * @param nanoClock              monotonic nanosecond source
     * @param targetTickNanos        desired complete server tick ceiling
     * @param overloadedTrickleNanos dispatch work retained when the non-Trinity baseline already reached the ceiling
     * @return mutable server-thread-confined budget
     */
    static CraftingServerDispatchBudget create(
                                               LongSupplier nanoClock,
                                               long targetTickNanos,
                                               long overloadedTrickleNanos) {
        return new CraftingServerDispatchBudgetImpl(nanoClock, targetTickNanos, overloadedTrickleNanos);
    }

    /**
     * @return a boundary used before server tick sampling starts
     */
    static CraftingServerDispatchBudget unbounded() {
        return CraftingServerDispatchBudgetImpl.UNBOUNDED;
    }

    /**
     * Checks whether another irreversible provider call may begin.
     *
     * @param activeDispatchNanos elapsed work in the currently open capture or submission scope
     * @return whether the shared current-tick allowance remains positive
     */
    boolean canStart(long activeDispatchNanos);

    /**
     * Accounts completed server-thread dispatch work.
     *
     * @param elapsedNanos non-negative measured duration
     */
    void record(long elapsedNanos);
}
