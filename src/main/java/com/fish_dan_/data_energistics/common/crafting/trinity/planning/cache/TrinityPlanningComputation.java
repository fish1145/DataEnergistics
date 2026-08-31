package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningSession;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof.TrinityCycleUnitProof;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.proof.TrinityAcyclicRouteFamily;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.proof.TrinityAcyclicRouteHint;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityCompiledGraph;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityCompiledGraphProofView;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanningPipeline;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanningStatistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.math.BigInteger;
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
    private static final int STRUCTURE_VERSION = 3;
    private static final int PATTERN_EXPANSION_VERSION = 1;
    private static final int DAG_ROUTE_PROOF_VERSION = 1;
    private static final int CYCLE_UNIT_PROOF_VERSION = 1;
    private static final int MIP_TEMPLATE_VERSION = 1;

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
        CacheTrace cacheTrace = new CacheTrace();
        TrinityComputationValue<TrinityCraftingGraphSnapshot> reachable = this.cache.computeInline(
                input.gridScope(),
                TrinityComputationNamespace.REACHABLE_GRAPH,
                input.graph().revision(),
                new ReachableGraphKey(input.target()),
                () -> TrinityCachedComputation.cacheable(input.graph().reachableSubgraph(input.target())));
        CompiledGraphKey compiledKey = new CompiledGraphKey(
                input.target(),
                reachable.value().patterns().stream().map(TrinityCraftingGraphPattern::identity).toList(),
                STRUCTURE_VERSION);
        TrinityComputationValue<TrinityAlgorithmResult<TrinityCompiledGraph>> compiled = this.cache.computeInline(
                input.gridScope(),
                TrinityComputationNamespace.TARGET_STRUCTURE,
                TrinityComputationCache.SEMANTIC_REVISION,
                compiledKey,
                () -> cacheSuccessful(compileStructure(
                        input.gridScope(),
                        reachable.value(),
                        input.target(),
                        limits,
                        feasibilityControl,
                        cacheTrace)));
        cacheTrace.targetStructureHit = compiled.cacheHit();
        if (!compiled.value().successful()) {
            long planningNanos = elapsedSince(startedNanos);
            return new TrinityPlanningComputationResult(
                    TrinityAlgorithmResult.failure(compiled.value().diagnostic()),
                    structurePath(reachable.cacheHit(), cacheTrace),
                    planningNanos,
                    cacheTrace.snapshot(false));
        }

        TrinityCompiledGraph structure = compiled.value().value();
        if (compiled.cacheHit()) {
            cacheTrace.recordEmbeddedProofs(structure);
        }
        Optional<TrinityPlanningDiagnostic> limitFailure = structuralLimitFailure(structure, limits);
        if (limitFailure.isPresent()) {
            long planningNanos = elapsedSince(startedNanos);
            return new TrinityPlanningComputationResult(
                    TrinityAlgorithmResult.failure(limitFailure.orElseThrow()),
                    structurePath(reachable.cacheHit(), cacheTrace),
                    planningNanos,
                    cacheTrace.snapshot(false));
        }
        TrinityCompiledGraphProofView requestStructure = attachRouteHints(input.gridScope(), structure, cacheTrace);
        List<InventoryAmount> projectedInventory = projectInventory(requestStructure.structure(), input.available());
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
                        requestStructure,
                        input,
                        projectedMap,
                        limits,
                        session)));
        if (solved.value().successful()) {
            publishRouteHints(input.gridScope(), requestStructure.structure(), solved.value().value());
        }
        PlanningCachePath path = solved.cacheHit() ? PlanningCachePath.IN_FLIGHT_SHARED :
                structurePath(reachable.cacheHit(), cacheTrace);
        long planningNanos = requestPlanningNanos(solved.value(), solved.cacheHit(), startedNanos, session);
        return new TrinityPlanningComputationResult(
                withRequestTiming(solved.value(), solved.cacheHit(), planningNanos, session),
                path,
                planningNanos,
                cacheTrace.snapshot(solved.cacheHit()));
    }

    private TrinityAlgorithmResult<TrinityCompiledGraph> compileStructure(
                                                                          long gridScope,
                                                                          TrinityCraftingGraphSnapshot reachable,
                                                                          AEKey target,
                                                                          TrinityPlanningLimits limits,
                                                                          TrinityPlanningControl control,
                                                                          CacheTrace cacheTrace)
                                                                                                 throws InterruptedException, ExecutionException {
        ObjectArrayList<TrinityPatternVariant> expanded = new ObjectArrayList<>();
        int totalVariants = 0;
        for (TrinityCraftingGraphPattern pattern : reachable.patterns()) {
            TrinityComputationValue<TrinityAlgorithmResult<List<TrinityPatternVariant>>> patternExpansion = this.cache
                    .computeInline(
                            gridScope,
                            TrinityComputationNamespace.PATTERN_EXPANSION,
                            TrinityComputationCache.SEMANTIC_REVISION,
                            new PatternExpansionKey(pattern.identity(), PATTERN_EXPANSION_VERSION),
                            () -> cacheSuccessful(this.pipeline.expandPattern(
                                    pattern,
                                    limits.maxBindingVariants(),
                                    control)));
            cacheTrace.recordPatternExpansion(patternExpansion.cacheHit());
            if (!patternExpansion.value().successful()) {
                return TrinityAlgorithmResult.failure(patternExpansion.value().diagnostic());
            }
            int patternVariants = patternExpansion.value().value().size();
            if (patternVariants > limits.maxBindingVariants() - totalVariants) {
                return TrinityAlgorithmResult.failure(variantLimit(
                        pattern.identity(),
                        limits.maxBindingVariants(),
                        (long) totalVariants + patternVariants));
            }
            totalVariants = Math.addExact(totalVariants, patternVariants);
            expanded.addAll(patternExpansion.value().value());
        }
        TrinityAlgorithmResult<TrinityCompiledGraph> compiled = this.pipeline.compileExpanded(
                reachable,
                target,
                ObjectLists.unmodifiable(expanded),
                limits.maxSccKeys(),
                control);
        return compiled.successful() ?
                TrinityAlgorithmResult.success(attachStructuralProofs(gridScope, compiled.value(), cacheTrace)) :
                compiled;
    }

    private TrinityCompiledGraph attachStructuralProofs(
                                                        long gridScope,
                                                        TrinityCompiledGraph compiled,
                                                        CacheTrace cacheTrace)
                                                                               throws InterruptedException, ExecutionException {
        Object2ObjectLinkedOpenHashMap<AEKey, TrinityAcyclicRouteFamily> routeFamilies = new Object2ObjectLinkedOpenHashMap<>();
        for (Map.Entry<AEKey, List<TrinityPatternVariant>> indexed : compiled.topology()
                .variantsByOutputKey()
                .entrySet()) {
            Integer componentIndex = compiled.topology().componentByKey().get(indexed.getKey());
            if (componentIndex == null || compiled.topology().components().get(componentIndex).cyclic()) {
                continue;
            }
            TrinityComputationValue<TrinityAcyclicRouteFamily> family = this.cache.computeInline(
                    gridScope,
                    TrinityComputationNamespace.DAG_ROUTE_PROOF,
                    TrinityComputationCache.SEMANTIC_REVISION,
                    new RouteFamilyKey(indexed.getKey(), indexed.getValue(), DAG_ROUTE_PROOF_VERSION),
                    () -> TrinityCachedComputation.cacheable(TrinityAcyclicRouteFamily.create(
                            indexed.getKey(),
                            indexed.getValue())));
            cacheTrace.recordDagRouteProof(family.cacheHit());
            routeFamilies.put(indexed.getKey(), family.value());
        }

        Object2ObjectLinkedOpenHashMap<AEKey, TrinityCycleUnitProof> cycleUnitProofs = new Object2ObjectLinkedOpenHashMap<>();
        Int2ObjectOpenHashMap<TrinityMipCoefficientTemplate> mipTemplates = new Int2ObjectOpenHashMap<>();
        for (TrinityStronglyConnectedComponent component : compiled.topology().components()) {
            if (!component.cyclic()) {
                continue;
            }
            ComponentSemanticKey componentKey = new ComponentSemanticKey(component.keys(), component.cycleVariants());
            TrinityComputationValue<TrinityMipCoefficientTemplate> template = this.cache.computeInline(
                    gridScope,
                    TrinityComputationNamespace.MIP_COEFFICIENT_TEMPLATE,
                    TrinityComputationCache.SEMANTIC_REVISION,
                    new MipTemplateKey(componentKey, MIP_TEMPLATE_VERSION),
                    () -> TrinityCachedComputation.cacheable(TrinityMipCoefficientTemplate.create(
                            component.cycleVariants(),
                            component.keys())));
            cacheTrace.recordMipTemplate(template.cacheHit());
            mipTemplates.put(component.index(), template.value());
            for (AEKey reservoir : component.keys()) {
                TrinityComputationValue<Optional<TrinityCycleUnitProof>> proof = this.cache.computeInline(
                        gridScope,
                        TrinityComputationNamespace.CYCLE_UNIT_PROOF,
                        TrinityComputationCache.SEMANTIC_REVISION,
                        new CycleUnitProofKey(componentKey, reservoir, CYCLE_UNIT_PROOF_VERSION),
                        () -> cacheOptional(TrinityCycleUnitProof.derive(component, reservoir)));
                cacheTrace.recordCycleUnitProof(proof.cacheHit() && proof.value().isPresent());
                proof.value().ifPresent(value -> cycleUnitProofs.put(reservoir, value));
            }
        }
        return compiled.withStructuralProofs(
                Object2ObjectMaps.unmodifiable(routeFamilies),
                Object2ObjectMaps.unmodifiable(cycleUnitProofs),
                mipTemplates);
    }

    private TrinityCompiledGraphProofView attachRouteHints(
                                                           long gridScope,
                                                           TrinityCompiledGraph compiled,
                                                           CacheTrace cacheTrace) {
        Object2ObjectLinkedOpenHashMap<AEKey, TrinityAcyclicRouteHint> hints = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityAcyclicRouteFamily family : compiled.routeFamilies().values()) {
            if (family.provedUniqueProducer().isPresent()) {
                continue;
            }
            Optional<TrinityAcyclicRouteHint> cached = this.cache.getIfPresent(
                    gridScope,
                    TrinityComputationNamespace.DAG_ROUTE_HINT,
                    TrinityComputationCache.SEMANTIC_REVISION,
                    new RouteHintKey(family.output(), family.candidates(), DAG_ROUTE_PROOF_VERSION));
            if (cached.isPresent()) {
                cacheTrace.routeHintHits++;
                hints.put(family.output(), cached.orElseThrow());
            }
        }
        return new TrinityCompiledGraphProofView(compiled, Object2ObjectMaps.unmodifiable(hints));
    }

    private void publishRouteHints(
                                   long gridScope,
                                   TrinityCompiledGraph compiled,
                                   TrinityCraftingPlan plan)
                                                             throws InterruptedException, ExecutionException {
        for (TrinityAcyclicRouteFamily family : compiled.routeFamilies().values()) {
            if (family.provedUniqueProducer().isPresent()) {
                continue;
            }
            ObjectArrayList<TrinityPatternIdentity> selected = new ObjectArrayList<>();
            family.candidates().stream()
                    .map(TrinityPatternVariant::patternIdentity)
                    .distinct()
                    .filter(plan.patternFirings()::containsKey)
                    .forEach(selected::add);
            if (selected.isEmpty()) {
                continue;
            }
            TrinityAcyclicRouteHint hint = new TrinityAcyclicRouteHint(
                    family.output(),
                    ObjectLists.unmodifiable(selected));
            this.cache.publishIfAbsent(
                    gridScope,
                    TrinityComputationNamespace.DAG_ROUTE_HINT,
                    TrinityComputationCache.SEMANTIC_REVISION,
                    new RouteHintKey(family.output(), family.candidates(), DAG_ROUTE_PROOF_VERSION),
                    hint);
        }
    }

    private static Optional<TrinityPlanningDiagnostic> structuralLimitFailure(
                                                                              TrinityCompiledGraph structure,
                                                                              TrinityPlanningLimits limits) {
        if (structure.expandedVariantCount() > limits.maxBindingVariants()) {
            return Optional.of(variantLimit(
                    structure.patternIdentities().getLast(),
                    limits.maxBindingVariants(),
                    structure.expandedVariantCount()));
        }
        return structure.topology().components().stream()
                .filter(component -> component.keys().size() > limits.maxSccKeys())
                .findFirst()
                .map(component -> new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.SCC_KEY_LIMIT,
                        Component.translatable("gui.data_energistics.trinity_planning.diagnostic.scc_key_limit"),
                        Map.of(
                                "limit", Integer.toString(limits.maxSccKeys()),
                                "required", Integer.toString(component.keys().size()))));
    }

    private static TrinityPlanningDiagnostic variantLimit(
                                                          TrinityPatternIdentity pattern,
                                                          int limit,
                                                          long required) {
        return new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.VARIANT_LIMIT,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic.variant_limit"),
                Map.of(
                        "limit", Integer.toString(limit),
                        "required", Long.toString(required),
                        "pattern", pattern.publicationEncoding()));
    }

    private static PlanningCachePath structurePath(boolean reachableHit, CacheTrace trace) {
        if (trace.routeHintHits > 0) {
            return PlanningCachePath.ROUTE_HINT_HIT;
        }
        if (trace.dagRouteProofHits > 0 && trace.cycleUnitProofHits > 0) {
            return PlanningCachePath.MIXED_PROOF_HIT;
        }
        if (trace.cycleUnitProofHits > 0) {
            return PlanningCachePath.CYCLE_UNIT_HIT;
        }
        if (trace.dagRouteProofHits > 0) {
            return PlanningCachePath.ROUTE_PROOF_HIT;
        }
        return reachableHit || trace.anyHit() ? PlanningCachePath.STRUCTURE_HIT : PlanningCachePath.MISS;
    }

    private TrinityAlgorithmResult<TrinityCraftingPlan> solveWithFallback(
                                                                          TrinityCompiledGraphProofView structure,
                                                                          TrinityPlanningInput input,
                                                                          Map<AEKey, BigInteger> projectedInventory,
                                                                          TrinityPlanningLimits limits,
                                                                          TrinityPlanningSession session) {
        Optional<TrinityPlanningControl> optimizationControl = session.optimizationControl();
        if (optimizationControl.isPresent()) {
            TrinityAlgorithmResult<TrinityCraftingPlan> optimized = this.pipeline.solve(
                    structure.structure(),
                    structure.routeHints(),
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
                structure.structure(),
                structure.routeHints(),
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

    private static <V> TrinityCachedComputation<Optional<V>> cacheOptional(Optional<V> value) {
        return value.isPresent() ?
                TrinityCachedComputation.cacheable(value) :
                TrinityCachedComputation.transientValue(value);
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
                                    int structureVersion) {}

    private record PatternExpansionKey(TrinityPatternIdentity identity, int expansionVersion) {}

    private record RouteFamilyKey(
                                  AEKey output,
                                  List<TrinityPatternVariant> candidates,
                                  int proofVersion) {}

    private record RouteHintKey(
                                AEKey output,
                                List<TrinityPatternVariant> candidates,
                                int hintVersion) {}

    private record ComponentSemanticKey(
                                        List<AEKey> keys,
                                        List<TrinityPatternVariant> variants) {}

    private record CycleUnitProofKey(
                                     ComponentSemanticKey component,
                                     AEKey reservoir,
                                     int proofVersion) {}

    private record MipTemplateKey(ComponentSemanticKey component, int templateVersion) {}

    private record InFlightRequestKey(
                                      CompiledGraphKey compiledGraph,
                                      BigInteger requestedAmount,
                                      CraftingQuantityMode quantityMode,
                                      List<InventoryAmount> relevantInventory,
                                      int maxScheduleStates,
                                      int planningBudgetMs,
                                      int strategyVersion) {}

    private record InventoryAmount(AEKey key, BigInteger amount) {}

    private static final class CacheTrace {

        private int patternExpansionHits;
        private int patternExpansionMisses;
        private int dagRouteProofHits;
        private int cycleUnitProofHits;
        private int mipTemplateHits;
        private int routeHintHits;
        private boolean targetStructureHit;

        private void recordPatternExpansion(boolean hit) {
            if (hit) {
                this.patternExpansionHits++;
            } else {
                this.patternExpansionMisses++;
            }
        }

        private boolean anyHit() {
            return this.targetStructureHit || this.patternExpansionHits > 0 || this.dagRouteProofHits > 0 ||
                    this.cycleUnitProofHits > 0 || this.mipTemplateHits > 0 || this.routeHintHits > 0;
        }

        private void recordDagRouteProof(boolean hit) {
            if (hit) {
                this.dagRouteProofHits++;
            }
        }

        private void recordCycleUnitProof(boolean hit) {
            if (hit) {
                this.cycleUnitProofHits++;
            }
        }

        private void recordMipTemplate(boolean hit) {
            if (hit) {
                this.mipTemplateHits++;
            }
        }

        private void recordEmbeddedProofs(TrinityCompiledGraph compiled) {
            this.dagRouteProofHits = Math.addExact(this.dagRouteProofHits, compiled.routeFamilies().size());
            this.cycleUnitProofHits = Math.addExact(this.cycleUnitProofHits, compiled.cycleUnitProofs().size());
            this.mipTemplateHits = Math.addExact(this.mipTemplateHits, compiled.cycleMipTemplates().size());
        }

        private TrinityPlanningCacheStatistics snapshot(boolean inFlightShared) {
            return new TrinityPlanningCacheStatistics(
                    this.patternExpansionHits,
                    this.patternExpansionMisses,
                    this.targetStructureHit,
                    this.dagRouteProofHits,
                    this.routeHintHits,
                    this.cycleUnitProofHits,
                    this.mipTemplateHits,
                    inFlightShared);
        }
    }
}
