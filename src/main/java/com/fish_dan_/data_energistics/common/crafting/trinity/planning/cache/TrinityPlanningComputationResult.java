package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;

/**
 * Publishes one immutable algorithm result and its observable cache path.
 *
 * @param result          pure solved plan or deterministic/transient diagnostic
 * @param cachePath       selected planning path
 * @param planningNanos   elapsed time for this request, independent of cached result timing
 * @param cacheStatistics observed immutable proof-layer hits for this request
 */
public record TrinityPlanningComputationResult(
                                               TrinityAlgorithmResult<TrinityCraftingPlan> result,
                                               PlanningCachePath cachePath,
                                               long planningNanos,
                                               TrinityPlanningCacheStatistics cacheStatistics) {

    public TrinityPlanningComputationResult(
                                            TrinityAlgorithmResult<TrinityCraftingPlan> result,
                                            PlanningCachePath cachePath) {
        this(
                result,
                cachePath,
                result.successful() ? result.value().statistics().planningNanos() : 0L,
                TrinityPlanningCacheStatistics.empty());
    }
}
