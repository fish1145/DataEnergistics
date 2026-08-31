package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicDemandPropagator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.proof.TrinityAcyclicRouteFamily;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.proof.TrinityAcyclicRouteHint;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly.TrinityGraphPlanAssembler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly.TrinityGraphPlanAssembly;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand.TrinityGraphDemandAggregator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand.TrinityGraphDemandSolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityGraphTopologyAnalyzer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityPatternVariantExpander;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityTransitionEffectCompactor;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Coordinates graph expansion, topology analysis, demand solving, and final plan assembly at one exact boundary.
 */
final class ExactTrinityGraphPlanningPipeline implements TrinityGraphPlanningPipeline {

    private static final String ARITHMETIC_OVERFLOW_KEY = "gui.data_energistics.trinity_planning.diagnostic.arithmetic_overflow";
    private static final String TARGET_ABSENT_KEY = "gui.data_energistics.trinity_planning.diagnostic.target_absent";
    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String TIMEOUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.timeout";

    private final TrinityPatternVariantExpander variantExpander;
    private final TrinityTransitionEffectCompactor effectCompactor;
    private final TrinityGraphTopologyAnalyzer topologyAnalyzer;
    private final TrinityAcyclicDemandPropagator acyclicDemandPropagator;
    private final TrinityGraphDemandAggregator demandAggregator;
    private final TrinityGraphPlanAssembler planAssembler;

    ExactTrinityGraphPlanningPipeline(TrinityPatternVariantExpander variantExpander,
                                      TrinityTransitionEffectCompactor effectCompactor,
                                      TrinityGraphTopologyAnalyzer topologyAnalyzer,
                                      TrinityAcyclicDemandPropagator acyclicDemandPropagator,
                                      TrinityGraphDemandAggregator demandAggregator,
                                      TrinityGraphPlanAssembler planAssembler) {
        this.variantExpander = variantExpander;
        this.effectCompactor = effectCompactor;
        this.topologyAnalyzer = topologyAnalyzer;
        this.acyclicDemandPropagator = acyclicDemandPropagator;
        this.demandAggregator = demandAggregator;
        this.planAssembler = planAssembler;
    }

    @Override
    public TrinityAlgorithmResult<TrinityCraftingPlan> plan(
                                                            TrinityCraftingGraphSnapshot snapshot,
                                                            AEKey target,
                                                            BigInteger requestedAmount,
                                                            CraftingQuantityMode quantityMode,
                                                            TrinityPlanningInventory inventory,
                                                            TrinityPlanningLimits limits,
                                                            TrinityPlanningControl control) {
        if (snapshot == null || target == null || requestedAmount == null || requestedAmount.signum() <= 0 ||
                quantityMode == null || inventory == null || limits == null || control == null) {
            throw new IllegalArgumentException("A Trinity graph planning request is incomplete");
        }
        try {
            StopState initialState = stopState(control);
            if (initialState == StopState.CANCELLED) {
                return cancelled();
            }
            if (initialState == StopState.DEADLINE_EXCEEDED) {
                return deadlineExceeded();
            }
            TrinityCraftingGraphSnapshot reachableSnapshot = snapshot.reachableSubgraph(target);
            TrinityAlgorithmResult<TrinityCompiledGraph> compiled = compileExact(
                    reachableSnapshot,
                    target,
                    limits.maxBindingVariants(),
                    limits.maxSccKeys(),
                    control);
            return compiled.successful() ?
                    solveExact(
                            compiled.value(),
                            Map.of(),
                            snapshot.revision(),
                            requestedAmount,
                            quantityMode,
                            inventory,
                            limits,
                            TrinityPlanningMode.OPTIMAL,
                            control) :
                    TrinityAlgorithmResult.failure(compiled.diagnostic());
        } catch (ArithmeticException exception) {
            return failure(
                    TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW,
                    ARITHMETIC_OVERFLOW_KEY,
                    Map.of("reason", exception.getClass().getSimpleName()));
        }
    }

