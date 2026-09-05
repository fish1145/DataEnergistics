package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningSession;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof.TrinityCycleUnitProofIndex;
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
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanningStatistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressMeasure;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressPhase;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressReporter;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

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
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Executes target planning through retained structure and transient request-coalescing layers.
 * <p>
 * Quantity and inventory remain request-local and no completed executable plan is retained.
 */
public final class TrinityPlanningComputation {

    private static final int SOLVE_STRATEGY_VERSION = 3;
    private static final int STRUCTURE_VERSION = 4;
    private static final int PATTERN_EXPANSION_VERSION = 1;
    private static final int DAG_ROUTE_PROOF_VERSION = 1;
    private static final int CYCLE_UNIT_PROOF_VERSION = 2;
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
        this.cache = cache;
        this.pipeline = pipeline;
        this.nanoClock = nanoClock;
    }

    /**
     * Enters the cache layers on the current accepted planner worker without submitting nested work.
     *
     * @param input immutable pure-planning input
     * @return algorithm result and selected cache path
     * @throws InterruptedException when this worker is interrupted while waiting for a shared calculation
     * @throws ExecutionException   when a shared bottom calculation fails
     */
    public TrinityPlanningComputationResult calculate(TrinityPlanningInput input,
                                                      TrinityPlanningProgressReporter progress)
                                                                                                throws InterruptedException, ExecutionException {
        long startedNanos = this.nanoClock.getAsLong();
        this.cache.invalidateRevision(input.gridScope(), input.graph().revision());
        TrinityPlanningLimits limits = input.limits();
        TrinityPlanningSession session = TrinityPlanningSession.create(
                () -> false,
                this.nanoClock,
                TimeUnit.MILLISECONDS.toNanos(limits.planningBudgetMs()),
                progress);
        TrinityPlanningControl feasibilityControl = session.feasibilityControl();
        CacheTrace cacheTrace = new CacheTrace();
        progress.publish(TrinityPlanningProgressSnapshot.withoutUnits(
                TrinityPlanningProgressPhase.REACHABLE_SUBGRAPH,
                TrinityPlanningProgressMeasure.INDETERMINATE));
        TrinityComputationValue<TrinityCraftingGraphSnapshot> reachable = this.cache.computeInline(
                input.gridScope(),
                TrinityComputationNamespace.REACHABLE_GRAPH,
                input.graph().revision(),
                new ReachableGraphKey(input.target(), input.graph().patterns(), input.graph().reusableInputFallbacks()),
                () -> TrinityCachedComputation.cacheable(input.graph().reachableSubgraph(input.target())));
        int patternCount = reachable.value().patterns().size();
        progress.publish(patternCount == 0 ?
                TrinityPlanningProgressSnapshot.withoutUnits(
                        TrinityPlanningProgressPhase.EXPANDING_PATTERNS,
                        TrinityPlanningProgressMeasure.NONE) :
                TrinityPlanningProgressSnapshot.exact(TrinityPlanningProgressPhase.EXPANDING_PATTERNS, 0, patternCount));
        TrinitySameItemPolicy sameItemPolicy = reachable.value().sameItemPolicy(input.target());
        CompiledGraphKey compiledKey = new CompiledGraphKey(
                input.target(),
                reachable.value().patterns(),
                sameItemPolicy,
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
                        cacheTrace,
                        progress)));
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
        TrinityCompiledGraphProofView requestStructure = attachRouteHints(input.gridScope(), structure, cacheTrace, progress);
        int inventoryKeyCount = requestStructure.structure().relevantInventoryKeys().size();
        progress.publish(inventoryKeyCount == 0 ?
                TrinityPlanningProgressSnapshot.withoutUnits(
                        TrinityPlanningProgressPhase.PROJECTING_REQUEST,
                        TrinityPlanningProgressMeasure.NONE) :
                TrinityPlanningProgressSnapshot.exact(TrinityPlanningProgressPhase.PROJECTING_REQUEST, 0, inventoryKeyCount));
        TrinityPlanningInventory projectedInventory = input.inventory()
                .normalized(requestStructure.structure().sameItemPolicy())
                .project(requestStructure.structure().relevantInventoryKeys());
        if (inventoryKeyCount > 0) {
            progress.publish(TrinityPlanningProgressSnapshot.exact(
                    TrinityPlanningProgressPhase.PROJECTING_REQUEST,
                    inventoryKeyCount,
                    inventoryKeyCount));
        }
        InFlightRequestKey inFlightKey = new InFlightRequestKey(
                compiledKey,
                input.requestedAmount(),
                input.quantityMode(),
                projectedInventory,
                limits.maxScheduleStates(),
                limits.planningBudgetMs(),
                SOLVE_STRATEGY_VERSION);
        TrinityComputationValue<TrinityAlgorithmResult<TrinityCraftingPlan>> solved = this.cache.computeInlineObserved(
                input.gridScope(),
                TrinityComputationNamespace.REQUEST_IN_FLIGHT,
                input.graph().revision(),
                inFlightKey,
                cacheHit -> {
                    if (cacheHit) {
                        progress.publish(TrinityPlanningProgressSnapshot.withoutUnits(
                                TrinityPlanningProgressPhase.WAITING_FOR_SHARED_RESULT,
                                TrinityPlanningProgressMeasure.INDETERMINATE));
                    }
                },
                () -> TrinityCachedComputation.transientValue(solveWithFallback(
                        requestStructure,
                        input,
                        projectedInventory,
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
                                                                          CacheTrace cacheTrace,
                                                                          TrinityPlanningProgressReporter progress)
                                                                                                                    throws InterruptedException, ExecutionException {
        ObjectArrayList<TrinityPatternVariant> expanded = new ObjectArrayList<>();
        int totalVariants = 0;
        int completedPatterns = 0;
        for (TrinityCraftingGraphPattern pattern : reachable.patterns()) {
            TrinityComputationValue<TrinityAlgorithmResult<List<TrinityPatternVariant>>> patternExpansion = this.cache
                    .computeInline(
                            gridScope,
                            TrinityComputationNamespace.PATTERN_EXPANSION,
                            TrinityComputationCache.SEMANTIC_REVISION,
                            new PatternExpansionKey(pattern, PATTERN_EXPANSION_VERSION),
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
            completedPatterns = Math.incrementExact(completedPatterns);
            publishExactProgress(
                    progress,
                    TrinityPlanningProgressPhase.EXPANDING_PATTERNS,
                    completedPatterns,
                    reachable.patterns().size());
        }
        progress.publish(TrinityPlanningProgressSnapshot.withoutUnits(
                TrinityPlanningProgressPhase.COMPACTING_VARIANTS,
                TrinityPlanningProgressMeasure.INDETERMINATE));
        TrinityAlgorithmResult<TrinityCompiledGraph> compiled = this.pipeline.compileExpanded(
                reachable,
                target,
                ObjectLists.unmodifiable(expanded),
                limits.maxSccKeys(),
                control);
        if (!compiled.successful()) {
            return compiled;
        }
        progress.publish(TrinityPlanningProgressSnapshot.exact(
                TrinityPlanningProgressPhase.ANALYZING_TOPOLOGY,
                1,
                1));
        return TrinityAlgorithmResult.success(attachStructuralProofs(gridScope, compiled.value(), cacheTrace, progress));
    }

    private TrinityCompiledGraph attachStructuralProofs(
                                                        long gridScope,
                                                        TrinityCompiledGraph compiled,
                                                        CacheTrace cacheTrace,
                                                        TrinityPlanningProgressReporter progress)
                                                                                                  throws InterruptedException, ExecutionException {
        int proofCount = Math.toIntExact(compiled.topology().variantsByOutputKey().entrySet().stream()
                .filter(indexed -> {
                    Integer componentIndex = compiled.topology().componentByKey().get(indexed.getKey());
                    return componentIndex != null && !compiled.topology().components().get(componentIndex).cyclic();
                })
                .count());
        proofCount = Math.addExact(proofCount, Math.multiplyExact(Math.toIntExact(compiled.topology().components().stream()
                .filter(TrinityStronglyConnectedComponent::cyclic)
                .count()), 2));
        progress.publish(proofCount == 0 ?
                TrinityPlanningProgressSnapshot.withoutUnits(
                        TrinityPlanningProgressPhase.BUILDING_STRUCTURAL_PROOFS,
                        TrinityPlanningProgressMeasure.NONE) :
                TrinityPlanningProgressSnapshot.exact(TrinityPlanningProgressPhase.BUILDING_STRUCTURAL_PROOFS, 0, proofCount));
        int completedProofs = 0;
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
            completedProofs = Math.incrementExact(completedProofs);
            publishExactProgress(
                    progress,
                    TrinityPlanningProgressPhase.BUILDING_STRUCTURAL_PROOFS,
                    completedProofs,
                    proofCount);
        }

        ObjectArrayList<TrinityCycleUnitProofIndex> cycleUnitProofs = new ObjectArrayList<>();
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
            completedProofs = Math.incrementExact(completedProofs);
            publishExactProgress(
                    progress,
                    TrinityPlanningProgressPhase.BUILDING_STRUCTURAL_PROOFS,
                    completedProofs,
                    proofCount);
            TrinityComputationValue<TrinityCycleUnitProofIndex> proof = this.cache.computeInline(
                    gridScope,
                    TrinityComputationNamespace.CYCLE_UNIT_PROOF,
                    TrinityComputationCache.SEMANTIC_REVISION,
                    new CycleUnitProofKey(componentKey, CYCLE_UNIT_PROOF_VERSION),
                    () -> {
                        TrinityCycleUnitProofIndex derived = TrinityCycleUnitProofIndex.derive(component);
                        return derived.isEmpty() ?
                                TrinityCachedComputation.transientValue(derived) :
                                TrinityCachedComputation.cacheable(derived);
                    });
            cacheTrace.recordCycleUnitProofs(proof.cacheHit() ? proof.value().uniqueCount() : 0);
            cycleUnitProofs.add(proof.value());
            completedProofs = Math.incrementExact(completedProofs);
            publishExactProgress(
                    progress,
                    TrinityPlanningProgressPhase.BUILDING_STRUCTURAL_PROOFS,
                    completedProofs,
                    proofCount);
        }
        return compiled.withStructuralProofs(
                Object2ObjectMaps.unmodifiable(routeFamilies),
                TrinityCycleUnitProofIndex.merge(cycleUnitProofs),
                mipTemplates);
    }

    private TrinityCompiledGraphProofView attachRouteHints(
                                                           long gridScope,
                                                           TrinityCompiledGraph compiled,
                                                           CacheTrace cacheTrace,
                                                           TrinityPlanningProgressReporter progress) {
        Object2ObjectLinkedOpenHashMap<AEKey, TrinityAcyclicRouteHint> hints = new Object2ObjectLinkedOpenHashMap<>();
        int totalHints = Math.toIntExact(compiled.routeFamilies().values().stream()
                .filter(family -> family.provedUniqueProducer().isEmpty())
                .count());
        progress.publish(totalHints == 0 ?
                TrinityPlanningProgressSnapshot.withoutUnits(
                        TrinityPlanningProgressPhase.LOADING_ROUTE_HINTS,
                        TrinityPlanningProgressMeasure.NONE) :
                TrinityPlanningProgressSnapshot.exact(TrinityPlanningProgressPhase.LOADING_ROUTE_HINTS, 0, totalHints));
        int completedHints = 0;
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
            completedHints = Math.incrementExact(completedHints);
            publishExactProgress(
                    progress,
                    TrinityPlanningProgressPhase.LOADING_ROUTE_HINTS,
                    completedHints,
                    totalHints);
        }
        return new TrinityCompiledGraphProofView(compiled, Object2ObjectMaps.unmodifiable(hints));
    }

    private void publishRouteHints(
                                   long gridScope,
                                   TrinityCompiledGraph compiled,
                                   TrinityCraftingPlan plan) {
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
                                                                          TrinityPlanningInventory projectedInventory,
                                                                          TrinityPlanningLimits limits,
                                                                          TrinityPlanningSession session) {
        Optional<TrinityPlanningControl> boundedControl = session.boundedControl();
        if (boundedControl.isPresent()) {
            session.beginSolving(TrinityPlanningProgressPhase.SOLVING_BOUNDED, limits.maxScheduleStates());
            TrinityAlgorithmResult<TrinityCraftingPlan> bounded = solveFirstFeasible(
                    structure,
                    input,
                    projectedInventory,
                    limits,
                    boundedControl.orElseThrow());
            if (bounded.successful() || !retryableFeasibilityStop(bounded.diagnostic())) {
                return bounded;
            }
        }
        session.beginSolving(TrinityPlanningProgressPhase.SOLVING_FALLBACK, 0);
        return solveFirstFeasible(
                structure,
                input,
                projectedInventory,
                limits,
                session.feasibilityControl());
    }

    private static void publishExactProgress(TrinityPlanningProgressReporter progress,
                                             TrinityPlanningProgressPhase phase,
                                             int completed,
                                             int total) {
        if ((completed & 31) == 0 || completed == total) {
            progress.publish(TrinityPlanningProgressSnapshot.exact(phase, completed, total));
        }
    }

    private TrinityAlgorithmResult<TrinityCraftingPlan> solveFirstFeasible(
                                                                           TrinityCompiledGraphProofView structure,
                                                                           TrinityPlanningInput input,
                                                                           TrinityPlanningInventory projectedInventory,
                                                                           TrinityPlanningLimits limits,
                                                                           TrinityPlanningControl control) {
        return this.pipeline.solve(
                structure.structure(),
                structure.routeHints(),
                input.graph().revision(),
                input.requestedAmount(),
                input.quantityMode(),
                projectedInventory,
                limits,
                TrinityPlanningMode.FIRST_FEASIBLE,
                control);
    }

    private static boolean retryableFeasibilityStop(TrinityPlanningDiagnostic diagnostic) {
        return diagnostic.code() == TrinityPlanningDiagnosticCode.MIP_TIMEOUT ||
                diagnostic.code() == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT &&
                        "timeout".equals(diagnostic.metadata().get("reason"));
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

    private static <V> TrinityCachedComputation<TrinityAlgorithmResult<V>> cacheSuccessful(
                                                                                           TrinityAlgorithmResult<V> result) {
        return result.successful() ?
                TrinityCachedComputation.cacheable(result) :
                TrinityCachedComputation.transientValue(result);
    }

    private record ReachableGraphKey(AEKey target, List<TrinityCraftingGraphPattern> patterns,
                                     Map<TrinityPatternIdentity, TrinityPlanningDiagnostic> reusableInputFallbacks) {}

    private record CompiledGraphKey(
                                    AEKey target,
                                    List<TrinityCraftingGraphPattern> patterns,
                                    TrinitySameItemPolicy sameItemPolicy,
                                    int structureVersion) {}

    private record PatternExpansionKey(TrinityCraftingGraphPattern pattern, int expansionVersion) {}

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
                                     int proofVersion) {}

    private record MipTemplateKey(ComponentSemanticKey component, int templateVersion) {}

    private record InFlightRequestKey(
                                      CompiledGraphKey compiledGraph,
                                      BigInteger requestedAmount,
                                      CraftingQuantityMode quantityMode,
                                      TrinityPlanningInventory relevantInventory,
                                      int maxScheduleStates,
                                      int planningBudgetMs,
                                      int strategyVersion) {}

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

        private void recordCycleUnitProofs(int hits) {
            this.cycleUnitProofHits = Math.addExact(this.cycleUnitProofHits, hits);
        }

        private void recordMipTemplate(boolean hit) {
            if (hit) {
                this.mipTemplateHits++;
            }
        }

        private void recordEmbeddedProofs(TrinityCompiledGraph compiled) {
            this.dagRouteProofHits = Math.addExact(this.dagRouteProofHits, compiled.routeFamilies().size());
            this.cycleUnitProofHits = Math.addExact(
                    this.cycleUnitProofHits,
                    compiled.cycleUnitProofs().uniqueCount());
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
