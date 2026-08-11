package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityCompiledGraph;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanningPipeline;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanningStatistics;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Executes the three-level target planning path on a shared server-lifetime cache.
 * <p>
 * Exact key implementation for reachable graph, compiled structure, and solved dynamic plan caching.
 */
public final class TrinityPlanningComputation {

    /**
     * Creates a planning computation from an owned cache and the exact graph pipeline.
     *
     * @param cache    server-lifetime shared computation cache
     * @param pipeline stateless compile and solve pipeline
     * @return planning computation entry point
     */
    public static TrinityPlanningComputation create(
                                                    TrinityComputationCache cache,
                                                    TrinityGraphPlanningPipeline pipeline) {
        return new TrinityPlanningComputation(cache, pipeline, System::nanoTime);
    }

    private final TrinityComputationCache cache;
    private final TrinityGraphPlanningPipeline pipeline;
    private final LongSupplier nanoClock;

    TrinityPlanningComputation(
                               TrinityComputationCache cache,
                               TrinityGraphPlanningPipeline pipeline,
                               LongSupplier nanoClock) {
        if (cache == null || pipeline == null) {
            throw new IllegalArgumentException("A Trinity planning computation requires cache and pipeline");
        }
        this.cache = cache;
        this.pipeline = pipeline;
        this.nanoClock = nanoClock;
    }

    /**
     * Invalidates obsolete revision-bound entries and submits one caller-isolated orchestration.
     *
     * @param input immutable pure planning input
     * @return caller-owned future; cancellation with interruption enabled stops this request's planner thread
     */
    public Future<TrinityPlanningComputationResult> begin(TrinityPlanningInput input) {
        validateInput(input);
        return this.cache.submit(
                input.gridScope(),
                input.graph().revision(),
                () -> calculate(input));
    }

    /**
     * Enters the cache layers on the current accepted planner worker without submitting nested work.
     *
     * @param input immutable pure-planning input
     * @return algorithm result and selected cache path
     * @throws InterruptedException when this worker is interrupted while waiting for a shared calculation
     * @throws ExecutionException   when a shared bottom calculation fails
     */
    public TrinityPlanningComputationResult calculate(TrinityPlanningInput input)
                                                                                  throws InterruptedException, ExecutionException {
        validateInput(input);
        long startedNanos = this.nanoClock.getAsLong();
        this.cache.invalidateRevision(input.gridScope(), input.graph().revision());
        TrinityPlanningControl control = TrinityPlanningControl.unbounded();
        TrinityComputationValue<TrinityCraftingGraphSnapshot> reachable = this.cache.computeInline(
                input.gridScope(),
                TrinityComputationNamespace.REACHABLE_GRAPH,
                input.graph().revision(),
                new ReachableGraphKey(input.target()),
                () -> TrinityCachedComputation.cacheable(input.graph().reachableSubgraph(input.target())));
        CompiledGraphKey compiledKey = new CompiledGraphKey(
                input.target(),
                reachable.value().patterns().stream().map(TrinityCraftingGraphPattern::identity).toList(),
                input.settings().maxBindingVariants(),
                input.settings().maxSccKeys());
        TrinityComputationValue<TrinityAlgorithmResult<TrinityCompiledGraph>> compiled = this.cache.computeInline(
                input.gridScope(),
                TrinityComputationNamespace.COMPILED_GRAPH,
                TrinityComputationCache.SEMANTIC_REVISION,
                compiledKey,
                () -> cached(this.pipeline.compile(
                        reachable.value(),
                        input.target(),
                        input.settings().maxBindingVariants(),
                        input.settings().maxSccKeys(),
                        control)));
        if (!compiled.value().successful()) {
            long planningNanos = elapsedSince(startedNanos);
            return new TrinityPlanningComputationResult(
                    TrinityAlgorithmResult.failure(compiled.value().diagnostic()),
                    compiled.cacheHit() ? PlanningCachePath.STRUCTURE_HIT : PlanningCachePath.MISS,
                    planningNanos);
        }

        TrinityCompiledGraph structure = compiled.value().value();
        List<InventoryAmount> projectedInventory = projectInventory(structure, input.available());
        SolvedPlanKey solvedKey = new SolvedPlanKey(
                compiledKey,
                input.requestedAmount(),
                input.quantityMode(),
                projectedInventory,
                input.settings().maxScheduleStates());
        Map<AEKey, BigInteger> projectedMap = projectedInventory.stream()
                .collect(Collectors.toUnmodifiableMap(InventoryAmount::key, InventoryAmount::amount));
        TrinityComputationValue<TrinityAlgorithmResult<TrinityCraftingPlan>> solved = this.cache.computeInline(
                input.gridScope(),
                TrinityComputationNamespace.SOLVED_PLAN,
                input.graph().revision(),
                solvedKey,
                () -> cached(this.pipeline.solve(
                        structure,
                        input.graph().revision(),
                        input.requestedAmount(),
                        input.quantityMode(),
                        projectedMap,
                        input.settings(),
                        control)));
        PlanningCachePath path = !compiled.cacheHit() ? PlanningCachePath.MISS :
                solved.cacheHit() ? PlanningCachePath.EXACT_HIT : PlanningCachePath.STRUCTURE_HIT;
        long planningNanos = requestPlanningNanos(solved.value(), solved.cacheHit(), startedNanos);
        return new TrinityPlanningComputationResult(
                withRequestTiming(solved.value(), solved.cacheHit(), planningNanos),
                path,
                planningNanos);
    }