    @Override
    public TrinityAlgorithmResult<TrinityCompiledGraph> compile(
                                                                TrinityCraftingGraphSnapshot reachableSnapshot,
                                                                AEKey target,
                                                                int maxBindingVariants,
                                                                int maxSccKeys,
                                                                TrinityPlanningControl control) {
        if (reachableSnapshot == null || target == null || maxBindingVariants <= 0 || maxSccKeys <= 0 ||
                control == null) {
            throw new IllegalArgumentException("A Trinity graph compilation request is incomplete");
        }
        try {
            return compileExact(reachableSnapshot, target, maxBindingVariants, maxSccKeys, control);
        } catch (ArithmeticException exception) {
            return failure(
                    TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW,
                    ARITHMETIC_OVERFLOW_KEY,
                    Map.of("reason", exception.getClass().getSimpleName()));
        }
    }

    @Override
    public TrinityAlgorithmResult<List<TrinityPatternVariant>> expandPattern(
                                                                             TrinityCraftingGraphPattern pattern,
                                                                             int maxBindingVariants,
                                                                             TrinityPlanningControl control) {
        return this.variantExpander.expandPattern(pattern, maxBindingVariants, control);
    }

    @Override
    public TrinityAlgorithmResult<TrinityCompiledGraph> compileExpanded(
                                                                        TrinityCraftingGraphSnapshot reachableSnapshot,
                                                                        AEKey target,
                                                                        List<TrinityPatternVariant> expandedVariants,
                                                                        int maxSccKeys,
                                                                        TrinityPlanningControl control) {
        try {
            return compileExpandedExact(reachableSnapshot, target, expandedVariants, maxSccKeys, control);
        } catch (ArithmeticException exception) {
            return failure(
                    TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW,
                    ARITHMETIC_OVERFLOW_KEY,
                    Map.of("reason", exception.getClass().getSimpleName()));
        }
    }

    private TrinityAlgorithmResult<TrinityCompiledGraph> compileExact(
                                                                      TrinityCraftingGraphSnapshot reachableSnapshot,
                                                                      AEKey target,
                                                                      int maxBindingVariants,
                                                                      int maxSccKeys,
                                                                      TrinityPlanningControl control) {
        StopState state = stopState(control);
        if (state == StopState.CANCELLED) {
            return cancelled();
        }
        if (state == StopState.DEADLINE_EXCEEDED) {
            return deadlineExceeded();
        }
        if (reachableSnapshot.patterns().isEmpty()) {
            return failure(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    TARGET_ABSENT_KEY,
                    Map.of("target", target.toString()));
        }
        TrinityAlgorithmResult<List<TrinityPatternVariant>> expanded = this.variantExpander.expand(
                reachableSnapshot,
                maxBindingVariants,
                control);
        if (!expanded.successful()) {
            return TrinityAlgorithmResult.failure(expanded.diagnostic());
        }
        return compileExpandedExact(reachableSnapshot, target, expanded.value(), maxSccKeys, control);
    }

    private TrinityAlgorithmResult<TrinityCompiledGraph> compileExpandedExact(
                                                                              TrinityCraftingGraphSnapshot reachableSnapshot,
                                                                              AEKey target,
                                                                              List<TrinityPatternVariant> expandedVariants,
                                                                              int maxSccKeys,
                                                                              TrinityPlanningControl control) {
        StopState state = stopState(control);
        if (state == StopState.CANCELLED) {
            return cancelled();
        }
        if (state == StopState.DEADLINE_EXCEEDED) {
            return deadlineExceeded();
        }
        List<TrinityPatternVariant> compacted = this.effectCompactor.compact(expandedVariants);
        TrinityAlgorithmResult<TrinityCraftingTopology> analyzed = this.topologyAnalyzer.analyze(
                reachableSnapshot,
                compacted,
                maxSccKeys,
                control);
        if (!analyzed.successful()) {
            return TrinityAlgorithmResult.failure(analyzed.diagnostic());
        }
        Integer targetComponent = analyzed.value().componentByKey().get(target);
        if (targetComponent == null) {
            return failure(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    TARGET_ABSENT_KEY,
                    Map.of("target", target.toString()));
        }

        boolean reachableCycle = hasReachableCycle(analyzed.value(), targetComponent);
        List<AEKey> relevantInventoryKeys = reachableSnapshot.keys().stream()
                .filter(analyzed.value().componentByKey()::containsKey)
                .toList();
        return TrinityAlgorithmResult.success(new TrinityCompiledGraph(
                target,
                reachableSnapshot.patterns().stream().map(TrinityCraftingGraphPattern::identity).toList(),
                expandedVariants.size(),
                compacted,
                analyzed.value(),
                targetComponent,
                reachableCycle,
                relevantInventoryKeys,
                Map.of(),
                Map.of(),
                Int2ObjectMaps.emptyMap()));
    }

