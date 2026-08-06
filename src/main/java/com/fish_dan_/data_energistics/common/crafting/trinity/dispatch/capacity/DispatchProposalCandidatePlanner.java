package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;

import java.util.function.Supplier;

/**
 * Cached pure planner for the immutable candidate order consumed by provider reservation.
 */
public interface DispatchProposalCandidatePlanner {

    /**
     * @param cache shared server-lifetime computation cache
     * @return independent candidate-planning facade
     */
    static DispatchProposalCandidatePlanner create(Supplier<TrinityComputationCache> cache) {
        return new DispatchProposalCandidatePlannerImpl(cache);
    }

    /**
     * Computes every safe positive candidate once before any reservation lock is acquired.
     *
     * @param request immutable proposal request
     * @return cached immutable candidate order
     */
    DispatchProposalCandidatePlan plan(CraftingDispatchProposalRequest request);
}
