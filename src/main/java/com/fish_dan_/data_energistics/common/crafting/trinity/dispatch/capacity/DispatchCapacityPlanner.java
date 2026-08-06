package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;

import java.math.BigInteger;
import java.util.function.Supplier;

/**
 * Grid-scoped cached boundary for pure provider-first capacity slice calculation.
 */
public interface DispatchCapacityPlanner {

    /**
     * Creates the production planner over the shared server-lifetime computation cache.
     *
     * @param cache cache supplier resolved for each calculation
     * @return independent stateless planner facade
     */
    static DispatchCapacityPlanner create(Supplier<TrinityComputationCache> cache) {
        return new DispatchCapacityPlannerImpl(cache, CapacitySlicePlanner.create());
    }

    /**
     * Computes or reuses one immutable slice plan.
     *
     * @param capture           complete capacity capture identity and values
     * @param remainingCrafts   non-negative remaining logical work
     * @param physicalCallLimit non-negative maximum returned physical calls
     * @param cursor            persistent provider and target fairness position
     * @return immutable capacity allocations
     */
    DispatchCapacitySlicePlan plan(
                                   ProviderCapacityCapture capture,
                                   BigInteger remainingCrafts,
                                   int physicalCallLimit,
                                   CraftingDispatchCursor cursor);
}