    @Override
    public TrinityAlgorithmResult<TrinityCraftingPlan> solve(
                                                             TrinityCompiledGraph compiled,
                                                             Map<AEKey, TrinityAcyclicRouteHint> routeHints,
                                                             long catalogRevision,
                                                             BigInteger requestedAmount,
                                                             CraftingQuantityMode quantityMode,
                                                             TrinityPlanningInventory inventory,
                                                             TrinityPlanningLimits limits,
                                                             TrinityPlanningMode mode,
                                                             TrinityPlanningControl control) {
        if (compiled == null || catalogRevision < 0L || requestedAmount == null || requestedAmount.signum() <= 0 ||
                quantityMode == null || inventory == null || limits == null || mode == null || control == null) {
            throw new IllegalArgumentException("A Trinity compiled graph solve request is incomplete");
        }
        try {
            return solveExact(
                    compiled,
                    routeHints,
                    catalogRevision,
                    requestedAmount,
                    quantityMode,
                    inventory,
                    limits,
                    mode,
                    control);
        } catch (ArithmeticException exception) {
            return failure(
                    TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW,
                    ARITHMETIC_OVERFLOW_KEY,
                    Map.of("reason", exception.getClass().getSimpleName()));
        }
    }

    private TrinityAlgorithmResult<TrinityCraftingPlan> solveExact(
                                                                   TrinityCompiledGraph compiled,
                                                                   Map<AEKey, TrinityAcyclicRouteHint> routeHints,
                                                                   long catalogRevision,
                                                                   BigInteger requestedAmount,
                                                                   CraftingQuantityMode quantityMode,
                                                                   TrinityPlanningInventory inventory,
                                                                   TrinityPlanningLimits limits,
                                                                   TrinityPlanningMode mode,
                                                                   TrinityPlanningControl control) {
        StopState state = stopState(control);
        if (state == StopState.CANCELLED) {
            return cancelled();
        }
        if (state == StopState.DEADLINE_EXCEEDED) {
            return deadlineExceeded();
        }
        long startedNanos = System.nanoTime();
        TrinityAlgorithmResult<TrinityGraphPlanAssembly> assembled = compiled.reachableCycle() ?
                solveWithCycles(
                        compiled,
                        requestedAmount,
                        quantityMode,
                        inventory,
                        limits,
                        mode,
                        control) :
                solveAcyclic(
                        compiled.topology(),
                        compiled.variants(),
                        compiled.routeFamilies(),
                        routeHints,
                        compiled.target(),
                        requestedAmount,
                        quantityMode,
                        inventory,
                        limits,
                        mode,
                        control);
        if (!assembled.successful()) {
            return TrinityAlgorithmResult.failure(assembled.diagnostic());
        }
        TrinityCraftingPlan plan = this.planAssembler.finalizePlan(
                new TrinityGraphPlanContext(
                        catalogRevision,
                        compiled.target(),
                        requestedAmount,
                        quantityMode,
                        compiled.variants(),
                        compiled.topology(),
                        startedNanos),
                assembled.value());
        return TrinityAlgorithmResult.success(plan);
    }

