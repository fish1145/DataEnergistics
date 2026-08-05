package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanningPipeline;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Executes the three-level target planning path on a shared server-lifetime cache.
 */
public interface TrinityPlanningComputation {

    /**
     * Creates a planning computation from an owned cache and the exact graph pipeline.
     *
     * @param cache    server-lifetime shared computation cache
     * @param pipeline stateless compile and solve pipeline
     * @return planning computation entry point
     */
    static TrinityPlanningComputation create(
                                               TrinityComputationCache cache,
                                               TrinityGraphPlanningPipeline pipeline) {
        return new TrinityPlanningComputationImpl(cache, pipeline);
    }

    /**
     * Invalidates obsolete revision-bound entries and submits one caller-isolated orchestration.
     *
     * @param input immutable pure planning input
     * @return caller-owned future; cancellation does not cancel shared bottom calculations
     */
    Future<TrinityPlanningComputationResult> begin(TrinityPlanningInput input);

    /**
     * Enters the cache layers on the current accepted planner worker without submitting nested work.
     *
     * @param input immutable pure-planning input
     * @return algorithm result and selected cache path
     * @throws InterruptedException when this worker is interrupted while waiting for a shared calculation
     * @throws ExecutionException   when a shared bottom calculation fails
     */
    TrinityPlanningComputationResult calculate(TrinityPlanningInput input)
            throws InterruptedException, ExecutionException;
}
