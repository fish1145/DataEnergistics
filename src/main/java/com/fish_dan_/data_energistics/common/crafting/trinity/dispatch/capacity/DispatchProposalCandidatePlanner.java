package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;

import java.util.function.BooleanSupplier;
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
    default DispatchProposalCandidatePlan plan(CraftingDispatchProposalRequest request) {
        return plan(request, () -> true);
    }

    /**
     * Computes candidates only while the owning proposal ticket remains active. The lifecycle check is evaluated under
     * the shared cache lock before an entry can be joined or created.
     *
     * @param request         immutable proposal request
     * @param lifecycleActive true while the owning proposal ticket may retain cache work
     * @return cached immutable candidate order, or an empty order when lifecycle admission is denied
     */
    DispatchProposalCandidatePlan plan(
                                       CraftingDispatchProposalRequest request,
                                       BooleanSupplier lifecycleActive);
}