    private TrinityAlgorithmResult<TrinityGraphPlanAssembly> solveWithCycles(
                                                                             TrinityCompiledGraph compiled,
                                                                             BigInteger requestedAmount,
                                                                             CraftingQuantityMode quantityMode,
                                                                             TrinityPlanningInventory inventory,
                                                                             TrinityPlanningLimits limits,
                                                                             TrinityPlanningMode mode,
                                                                             TrinityPlanningControl control) {
        TrinityAlgorithmResult<TrinityGraphDemandSolution> solved = this.demandAggregator.aggregate(
                compiled.topology(),
                compiled.target(),
                requestedAmount,
                quantityMode,
                inventory,
                limits,
                mode,
                control,
                compiled.cycleUnitProofs(),
                compiled.cycleMipTemplates());
        return solved.successful() ?
                this.planAssembler.assembleDemand(compiled.target(), compiled.topology(), solved.value()) :
                TrinityAlgorithmResult.failure(solved.diagnostic());
    }

    private TrinityAlgorithmResult<TrinityGraphPlanAssembly> solveAcyclic(
                                                                          TrinityCraftingTopology topology,
                                                                          List<TrinityPatternVariant> variants,
                                                                          Map<AEKey, TrinityAcyclicRouteFamily> routeFamilies,
                                                                          Map<AEKey, TrinityAcyclicRouteHint> routeHints,
                                                                          AEKey target,
                                                                          BigInteger requestedAmount,
                                                                          CraftingQuantityMode quantityMode,
                                                                          TrinityPlanningInventory inventory,
                                                                          TrinityPlanningLimits limits,
                                                                          TrinityPlanningMode mode,
                                                                          TrinityPlanningControl control) {
        int recordedRouteStates = control.recordedRouteStates();
        TrinityAlgorithmResult<TrinityAcyclicPlan> propagated = this.acyclicDemandPropagator.propagate(
                topology,
                variants,
                routeFamilies,
                routeHints,
                target,
                requestedAmount,
                quantityMode,
                inventory,
                limits.maxScheduleStates(),
                mode,
                control);
        if (!propagated.successful()) {
            return TrinityAlgorithmResult.failure(propagated.diagnostic());
        }
        int optimizerStates = Math.subtractExact(control.recordedRouteStates(), recordedRouteStates);
        if (optimizerStates == 0) {
            control.recordRouteStates(propagated.value().statesVisited());
        }
        return TrinityAlgorithmResult.success(this.planAssembler.assembleAcyclic(propagated.value()));
    }

    private static boolean hasReachableCycle(TrinityCraftingTopology topology, int targetComponent) {
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        HashSet<Integer> visited = new HashSet<>();
        pending.add(targetComponent);
        while (!pending.isEmpty()) {
            int componentIndex = pending.removeFirst();
            if (!visited.add(componentIndex)) {
                continue;
            }
            TrinityStronglyConnectedComponent component = topology.components().get(componentIndex);
            if (component.cyclic()) {
                return true;
            }
            pending.addAll(component.predecessorIndexes());
        }
        return false;
    }

    private static StopState stopState(TrinityPlanningControl control) {
        if (control.cancellationRequested()) {
            return StopState.CANCELLED;
        }
        return control.deadlineExceeded() ? StopState.DEADLINE_EXCEEDED : StopState.RUNNING;
    }

    private static <T> TrinityAlgorithmResult<T> cancelled() {
        return failure(
                TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                CANCELLED_KEY,
                Map.of());
    }

    private static <T> TrinityAlgorithmResult<T> deadlineExceeded() {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                TIMEOUT_KEY,
                Map.of("phase", "graph"));
    }

    private static <T> TrinityAlgorithmResult<T> failure(
                                                         TrinityPlanningDiagnosticCode code,
                                                         String translationKey,
                                                         Map<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.translatable(translationKey),
                metadata));
    }

    private enum StopState {
        RUNNING,
        CANCELLED,
        DEADLINE_EXCEEDED
    }
}
