package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningSession;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityCompiledGraph;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanningPipeline;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanningStatistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Executes target planning through retained structure and transient request-coalescing layers.
 * <p>
 * Quantity and inventory remain request-local and no completed executable plan is retained.
 */
public final class TrinityPlanningComputation {

    private static final int SOLVE_STRATEGY_VERSION = 2;

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
        if (cache == null || pipeline == null || nanoClock == null) {
            throw new IllegalArgumentException("A Trinity planning computation requires cache and pipeline");
        }
        this.cache = cache;
        this.pipeline = pipeline;
        this.nanoClock = nanoClock;
    }

    /**
     * Submits one caller-isolated orchestration; revision invalidation occurs when that request enters the shared
     * computation layers.
     *
     * @param input immutable pure planning input
     * @return caller-owned future; cancellation with interruption enabled stops this request's planner thread
     */
    public Future<TrinityPlanningComputationResult> begin(TrinityPlanningInput input) {
        validateInput(input);
        return this.cache.submit(
                input.gridScope(),
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
        TrinityPlanningLimits limits = input.limits();
        TrinityPlanningSession session = TrinityPlanningSession.create(
                () -> false,
                this.nanoClock,
                TimeUnit.MILLISECONDS.toNanos(limits.planningBudgetMs()));
        TrinityPlanningControl feasibilityControl = session.feasibilityControl();
        TrinityComputationValue<TrinityCraftingGraphSnapshot> reachable = this.cache.computeInline(
                input.gridScope(),
                TrinityComputationNamespace.REACHABLE_GRAPH,
                input.graph().revision(),
                new ReachableGraphKey(input.target()),
                () -> TrinityCachedComputation.cacheable(input.graph().reachableSubgraph(input.target())));
        CompiledGraphKey compiledKey = new CompiledGraphKey(
                input.target(),
                reachable.value().patterns().stream().map(TrinityCraftingGraphPattern::identity).toList(),
                limits.maxBindingVariants(),
                limits.maxSccKeys());
        TrinityComputationValue<TrinityAlgorithmResult<TrinityCompiledGraph>> compiled = this.cache.computeInline(
                input.gridScope(),
                TrinityComputationNamespace.COMPILED_GRAPH,
                TrinityComputationCache.SEMANTIC_REVISION,
                compiledKey,
                () -> cacheSuccessful(this.pipeline.compile(
                        reachable.value(),
                        input.target(),
                        limits.maxBindingVariants(),
                        limits.maxSccKeys(),
                        feasibilityControl)));
        if (!compiled.value().successful()) {
            long planningNanos = elapsedSince(startedNanos);
            return new TrinityPlanningComputationResult(
                    TrinityAlgorithmResult.failure(compiled.value().diagnostic()),
                    compiled.cacheHit() ? PlanningCachePath.STRUCTURE_HIT : PlanningCachePath.MISS,
                    planningNanos);
        }

        TrinityCompiledGraph structure = compiled.value().value();
        List<InventoryAmount> projectedInventory = projectInventory(structure, input.available());
        InFlightRequestKey inFlightKey = new InFlightRequestKey(
                compiledKey,
                input.requestedAmount(),
                input.quantityMode(),
                projectedInventory,
                limits.maxScheduleStates(),
                limits.planningBudgetMs(),
                SOLVE_STRATEGY_VERSION);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> projectedMutable = new Object2ObjectLinkedOpenHashMap<>();
        projectedInventory.forEach(amount -> projectedMutable.put(amount.key(), amount.amount()));
        Map<AEKey, BigInteger> projectedMap = Object2ObjectMaps.unmodifiable(projectedMutable);
        TrinityComputationValue<TrinityAlgorithmResult<TrinityCraftingPlan>> solved = this.cache.computeInline(
                input.gridScope(),
                TrinityComputationNamespace.REQUEST_IN_FLIGHT,
                input.graph().revision(),
                inFlightKey,
                () -> TrinityCachedComputation.transientValue(solveWithFallback(
                        structure,
                        input,
                        projectedMap,
                        limits,
                        session)));
        PlanningCachePath path = !compiled.cacheHit() ? PlanningCachePath.MISS :
                solved.cacheHit() ? PlanningCachePath.IN_FLIGHT_SHARED : PlanningCachePath.STRUCTURE_HIT;
        long planningNanos = requestPlanningNanos(solved.value(), solved.cacheHit(), startedNanos, session);
        return new TrinityPlanningComputationResult(
                withRequestTiming(solved.value(), solved.cacheHit(), planningNanos, session),
                path,
                planningNanos);
    }

    private TrinityAlgorithmResult<TrinityCraftingPlan> solveWithFallback(
                                                                          TrinityCompiledGraph structure,
                                                                          TrinityPlanningInput input,
                                                                          Map<AEKey, BigInteger> projectedInventory,
                                                                          TrinityPlanningLimits limits,
                                                                          TrinityPlanningSession session) {
        Optional<TrinityPlanningControl> optimizationControl = session.optimizationControl();
        if (optimizationControl.isPresent()) {
            TrinityAlgorithmResult<TrinityCraftingPlan> optimized = this.pipeline.solve(
                    structure,
                    input.graph().revision(),
                    input.requestedAmount(),
                    input.quantityMode(),
                    projectedInventory,
                    limits,
                    TrinityPlanningMode.OPTIMAL,
                    optimizationControl.orElseThrow());
            if (optimized.successful() ||
                    optimized.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_TIMEOUT) {
                return optimized;
            }
        }
        return this.pipeline.solve(
                structure,
                input.graph().revision(),
                input.requestedAmount(),
                input.quantityMode(),
                projectedInventory,
                limits,
                TrinityPlanningMode.FIRST_FEASIBLE,
                session.feasibilityControl());
    }

    private TrinityAlgorithmResult<TrinityCraftingPlan> withRequestTiming(
                                                                          TrinityAlgorithmResult<TrinityCraftingPlan> result,
                                                                          boolean sharedInFlight,
                                                                          long planningNanos,
                                                                          TrinityPlanningSession session) {
        if (!result.successful()) {
            return result;
        }
        TrinityCraftingPlan selectedPlan = result.value();
        TrinityPlanningStatistics statistics = selectedPlan.statistics();
        long mipNanos = sharedInFlight ? 0L : session.mipNanos();
        int solverPasses = sharedInFlight ? 0 : session.solverPasses();
        int solverModels = sharedInFlight ? 0 : session.solverModels();
        int jointStates = sharedInFlight ? 0 : session.jointStates();
        int routeStates = sharedInFlight ? 0 : session.routeStates();
        TrinityPlanningStatistics requestStatistics = statistics.withRequestMetrics(
                planningNanos,
                planningNanos,
                mipNanos,
                solverPasses,
                solverModels,
                jointStates,
                routeStates);
        return TrinityAlgorithmResult.success(selectedPlan.withPlanningStatistics(requestStatistics));
    }

    private long requestPlanningNanos(
                                      TrinityAlgorithmResult<TrinityCraftingPlan> result,
                                      boolean sharedInFlight,
                                      long startedNanos,
                                      TrinityPlanningSession session) {
        long elapsedNanos = elapsedSince(startedNanos);
        if (!result.successful() || sharedInFlight) {
            return elapsedNanos;
        }
        return Math.max(elapsedNanos, session.mipNanos());
    }

    private long elapsedSince(long startedNanos) {
        return Math.max(0L, this.nanoClock.getAsLong() - startedNanos);
    }

    private static void validateInput(TrinityPlanningInput input) {
        if (input == null) {
            throw new IllegalArgumentException("A Trinity planning computation requires an input");
        }
    }

    private static <V> TrinityCachedComputation<TrinityAlgorithmResult<V>> cacheSuccessful(
                                                                                           TrinityAlgorithmResult<V> result) {
        return result.successful() ?
                TrinityCachedComputation.cacheable(result) :
                TrinityCachedComputation.transientValue(result);
    }

    private static List<InventoryAmount> projectInventory(
                                                          TrinityCompiledGraph compiled,
                                                          Map<AEKey, BigInteger> available) {
        ObjectArrayList<InventoryAmount> projected = new ObjectArrayList<>();
        for (AEKey key : compiled.relevantInventoryKeys()) {
            BigInteger amount = available.get(key);
            if (amount != null && amount.signum() > 0) {
                projected.add(new InventoryAmount(key, amount));
            }
        }
        return ObjectLists.unmodifiable(projected);
    }

    private record ReachableGraphKey(AEKey target) {}

    private record CompiledGraphKey(
                                    AEKey target,
                                    List<TrinityPatternIdentity> patternIdentities,
                                    int maxBindingVariants,
                                    int maxSccKeys) {

        private CompiledGraphKey {
            patternIdentities = Collections.unmodifiableList(patternIdentities);
        }
    }

    private record InFlightRequestKey(
                                      CompiledGraphKey compiledGraph,
                                      BigInteger requestedAmount,
                                      CraftingQuantityMode quantityMode,
                                      List<InventoryAmount> relevantInventory,
                                      int maxScheduleStates,
                                      int planningBudgetMs,
                                      int strategyVersion) {}

    private record InventoryAmount(AEKey key, BigInteger amount) {}
}
