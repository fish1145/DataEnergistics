package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchResult;

/**
 * Unique server-thread boundary for a provider call, input ownership decision, and accounting settlement.
 */
public interface CraftingDispatchCommitter {

    /**
     * Creates an independent stateless committer.
     *
     * @return synchronous committer
     */
    static CraftingDispatchCommitter create() {
        return new CraftingDispatchCommitterImpl();
    }

    /**
     * Performs at most one physical provider call and settles the paired accounting exactly once.
     *
     * @param request fully prevalidated synchronous request
     * @return structured ownership and accounting result
     */
    CraftingDispatchResult commit(CraftingDispatchCommitRequest request);
}