    private TrinityAlgorithmResult<TrinityCraftingPlan> withRequestTiming(
                                                                          TrinityAlgorithmResult<TrinityCraftingPlan> result,
                                                                          boolean solvedFromCache,
                                                                          long planningNanos) {
        if (!result.successful()) {
            return result;
        }
        TrinityCraftingPlan cachedPlan = result.value();
        TrinityPlanningStatistics cachedStatistics = cachedPlan.statistics();
        long mipNanos = solvedFromCache ? 0L : cachedStatistics.mipNanos();
        TrinityPlanningStatistics requestStatistics = new TrinityPlanningStatistics(
                cachedStatistics.sccCount(),
                cachedStatistics.variantCount(),
                planningNanos,
                mipNanos,
                cachedStatistics.scheduleStates());
        return TrinityAlgorithmResult.success(cachedPlan.withPlanningStatistics(requestStatistics));
    }

    private long requestPlanningNanos(
                                      TrinityAlgorithmResult<TrinityCraftingPlan> result,
                                      boolean solvedFromCache,
                                      long startedNanos) {
        long elapsedNanos = elapsedSince(startedNanos);
        if (!result.successful() || solvedFromCache) {
            return elapsedNanos;
        }
        return Math.max(elapsedNanos, result.value().statistics().mipNanos());
    }

    private long elapsedSince(long startedNanos) {
        return Math.max(0L, this.nanoClock.getAsLong() - startedNanos);
    }

    private static void validateInput(TrinityPlanningInput input) {
        if (input == null) {
            throw new IllegalArgumentException("A Trinity planning computation requires an input");
        }
    }

    private static <V> TrinityCachedComputation<TrinityAlgorithmResult<V>> cached(
                                                                                  TrinityAlgorithmResult<V> result) {
        return result.successful() || !result.diagnostic().code().transientPlanningFailure() ?
                TrinityCachedComputation.cacheable(result) :
                TrinityCachedComputation.transientValue(result);
    }

    private static List<InventoryAmount> projectInventory(
                                                          TrinityCompiledGraph compiled,
                                                          Map<AEKey, BigInteger> available) {
        ArrayList<InventoryAmount> projected = new ArrayList<>();
        for (AEKey key : compiled.relevantInventoryKeys()) {
            BigInteger amount = available.get(key);
            if (amount != null && amount.signum() > 0) {
                projected.add(new InventoryAmount(key, amount));
            }
        }
        return List.copyOf(projected);
    }

    private record ReachableGraphKey(AEKey target) {}

    private record CompiledGraphKey(
                                    AEKey target,
                                    List<TrinityPatternIdentity> patternIdentities,
                                    int maxBindingVariants,
                                    int maxSccKeys) {

        private CompiledGraphKey {
            patternIdentities = List.copyOf(patternIdentities);
        }
    }

    private record SolvedPlanKey(
                                 CompiledGraphKey compiledGraph,
                                 BigInteger requestedAmount,
                                 CraftingQuantityMode quantityMode,
                                 List<InventoryAmount> relevantInventory,
                                 int maxScheduleStates) {

        private SolvedPlanKey {
            relevantInventory = List.copyOf(relevantInventory);
        }
    }

    private record InventoryAmount(AEKey key, BigInteger amount) {}
}
