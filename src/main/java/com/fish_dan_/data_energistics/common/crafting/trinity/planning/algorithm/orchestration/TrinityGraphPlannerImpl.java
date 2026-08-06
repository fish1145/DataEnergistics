package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicDemandPropagator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly.TrinityGraphPlanAssembler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly.TrinityGraphPlanAssembly;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand.TrinityGraphDemandAggregator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand.TrinityGraphDemandSolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityGraphTopologyAnalyzer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityPatternVariantExpander;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.TrinityCrafting;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinates graph expansion, topology analysis, demand solving, and final plan assembly at one exact boundary.
 */
final class TrinityGraphPlannerImpl implements TrinityGraphPlanningPipeline {

    private static final String ARITHMETIC_OVERFLOW_KEY = "gui.data_energistics.trinity_planning.diagnostic.arithmetic_overflow";
    private static final String TARGET_ABSENT_KEY = "gui.data_energistics.trinity_planning.diagnostic.target_absent";
    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String TIMEOUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.timeout";

    private final TrinityPatternVariantExpander variantExpander;
    private final TrinityGraphTopologyAnalyzer topologyAnalyzer;
    private final TrinityAcyclicDemandPropagator acyclicDemandPropagator;
    private final TrinityGraphDemandAggregator demandAggregator;
    private final TrinityGraphPlanAssembler planAssembler;

    TrinityGraphPlannerImpl(TrinityPatternVariantExpander variantExpander,
                            TrinityGraphTopologyAnalyzer topologyAnalyzer,
                            TrinityAcyclicDemandPropagator acyclicDemandPropagator,
                            TrinityGraphDemandAggregator demandAggregator,
                            TrinityGraphPlanAssembler planAssembler) {
        this.variantExpander = variantExpander;
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
                                                            Map<AEKey, BigInteger> available,
                                                            TrinityCrafting settings,
                                                            TrinityPlanningControl control) {
        if (snapshot == null || target == null || requestedAmount == null || requestedAmount.signum() <= 0 ||
                quantityMode == null || available == null || settings == null || control == null) {
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
                    settings.maxBindingVariants(),
                    settings.maxSccKeys(),
                    control);
            return compiled.successful() ?
                    solveExact(
                            compiled.value(),
                            snapshot.revision(),
                            requestedAmount,
                            quantityMode,
                            copyAvailable(available),
                            settings,
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
                maxBindingVariants);
        if (!expanded.successful()) {
            return TrinityAlgorithmResult.failure(expanded.diagnostic());
        }
        TrinityAlgorithmResult<TrinityCraftingTopology> analyzed = this.topologyAnalyzer.analyze(
                reachableSnapshot,
                expanded.value(),
                maxSccKeys);
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
                expanded.value(),
                analyzed.value(),
                targetComponent,
                reachableCycle,
                relevantInventoryKeys));
    }

    @Override
    public TrinityAlgorithmResult<TrinityCraftingPlan> solve(
                                                             TrinityCompiledGraph compiled,
                                                             long catalogRevision,
                                                             BigInteger requestedAmount,
                                                             CraftingQuantityMode quantityMode,
                                                             Map<AEKey, BigInteger> available,
                                                             TrinityCrafting settings,
                                                             TrinityPlanningControl control) {
        if (compiled == null || catalogRevision < 0L || requestedAmount == null || requestedAmount.signum() <= 0 ||
                quantityMode == null || available == null || settings == null || control == null) {
            throw new IllegalArgumentException("A Trinity compiled graph solve request is incomplete");
        }
        try {
            return solveExact(
                    compiled,
                    catalogRevision,
                    requestedAmount,
                    quantityMode,
                    copyAvailable(available),
                    settings,
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
                                                                   long catalogRevision,
                                                                   BigInteger requestedAmount,
                                                                   CraftingQuantityMode quantityMode,
                                                                   Map<AEKey, BigInteger> available,
                                                                   TrinityCrafting settings,
                                                                   TrinityPlanningControl control) {
        StopState state = stopState(control);
        if (state == StopState.CANCELLED) {
            return cancelled();
        }
        if (state == StopState.DEADLINE_EXCEEDED) {
            return deadlineExceeded();
        }
        long requestedLong = requestedAmount.longValueExact();
        long startedNanos = System.nanoTime();
        TrinityAlgorithmResult<TrinityGraphPlanAssembly> assembled = compiled.reachableCycle() ?
                solveWithCycles(
                        compiled.topology(),
                        compiled.target(),
                        requestedAmount,
                        quantityMode,
                        available,
                        settings,
                        control) :
                solveAcyclic(
                        compiled.topology(),
                        compiled.variants(),
                        compiled.target(),
                        requestedAmount,
                        quantityMode,
                        available,
                        settings,
                        control);
        if (!assembled.successful()) {
            return TrinityAlgorithmResult.failure(assembled.diagnostic());
        }
        TrinityCraftingPlan plan = this.planAssembler.finalizePlan(
                new TrinityGraphPlanContext(
                        catalogRevision,
                        compiled.target(),
                        requestedAmount,
                        requestedLong,
                        quantityMode,
                        compiled.variants(),
                        compiled.topology(),
                        startedNanos),
                assembled.value());
        return TrinityAlgorithmResult.success(plan);
    }

    private TrinityAlgorithmResult<TrinityGraphPlanAssembly> solveWithCycles(
                                                                             TrinityCraftingTopology topology,
                                                                             AEKey target,
                                                                             BigInteger requestedAmount,
                                                                             CraftingQuantityMode quantityMode,
                                                                             Map<AEKey, BigInteger> available,
                                                                             TrinityCrafting settings,
                                                                             TrinityPlanningControl control) {
        TrinityAlgorithmResult<TrinityGraphDemandSolution> solved = this.demandAggregator.aggregate(
                topology,
                target,
                requestedAmount,
                quantityMode,
                available,
                settings,
                control);
        return solved.successful() ?
                this.planAssembler.assembleDemand(target, topology, solved.value()) :
                TrinityAlgorithmResult.failure(solved.diagnostic());
    }

    private TrinityAlgorithmResult<TrinityGraphPlanAssembly> solveAcyclic(
                                                                          TrinityCraftingTopology topology,
                                                                          List<TrinityPatternVariant> variants,
                                                                          AEKey target,
                                                                          BigInteger requestedAmount,
                                                                          CraftingQuantityMode quantityMode,
                                                                          Map<AEKey, BigInteger> available,
                                                                          TrinityCrafting settings,
                                                                          TrinityPlanningControl control) {
        TrinityAlgorithmResult<TrinityAcyclicPlan> propagated = this.acyclicDemandPropagator.propagate(
                topology,
                variants,
                target,
                requestedAmount,
                quantityMode,
                available,
                settings.maxScheduleStates(),
                control);
        return propagated.successful() ?
                TrinityAlgorithmResult.success(this.planAssembler.assembleAcyclic(propagated.value())) :
                TrinityAlgorithmResult.failure(propagated.diagnostic());
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

    private static Map<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity graph-planning inventory cannot be negative or null");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(copied);
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
