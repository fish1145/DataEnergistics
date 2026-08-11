package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;

import java.util.Objects;

/**
 * Publishes one immutable algorithm result and its observable cache path.
 *
 * @param result        pure solved plan or deterministic/transient diagnostic
 * @param cachePath     selected planning path
 * @param planningNanos elapsed time for this request, independent of cached result timing
 */
public record TrinityPlanningComputationResult(
                                               TrinityAlgorithmResult<TrinityCraftingPlan> result,
                                               PlanningCachePath cachePath,
                                               long planningNanos) {

    public TrinityPlanningComputationResult(
                                            TrinityAlgorithmResult<TrinityCraftingPlan> result,
                                            PlanningCachePath cachePath) {
        this(
                result,
                cachePath,
                result.successful() ? result.value().statistics().planningNanos() : 0L);
    }

    public TrinityPlanningComputationResult {
        Objects.requireNonNull(result, "A Trinity planning computation requires an algorithm result");
        Objects.requireNonNull(cachePath, "A Trinity planning computation requires a cache path");
        if (planningNanos < 0L) {
            throw new IllegalArgumentException("Trinity request planning time must not be negative");
        }
    }
}
