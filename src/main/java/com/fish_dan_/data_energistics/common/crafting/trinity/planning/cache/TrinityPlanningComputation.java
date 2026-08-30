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
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanningStatistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Executes the three-level target planning path on a shared server-lifetime cache.
 * <p>
 * Exact key implementation for reachable graph, compiled structure, and solved dynamic plan caching.
 */
public final class TrinityPlanningComputation {

    private static final int SOLVE_STRATEGY_VERSION = 1;
    private static final int OPTIMAL_PLAN_REFERENCES_PER_GRID = 256;

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
    private final OptimalPlanIndex optimalPlanIndex = new OptimalPlanIndex(OPTIMAL_PLAN_REFERENCES_PER_GRID);

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
        this.optimalPlanIndex.invalidateRevision(input.gridScope(), input.graph().revision());
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
                () -> cached(this.pipeline.compile(
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
        SolvedPlanKey solvedKey = new SolvedPlanKey(
                compiledKey,
                input.requestedAmount(),
                input.quantityMode(),
                projectedInventory,
                limits.maxScheduleStates(),
                limits.planningBudgetMs(),
                SOLVE_STRATEGY_VERSION);
        Map<AEKey, BigInteger> projectedMap = projectedInventory.stream()
                .collect(Collectors.toUnmodifiableMap(InventoryAmount::key, InventoryAmount::amount));
        OptimalPlanFamilyKey familyKey = new OptimalPlanFamilyKey(
                input.graph().revision(),
                compiledKey,
                input.requestedAmount(),
                input.quantityMode(),
                limits.maxScheduleStates(),
                SOLVE_STRATEGY_VERSION);
        Optional<TrinityCraftingPlan> equivalent = this.optimalPlanIndex.find(
                input.gridScope(),
                familyKey,
                projectedInventory,
                projectedMap,
                input.target(),
                input.requestedAmount(),
                input.quantityMode());
        if (equivalent.isPresent()) {
            long planningNanos = elapsedSince(startedNanos);
            TrinityAlgorithmResult<TrinityCraftingPlan> result = withRequestTiming(
                    TrinityAlgorithmResult.success(equivalent.orElseThrow()),
                    true,
                    planningNanos);
            return new TrinityPlanningComputationResult(
                    result,
                    PlanningCachePath.PROVEN_EQUIVALENT_HIT,
                    planningNanos);
        }
        TrinityComputationValue<TrinityAlgorithmResult<TrinityCraftingPlan>> solved = this.cache.computeInline(
                input.gridScope(),
                TrinityComputationNamespace.SOLVED_PLAN,
                input.graph().revision(),
                solvedKey,
                () -> cached(solveWithFallback(
                        structure,
                        input,
                        projectedMap,
                        limits,
                        session)));
        if (solved.value().successful() &&
                solved.value().value().statistics().quality() == TrinityPlanQuality.PROVED_OPTIMAL) {
            this.optimalPlanIndex.publish(
                    input.gridScope(),
                    familyKey,
                    projectedInventory,
                    solved.value().value());
        }
        PlanningCachePath path = !compiled.cacheHit() ? PlanningCachePath.MISS :
                solved.cacheHit() ? PlanningCachePath.EXACT_HIT : PlanningCachePath.STRUCTURE_HIT;
        long planningNanos = requestPlanningNanos(solved.value(), solved.cacheHit(), startedNanos);
        return new TrinityPlanningComputationResult(
                withRequestTiming(solved.value(), solved.cacheHit(), planningNanos),
                path,
                planningNanos);
    }

    /**
     * Clears request-derived optimal-plan certificates for a closed Grid scope.
     */
    public void clearGrid(long gridScope) {
        this.optimalPlanIndex.clearGrid(gridScope);
    }

    /**
     * Clears every request-derived certificate during server shutdown.
     */
    public void clear() {
        this.optimalPlanIndex.clear();
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
                                                                          boolean solvedFromCache,
                                                                          long planningNanos) {
        if (!result.successful()) {
            return result;
        }
        TrinityCraftingPlan cachedPlan = result.value();
        TrinityPlanningStatistics cachedStatistics = cachedPlan.statistics();
        long mipNanos = solvedFromCache ? 0L : cachedStatistics.mipNanos();
        TrinityPlanningStatistics requestStatistics = cachedStatistics.withRequestTiming(
                planningNanos,
                planningNanos,
                mipNanos);
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
                                 int maxScheduleStates,
                                 int planningBudgetMs,
                                 int strategyVersion) {

        private SolvedPlanKey {
            relevantInventory = List.copyOf(relevantInventory);
        }
    }

    private record OptimalPlanFamilyKey(
                                        long graphRevision,
                                        CompiledGraphKey compiledGraph,
                                        BigInteger requestedAmount,
                                        CraftingQuantityMode quantityMode,
                                        int maxScheduleStates,
                                        int strategyVersion) {}

    private record OptimalPlanReferenceKey(
                                           OptimalPlanFamilyKey family,
                                           List<InventoryAmount> referenceInventory) {

        private OptimalPlanReferenceKey {
            referenceInventory = List.copyOf(referenceInventory);
        }
    }

    /**
     * Bounded per-Grid LRU of proved-optimal plans and the inventory domains in which they were established.
     */
    private static final class OptimalPlanIndex {

        private final int perGridLimit;
        private final Map<Long, LinkedHashMap<OptimalPlanReferenceKey, TrinityCraftingPlan>> partitions = new LinkedHashMap<>();

        private OptimalPlanIndex(int perGridLimit) {
            this.perGridLimit = perGridLimit;
        }

        private synchronized Optional<TrinityCraftingPlan> find(
                                                                long gridScope,
                                                                OptimalPlanFamilyKey family,
                                                                List<InventoryAmount> inventory,
                                                                Map<AEKey, BigInteger> available,
                                                                AEKey target,
                                                                BigInteger requestedAmount,
                                                                CraftingQuantityMode quantityMode) {
            LinkedHashMap<OptimalPlanReferenceKey, TrinityCraftingPlan> partition = this.partitions.get(gridScope);
            if (partition == null) {
                return Optional.empty();
            }
            OptimalPlanReferenceKey matched = null;
            for (Map.Entry<OptimalPlanReferenceKey, TrinityCraftingPlan> entry : partition.entrySet()) {
                OptimalPlanReferenceKey reference = entry.getKey();
                if (reference.family().equals(family) &&
                        !reference.referenceInventory().equals(inventory) &&
                        isEquivalentDomain(
                                reference.referenceInventory(),
                                available,
                                entry.getValue(),
                                target,
                                requestedAmount,
                                quantityMode)) {
                    matched = reference;
                    break;
                }
            }
            return matched == null ? Optional.empty() : Optional.of(partition.get(matched));
        }

        private synchronized void publish(
                                          long gridScope,
                                          OptimalPlanFamilyKey family,
                                          List<InventoryAmount> inventory,
                                          TrinityCraftingPlan plan) {
            LinkedHashMap<OptimalPlanReferenceKey, TrinityCraftingPlan> partition = this.partitions.computeIfAbsent(
                    gridScope,
                    ignored -> new LinkedHashMap<>(16, 0.75F, true));
            partition.put(new OptimalPlanReferenceKey(family, inventory), plan);
            while (partition.size() > this.perGridLimit) {
                OptimalPlanReferenceKey eldest = partition.keySet().iterator().next();
                partition.remove(eldest);
            }
        }

        private synchronized void invalidateRevision(long gridScope, long revision) {
            LinkedHashMap<OptimalPlanReferenceKey, TrinityCraftingPlan> partition = this.partitions.get(gridScope);
            if (partition == null) {
                return;
            }
            partition.entrySet().removeIf(entry -> entry.getKey().family().graphRevision() != revision);
            if (partition.isEmpty()) {
                this.partitions.remove(gridScope);
            }
        }

        private synchronized void clearGrid(long gridScope) {
            this.partitions.remove(gridScope);
        }

        private synchronized void clear() {
            this.partitions.clear();
        }

        private static boolean isEquivalentDomain(
                                                  List<InventoryAmount> referenceInventory,
                                                  Map<AEKey, BigInteger> available,
                                                  TrinityCraftingPlan plan,
                                                  AEKey target,
                                                  BigInteger requestedAmount,
                                                  CraftingQuantityMode quantityMode) {
            LinkedHashMap<AEKey, BigInteger> reference = new LinkedHashMap<>();
            referenceInventory.forEach(amount -> reference.put(amount.key(), amount.amount()));
            for (Map.Entry<AEKey, BigInteger> current : available.entrySet()) {
                if (current.getValue().compareTo(reference.getOrDefault(current.getKey(), BigInteger.ZERO)) > 0) {
                    return false;
                }
            }
            if (plan.initialExpectedInputs().entrySet().stream().anyMatch(entry -> available
                    .getOrDefault(entry.getKey(), BigInteger.ZERO)
                    .compareTo(entry.getValue()) < 0)) {
                return false;
            }
            BigInteger targetDelta = plan.targetNetChange().getOrDefault(target, BigInteger.ZERO);
            return quantityMode == CraftingQuantityMode.NET_NEW ?
                    targetDelta.compareTo(requestedAmount) >= 0 :
                    available.getOrDefault(target, BigInteger.ZERO).add(targetDelta).compareTo(requestedAmount) >= 0;
        }
    }

    private record InventoryAmount(AEKey key, BigInteger amount) {}
}
