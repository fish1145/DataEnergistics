package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;

import java.util.Objects;

/**
 * Publishes one immutable algorithm result and its observable cache path.
 *
 * @param result    pure solved plan or deterministic/transient diagnostic
 * @param cachePath selected planning path
 */
public record TrinityPlanningComputationResult(
                                               TrinityAlgorithmResult<TrinityCraftingPlan> result,
                                               PlanningCachePath cachePath) {

    public TrinityPlanningComputationResult {
        Objects.requireNonNull(result, "A Trinity planning computation requires an algorithm result");
        Objects.requireNonNull(cachePath, "A Trinity planning computation requires a cache path");
    }
}
