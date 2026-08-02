package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityDeterministicComponentPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityDeterministicComponentPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityDeterministicCyclePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityDeterministicCycleSequence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityMipCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityMixedIntegerCycleSolver;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicDemandPropagator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityGraphTopologyAnalyzer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityPatternVariantExpander;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlanImpl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanByteEstimateInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanByteEstimator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanningStatistics;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reverse condensation planner that retains aggregate {@link BigInteger} amounts until the final AE2 boundaries.
 */
final class TrinityGraphPlannerImpl implements TrinityGraphPlanner {

    private final TrinityPatternVariantExpander variantExpander;
    private final TrinityGraphTopologyAnalyzer topologyAnalyzer;
    private final TrinityAcyclicDemandPropagator acyclicDemandPropagator;
    private final TrinityDeterministicCycleSequence deterministicCycleSequence;
    private final TrinityDeterministicCyclePlanner deterministicCyclePlanner;
    private final TrinityDeterministicComponentPlanner deterministicComponentPlanner;
    private final TrinityMixedIntegerCycleSolver mixedIntegerCycleSolver;
    private final TrinityPlanByteEstimator byteEstimator;

    TrinityGraphPlannerImpl(TrinityPatternVariantExpander variantExpander,
                            TrinityGraphTopologyAnalyzer topologyAnalyzer,
                            TrinityAcyclicDemandPropagator acyclicDemandPropagator,
                            TrinityDeterministicCycleSequence deterministicCycleSequence,
                            TrinityDeterministicCyclePlanner deterministicCyclePlanner,
                            TrinityDeterministicComponentPlanner deterministicComponentPlanner,
                            TrinityMixedIntegerCycleSolver mixedIntegerCycleSolver,
                            TrinityPlanByteEstimator byteEstimator) {
        this.variantExpander = variantExpander;
        this.topologyAnalyzer = topologyAnalyzer;
        this.acyclicDemandPropagator = acyclicDemandPropagator;
        this.deterministicCycleSequence = deterministicCycleSequence;
        this.deterministicCyclePlanner = deterministicCyclePlanner;
        this.deterministicComponentPlanner = deterministicComponentPlanner;
        this.mixedIntegerCycleSolver = mixedIntegerCycleSolver;
        this.byteEstimator = byteEstimator;
    }

    @Override
    public TrinityAlgorithmResult<TrinityCraftingPlan> plan(
                                                            TrinityCraftingGraphSnapshot snapshot,
                                                            AEKey target,
                                                            BigInteger requestedAmount,
                                                            CraftingQuantityMode quantityMode,
                                                            Map<AEKey, BigInteger> available,
                                                            TrinityCraftingConfig.Settings settings,
                                                            TrinityPlanningControl control) {
        if (snapshot == null || target == null || requestedAmount == null || requestedAmount.signum() <= 0 ||
                quantityMode == null || available == null || settings == null || control == null) {
            throw new IllegalArgumentException("A Trinity graph planning request is incomplete");
        }
        try {
            return planExact(
                    snapshot,
                    target,
                    requestedAmount,
                    quantityMode,
                    copyAvailable(available),
                    settings,
                    control);
        } catch (ArithmeticException exception) {
            return failure(
                    TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW,
                    "A Trinity plan cannot be represented by an exact AE2 long boundary",
                    Map.of("reason", exception.getClass().getSimpleName()));
        }
    }

    private TrinityAlgorithmResult<TrinityCraftingPlan> planExact(
                                                                  TrinityCraftingGraphSnapshot snapshot,
                                                                  AEKey target,
                                                                  BigInteger requestedAmount,
                                                                  CraftingQuantityMode quantityMode,
                                                                  Map<AEKey, BigInteger> available,
                                                                  TrinityCraftingConfig.Settings settings,
                                                                  TrinityPlanningControl control) {
        long requestedLong = requestedAmount.longValueExact();
        StopState initialState = stopState(control);
        if (initialState == StopState.CANCELLED) {
            return cancelled();
        }
        if (initialState == StopState.DEADLINE_EXCEEDED) {
            return deadlineExceeded();
        }

        long startedNanos = System.nanoTime();
        TrinityAlgorithmResult<List<TrinityPatternVariant>> expanded = this.variantExpander.expand(
                snapshot,
                settings.maxBindingVariants());
        if (!expanded.successful()) {
            return TrinityAlgorithmResult.failure(expanded.diagnostic());
        }
        TrinityAlgorithmResult<TrinityCraftingTopology> analyzed = this.topologyAnalyzer.analyze(
                snapshot,
                expanded.value(),
                settings.maxSccKeys());
        if (!analyzed.successful()) {
            return TrinityAlgorithmResult.failure(analyzed.diagnostic());
        }
        Integer targetComponent = analyzed.value().componentByKey().get(target);
        if (targetComponent == null) {
            return failure(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    "The requested key is absent from the Trinity crafting graph",
                    Map.of("target", target.toString()));
        }

        TrinityAlgorithmResult<PlanAssembly> assembled;
        if (hasReachableCycle(analyzed.value(), targetComponent)) {
            PlanningAccumulator accumulator = new PlanningAccumulator(
                    analyzed.value(),
                    expanded.value(),
                    target,
                    requestedAmount,
                    quantityMode,
                    available,
                    settings,
                    control);
            assembled = accumulator.solve();
        } else {
            TrinityAlgorithmResult<TrinityAcyclicPlan> propagated = this.acyclicDemandPropagator.propagate(
                    analyzed.value(),
                    expanded.value(),
                    target,
                    requestedAmount,
                    quantityMode,
                    available,
                    settings.maxScheduleStates(),
                    control);
            assembled = propagated.successful() ?
                    TrinityAlgorithmResult.success(assembleAcyclic(propagated.value())) :
                    TrinityAlgorithmResult.failure(propagated.diagnostic());
        }
        if (!assembled.successful()) {
            return TrinityAlgorithmResult.failure(assembled.diagnostic());
        }
        PlanAssembly assembly = assembled.value();
        verifyLongBoundaries(
                requestedAmount,
                assembly.initialInputs(),
                assembly.patternFirings(),
                assembly.netChange(),
                assembly.stackRequests());
        long bytes = this.byteEstimator.estimate(new TrinityPlanByteEstimateInput(
                assembly.stackRequests(),
                sum(assembly.patternFirings()),
                BigInteger.valueOf(assembly.stages().size())));
        long elapsedNanos = Math.max(
                assembly.mipNanos(),
                Math.max(0L, System.nanoTime() - startedNanos));
        TrinityPlanningStatistics statistics = new TrinityPlanningStatistics(
                analyzed.value().components().size(),
                expanded.value().size(),
                elapsedNanos,
                assembly.mipNanos(),
                assembly.scheduleStates());
        TrinityCraftingPlan plan = TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(target, requestedLong))
                .bytes(bytes)
                .multiplePaths(hasMultiplePaths(expanded.value()))
                .catalogRevision(snapshot.revision())
                .quantityMode(quantityMode)
                .initialExpectedInputs(assembly.initialInputs())
                .patternFirings(assembly.patternFirings())
                .stages(assembly.stages())
                .stageOrder(assembly.stageOrder())
                .cycleRepeatBlocks(assembly.repeatBlocks())
                .minimumSeed(assembly.minimumSeed())
                .targetNetChange(assembly.netChange())
                .emittedItems(Map.of())
                .diagnostics(List.of())
                .statistics(statistics)
                .build();
        return TrinityAlgorithmResult.success(plan);
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

    private static PlanAssembly assembleAcyclic(TrinityAcyclicPlan acyclicPlan) {
        ArrayList<TrinityPlanStage> stages = new ArrayList<>(acyclicPlan.executionOrder().size());
        ArrayList<Integer> stageOrder = new ArrayList<>(acyclicPlan.executionOrder().size());
        LinkedHashMap<TrinityPatternIdentity, BigInteger> patternFirings = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> stackRequests = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : acyclicPlan.executionOrder()) {
            int stageIndex = stages.size();
            Set<Integer> dependencies = stageIndex == 0 ?
                    Set.of() :
                    Set.of(stageIndex - 1);
            stages.add(stage(
                    stageIndex,
                    false,
                    dependencies,
                    firing.variant(),
                    firing.count(),
                    false));
            stageOrder.add(stageIndex);
            mergePatternFiring(patternFirings, firing.variant(), firing.count());
            mergeScaled(stackRequests, firing.variant().inputs(), firing.count());
            mergeScaled(stackRequests, firing.variant().outputs(), firing.count());
        }
        return new PlanAssembly(
                acyclicPlan.externalInputs(),
                Collections.unmodifiableMap(patternFirings),
                List.copyOf(stages),
                List.copyOf(stageOrder),
                List.of(),
                Map.of(),
                acyclicPlan.netChange(),
                Collections.unmodifiableMap(stackRequests),
                acyclicPlan.statesVisited(),
                0L);
    }

    private final class PlanningAccumulator {

        private final TrinityCraftingTopology topology;
        private final List<TrinityPatternVariant> variants;
        private final AEKey target;
        private final CraftingQuantityMode quantityMode;
        private final LinkedHashMap<AEKey, BigInteger> inventory;
        private final TrinityCraftingConfig.Settings settings;
        private final TrinityPlanningControl control;
        private final Map<Integer, Integer> topologicalPositions;
        private final RouteSearchBudget routeSearchBudget;
        private final LinkedHashMap<AEKey, BigInteger> demand = new LinkedHashMap<>();
        private final LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>();
        private final LinkedHashMap<TrinityPatternVariant, AcyclicFiring> acyclicFirings = new LinkedHashMap<>();
        private final ArrayList<CycleSolution> cycleSolutions = new ArrayList<>();
        private final LinkedHashMap<Integer, LinkedHashMap<AEKey, BigInteger>> cycleOutputDemands = new LinkedHashMap<>();
        private int scheduleStates;
        private long mipNanos;

        private PlanningAccumulator(
                                    TrinityCraftingTopology topology,
                                    List<TrinityPatternVariant> variants,
                                    AEKey target,
                                    BigInteger requestedAmount,
                                    CraftingQuantityMode quantityMode,
                                    Map<AEKey, BigInteger> available,
                                    TrinityCraftingConfig.Settings settings,
                                    TrinityPlanningControl control) {
            this.topology = topology;
            this.variants = variants;
            this.target = target;
            this.quantityMode = quantityMode;
            this.inventory = new LinkedHashMap<>(available);
            this.settings = settings;
            this.control = control;
            this.topologicalPositions = topologicalPositions(topology);
            this.routeSearchBudget = new RouteSearchBudget(settings.maxScheduleStates());
            this.demand.put(target, requestedAmount);
        }

        private PlanningAccumulator(PlanningAccumulator source) {
            this.topology = source.topology;
            this.variants = source.variants;
            this.target = source.target;
            this.quantityMode = source.quantityMode;
            this.inventory = new LinkedHashMap<>(source.inventory);
            this.settings = source.settings;
            this.control = source.control;
            this.topologicalPositions = source.topologicalPositions;
            this.routeSearchBudget = source.routeSearchBudget;
            this.demand.putAll(source.demand);
            this.initialInputs.putAll(source.initialInputs);
            this.acyclicFirings.putAll(source.acyclicFirings);
            this.cycleSolutions.addAll(source.cycleSolutions);
            source.cycleOutputDemands.forEach((component, amounts) -> this.cycleOutputDemands.put(component, new LinkedHashMap<>(amounts)));
            this.scheduleStates = source.scheduleStates;
            this.mipNanos = source.mipNanos;
        }

        private TrinityAlgorithmResult<PlanAssembly> solve() {
            return solveComponent(this.topology.topologicalOrder().size() - 1);
        }

        private TrinityAlgorithmResult<PlanAssembly> solveComponent(int position) {
            StopState state = stopState(this.control);
            if (state == StopState.CANCELLED) {
                return cancelled();
            }
            if (state == StopState.DEADLINE_EXCEEDED) {
                return deadlineExceeded();
            }
            if (position < 0) {
                return completePlan();
            }
            int componentIndex = this.topology.topologicalOrder().get(position);
            TrinityStronglyConnectedComponent component = this.topology.components().get(componentIndex);
            if (!component.cyclic()) {
                return processAcyclicComponent(component, 0, position);
            }
            TrinityAlgorithmResult<Optional<PreparedCycle>> prepared = prepareCycleComponent(component);
            if (!prepared.successful()) {
                return TrinityAlgorithmResult.failure(prepared.diagnostic());
            }
            if (prepared.value().isEmpty()) {
                return solveComponent(position - 1);
            }
            PreparedCycle cycle = prepared.value().orElseThrow();
            return satisfyCycleInputs(
                    component,
                    cycle,
                    List.copyOf(cycle.solution().initialInputs().entrySet()),
                    0,
                    position);
        }

        private TrinityAlgorithmResult<PlanAssembly> completePlan() {
            if (this.cycleOutputDemands.values().stream()
                    .flatMap(amounts -> amounts.values().stream())
                    .anyMatch(amount -> amount.signum() > 0)) {
                return failure(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        "A Trinity boundary-output demand outlived its cyclic owner",
                        Map.of());
            }
            for (Map.Entry<AEKey, BigInteger> remaining : this.demand.entrySet()) {
                if (remaining.getValue().signum() > 0) {
                    return insufficient(remaining.getKey(), remaining.getValue());
                }
            }
            return assemble();
        }

        private TrinityAlgorithmResult<PlanAssembly> processAcyclicComponent(
                                                                             TrinityStronglyConnectedComponent component,
                                                                             int keyIndex,
                                                                             int position) {
            if (keyIndex >= component.keys().size()) {
                return solveComponent(position - 1);
            }
            AEKey key = component.keys().get(keyIndex);
            BigInteger required = positiveDemand(key);
            boolean forceFinalProduction = key.equals(this.target) &&
                    this.quantityMode == CraftingQuantityMode.FINAL_TOTAL;
            if (required.signum() <= 0 && !forceFinalProduction) {
                return processAcyclicComponent(component, keyIndex + 1, position);
            }

            boolean netNewTarget = key.equals(this.target) &&
                    this.quantityMode == CraftingQuantityMode.NET_NEW;
            if (!netNewTarget && required.signum() > 0) {
                BigInteger reserved = reserveFromInventory(key, required);
                merge(this.demand, key, reserved.negate());
            }
            BigInteger missing = positiveDemand(key);
            if (missing.signum() <= 0 && !forceFinalProduction) {
                return processAcyclicComponent(component, keyIndex + 1, position);
            }

            List<TrinityPatternVariant> candidates = producersFor(key, component.index(), false);
            if (candidates.isEmpty()) {
                return insufficient(key, missing.max(BigInteger.ONE));
            }
            TrinityPlanningDiagnostic lastDiagnostic = null;
            for (TrinityPatternVariant selected : candidates) {
                if (!this.routeSearchBudget.consume()) {
                    return routeSearchLimit();
                }
                PlanningAccumulator branch = new PlanningAccumulator(this);
                BigInteger outputDemand = missing.signum() > 0 ? missing : BigInteger.ONE;
                TrinityAlgorithmResult<StepSuccess> applied = branch.applyProducerChoice(
                        component,
                        key,
                        selected,
                        outputDemand,
                        false);
                if (!applied.successful()) {
                    lastDiagnostic = applied.diagnostic();
                    continue;
                }
                TrinityAlgorithmResult<PlanAssembly> result = branch.processAcyclicComponent(
                        component,
                        keyIndex + 1,
                        position);
                if (result.successful()) {
                    return result;
                }
                lastDiagnostic = result.diagnostic();
            }
            return TrinityAlgorithmResult.failure(lastDiagnostic == null ?
                    insufficient(key, missing.max(BigInteger.ONE)).diagnostic() :
                    lastDiagnostic);
        }

        private TrinityAlgorithmResult<Optional<PreparedCycle>> prepareCycleComponent(
                                                                                      TrinityStronglyConnectedComponent component) {
            LinkedHashMap<AEKey, BigInteger> internalRequirements = new LinkedHashMap<>();
            boolean requiresCycle = !this.cycleOutputDemands
                    .getOrDefault(component.index(), new LinkedHashMap<>())
                    .isEmpty();
            for (AEKey key : component.keys()) {
                BigInteger required = positiveDemand(key);
                if (required.signum() <= 0) {
                    continue;
                }
                internalRequirements.put(key, required);
                if (key.equals(this.target) ||
                        this.inventory.getOrDefault(key, BigInteger.ZERO).compareTo(required) < 0) {
                    requiresCycle = true;
                }
            }
            if (!requiresCycle) {
                internalRequirements.forEach((key, required) -> {
                    BigInteger reserved = reserveFromInventory(key, required);
                    merge(this.demand, key, reserved.negate());
                });
                return TrinityAlgorithmResult.success(Optional.empty());
            }

            LinkedHashMap<AEKey, BigInteger> finalBalances = new LinkedHashMap<>();
            LinkedHashMap<AEKey, BigInteger> requiredNetChanges = new LinkedHashMap<>();
            for (Map.Entry<AEKey, BigInteger> requirement : internalRequirements.entrySet()) {
                AEKey key = requirement.getKey();
                BigInteger required = requirement.getValue();
                if (key.equals(this.target) && this.quantityMode == CraftingQuantityMode.NET_NEW) {
                    merge(requiredNetChanges, key, required);
                    continue;
                }
                finalBalances.put(key, required);
                BigInteger shortage = required
                        .subtract(this.inventory.getOrDefault(key, BigInteger.ZERO))
                        .max(BigInteger.ZERO);
                if (key.equals(this.target) && this.quantityMode == CraftingQuantityMode.FINAL_TOTAL) {
                    shortage = shortage.max(BigInteger.ONE);
                }
                if (shortage.signum() > 0) {
                    requiredNetChanges.put(key, shortage);
                }
            }
            this.cycleOutputDemands
                    .getOrDefault(component.index(), new LinkedHashMap<>())
                    .forEach((key, amount) -> merge(requiredNetChanges, key, amount));
            TrinityCycleDemand cycleDemand = new TrinityCycleDemand(finalBalances, requiredNetChanges);
            Set<AEKey> producibleInputs = producibleInputs(component);
            TrinityAlgorithmResult<CycleSolution> solved = solveCycle(
                    component,
                    cycleDemand,
                    producibleInputs);
            if (!solved.successful()) {
                return TrinityAlgorithmResult.failure(solved.diagnostic());
            }
            return TrinityAlgorithmResult.success(Optional.of(new PreparedCycle(
                    solved.value(),
                    internalRequirements,
                    producibleInputs)));
        }

        private TrinityAlgorithmResult<CycleSolution> solveCycle(
                                                                 TrinityStronglyConnectedComponent component,
                                                                 TrinityCycleDemand demand,
                                                                 Set<AEKey> producibleInputs) {
            Optional<ScalarCycleDemand> scalar = scalarDemand(component, demand);
            if (scalar.isPresent()) {
                ScalarCycleDemand request = scalar.orElseThrow();
                Optional<List<TrinityVariantFiring>> deterministicOrder = deterministicCycleSequence.resolve(
                        component,
                        request.target(),
                        this.inventory);
                if (deterministicOrder.isPresent()) {
                    TrinityAlgorithmResult<TrinityCyclePlan> deterministic = deterministicCyclePlanner.plan(
                            deterministicOrder.orElseThrow(),
                            request.target(),
                            request.amount(),
                            request.quantityMode(),
                            this.inventory,
                            this.settings.maxScheduleStates(),
                            this.control);
                    if (deterministic.successful()) {
                        TrinityCyclePlan plan = deterministic.value();
                        return TrinityAlgorithmResult.success(new CycleSolution(
                                component.index(),
                                plan.oneCycleOrder(),
                                plan.repetitions(),
                                plan.minimumSeed(),
                                plan.initialInputs(),
                                plan.netChange(),
                                plan.schedule().statesVisited(),
                                0L));
                    }
                    if (deterministic.diagnostic().code() != TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT ||
                            producibleInputs.isEmpty()) {
                        return TrinityAlgorithmResult.failure(deterministic.diagnostic());
                    }
                }
            }

            long componentStartedNanos = System.nanoTime();
            Optional<TrinityAlgorithmResult<TrinityDeterministicComponentPlan>> deterministicComponent =
                    deterministicComponentPlanner.plan(
                            component,
                            demand,
                            this.inventory,
                            producibleInputs,
                            this.settings.maxScheduleStates(),
                            this.control);
            long componentNanos = Math.max(0L, System.nanoTime() - componentStartedNanos);
            if (deterministicComponent.isPresent()) {
                TrinityAlgorithmResult<TrinityDeterministicComponentPlan> deterministic =
                        deterministicComponent.orElseThrow();
                if (!deterministic.successful()) {
                    return TrinityAlgorithmResult.failure(deterministic.diagnostic());
                }
                TrinityDeterministicComponentPlan plan = deterministic.value();
                return TrinityAlgorithmResult.success(new CycleSolution(
                        component.index(),
                        plan.schedule().batches(),
                        BigInteger.ONE,
                        plan.minimumSeed(),
                        plan.initialInputs(),
                        plan.netChange(),
                        plan.schedule().statesVisited(),
                        componentNanos));
            }

            TrinityAlgorithmResult<TrinityMipCyclePlan> mip = mixedIntegerCycleSolver.solve(
                    component,
                    demand,
                    this.inventory,
                    producibleInputs,
                    this.settings.maxScheduleStates(),
                    this.control);
            if (!mip.successful()) {
                return TrinityAlgorithmResult.failure(mip.diagnostic());
            }
            TrinityMipCyclePlan plan = mip.value();
            LinkedHashMap<AEKey, BigInteger> prefix = maximumAmounts(
                    plan.minimumSeed(),
                    plan.externalInputs());
            return TrinityAlgorithmResult.success(new CycleSolution(
                    component.index(),
                    plan.schedule().batches(),
                    BigInteger.ONE,
                    prefix,
                    plan.initialInputs(),
                    plan.netChange(),
                    plan.schedule().statesVisited(),
                    Math.addExact(componentNanos, plan.solverNanos())));
        }

        private TrinityAlgorithmResult<PlanAssembly> satisfyCycleInputs(
                                                                        TrinityStronglyConnectedComponent component,
                                                                        PreparedCycle prepared,
                                                                        List<Map.Entry<AEKey, BigInteger>> inputs,
                                                                        int inputIndex,
                                                                        int position) {
            if (inputIndex >= inputs.size()) {
                return finishCycleComponent(component, prepared, position);
            }
            Map.Entry<AEKey, BigInteger> input = inputs.get(inputIndex);
            AEKey key = input.getKey();
            BigInteger required = input.getValue();
            BigInteger available = this.inventory.getOrDefault(key, BigInteger.ZERO);
            if (available.compareTo(required) >= 0) {
                reserveFromInventory(key, required);
                return satisfyCycleInputs(component, prepared, inputs, inputIndex + 1, position);
            }
            if (!prepared.producibleInputs().contains(key)) {
                BigInteger missing = required.subtract(reserveFromInventory(key, required));
                return insufficient(key, missing);
            }
            int inputComponent = this.topology.componentByKey().get(key);
            int cyclePosition = this.topologicalPositions.get(component.index());
            int inputPosition = this.topologicalPositions.get(inputComponent);
            if (inputPosition < cyclePosition) {
                merge(this.demand, key, required);
                return satisfyCycleInputs(component, prepared, inputs, inputIndex + 1, position);
            }
            if (inputComponent != component.index()) {
                return failure(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        "A Trinity cycle input producer violates condensation order",
                        Map.of("key", key.toString()));
            }
            BigInteger missing = required.subtract(reserveFromInventory(key, required));
            List<TrinityPatternVariant> candidates = producersFor(key, component.index(), true);
            if (candidates.isEmpty()) {
                return insufficient(key, missing);
            }
            TrinityPlanningDiagnostic lastDiagnostic = null;
            for (TrinityPatternVariant selected : candidates) {
                if (!this.routeSearchBudget.consume()) {
                    return routeSearchLimit();
                }
                PlanningAccumulator branch = new PlanningAccumulator(this);
                TrinityAlgorithmResult<StepSuccess> applied = branch.applyProducerChoice(
                        component,
                        key,
                        selected,
                        missing,
                        true);
                if (!applied.successful()) {
                    lastDiagnostic = applied.diagnostic();
                    continue;
                }
                TrinityAlgorithmResult<PlanAssembly> result = branch.satisfyCycleInputs(
                        component,
                        prepared,
                        inputs,
                        inputIndex + 1,
                        position);
                if (result.successful()) {
                    return result;
                }
                lastDiagnostic = result.diagnostic();
            }
            return TrinityAlgorithmResult.failure(lastDiagnostic == null ?
                    insufficient(key, missing).diagnostic() :
                    lastDiagnostic);
        }

        private TrinityAlgorithmResult<PlanAssembly> finishCycleComponent(
                                                                          TrinityStronglyConnectedComponent component,
                                                                          PreparedCycle prepared,
                                                                          int position) {
            CycleSolution solution = prepared.solution();
            this.cycleSolutions.add(solution);
            this.scheduleStates = Math.addExact(this.scheduleStates, solution.scheduleStates());
            this.mipNanos = Math.addExact(this.mipNanos, solution.mipNanos());
            prepared.internalRequirements().keySet().forEach(key -> this.demand.put(key, BigInteger.ZERO));
            this.cycleOutputDemands.remove(component.index());
            return solveComponent(position - 1);
        }

        private TrinityAlgorithmResult<StepSuccess> applyProducerChoice(
                                                                        TrinityStronglyConnectedComponent outputComponent,
                                                                        AEKey key,
                                                                        TrinityPatternVariant selected,
                                                                        BigInteger outputDemand,
                                                                        boolean crossBoundaryInput) {
            Integer cyclicOwner = this.topology.cyclicOwnerByVariant().get(selected);
            if (cyclicOwner != null && cyclicOwner != outputComponent.index()) {
                int ownerPosition = this.topologicalPositions.get(cyclicOwner);
                int outputPosition = this.topologicalPositions.get(outputComponent.index());
                if (ownerPosition >= outputPosition) {
                    return failure(
                            TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                            "A Trinity feedback transition owner violates condensation order",
                            Map.of("key", key.toString()));
                }
                this.cycleOutputDemands
                        .computeIfAbsent(cyclicOwner, ignored -> new LinkedHashMap<>())
                        .merge(key, outputDemand, BigInteger::add);
                if (!crossBoundaryInput) {
                    merge(this.demand, key, outputDemand.negate());
                }
                return TrinityAlgorithmResult.success(StepSuccess.INSTANCE);
            }
            BigInteger count = ceilDivide(outputDemand, selected.outputs().get(key));
            int rank = Math.multiplyExact(this.topologicalPositions.get(outputComponent.index()), 2);
            if (crossBoundaryInput) {
                rank = Math.subtractExact(rank, 1);
            }
            registerAcyclic(selected, count, rank);
            applyReverseDemand(selected, count, crossBoundaryInput ? key : null);
            return TrinityAlgorithmResult.success(StepSuccess.INSTANCE);
        }

        private Optional<ScalarCycleDemand> scalarDemand(
                                                         TrinityStronglyConnectedComponent component,
                                                         TrinityCycleDemand demand) {
            if (demand.requiredNetChangeLowerBounds().size() != 1) {
                return Optional.empty();
            }
            Map.Entry<AEKey, BigInteger> net = demand.requiredNetChangeLowerBounds()
                    .entrySet()
                    .iterator()
                    .next();
            if (!component.keys().contains(net.getKey())) {
                return Optional.empty();
            }
            if (demand.finalBalanceLowerBounds().isEmpty()) {
                return Optional.of(new ScalarCycleDemand(
                        net.getKey(),
                        net.getValue(),
                        CraftingQuantityMode.NET_NEW));
            }
            if (demand.finalBalanceLowerBounds().size() != 1 ||
                    !demand.finalBalanceLowerBounds().containsKey(net.getKey())) {
                return Optional.empty();
            }
            return Optional.of(new ScalarCycleDemand(
                    net.getKey(),
                    demand.finalBalanceLowerBounds().get(net.getKey()),
                    CraftingQuantityMode.FINAL_TOTAL));
        }

        private List<TrinityPatternVariant> producersFor(
                                                         AEKey key,
                                                         int outputComponent,
                                                         boolean crossBoundaryOnly) {
            int outputPosition = this.topologicalPositions.get(outputComponent);
            return this.topology.variantsByOutputComponent()
                    .getOrDefault(outputComponent, List.of())
                    .stream()
                    .filter(variant -> variant.outputs().containsKey(key))
                    .filter(variant -> !crossBoundaryOnly || variant.inputs().keySet().stream().allMatch(input -> this.topologicalPositions.get(this.topology.componentByKey().get(input)) <
                            outputPosition))
                    .sorted()
                    .toList();
        }

        private Set<AEKey> producibleInputs(TrinityStronglyConnectedComponent component) {
            int cyclePosition = this.topologicalPositions.get(component.index());
            LinkedHashSet<AEKey> inputs = new LinkedHashSet<>(component.keys());
            component.cycleVariants().forEach(variant -> inputs.addAll(variant.inputs().keySet()));
            LinkedHashSet<AEKey> producible = new LinkedHashSet<>();
            for (AEKey key : inputs) {
                boolean hasEarlierProducer = this.variants.stream()
                        .filter(variant -> variant.outputs().containsKey(key))
                        .anyMatch(variant -> variant.inputs().keySet().stream().allMatch(input -> this.topologicalPositions.get(this.topology.componentByKey().get(input)) <
                                cyclePosition));
                if (hasEarlierProducer) {
                    producible.add(key);
                }
            }
            return Collections.unmodifiableSet(producible);
        }

        private void applyReverseDemand(
                                        TrinityPatternVariant variant,
                                        BigInteger count,
                                        AEKey satisfiedBoundary) {
            variant.inputs().forEach((key, amount) -> merge(this.demand, key, amount.multiply(count)));
            variant.outputs().forEach((key, amount) -> {
                if (!key.equals(satisfiedBoundary)) {
                    merge(this.demand, key, amount.multiply(count).negate());
                }
            });
        }

        private void registerAcyclic(TrinityPatternVariant variant, BigInteger count, int rank) {
            this.acyclicFirings.merge(
                    variant,
                    new AcyclicFiring(count, rank),
                    (existing, added) -> new AcyclicFiring(
                            existing.count().add(added.count()),
                            Math.min(existing.rank(), added.rank())));
        }

        private BigInteger reserveFromInventory(AEKey key, BigInteger required) {
            BigInteger available = this.inventory.getOrDefault(key, BigInteger.ZERO);
            BigInteger reserved = required.min(available);
            if (reserved.signum() > 0) {
                BigInteger remaining = available.subtract(reserved);
                if (remaining.signum() == 0) {
                    this.inventory.remove(key);
                } else {
                    this.inventory.put(key, remaining);
                }
                merge(this.initialInputs, key, reserved);
            }
            return reserved;
        }

        private BigInteger positiveDemand(AEKey key) {
            return this.demand.getOrDefault(key, BigInteger.ZERO).max(BigInteger.ZERO);
        }

        private TrinityAlgorithmResult<PlanAssembly> assemble() {
            ArrayList<OrderedUnit> units = new ArrayList<>();
            this.acyclicFirings.forEach((variant, firing) -> units.add(new AcyclicUnit(
                    firing.rank(),
                    variant,
                    firing.count())));
            for (int index = 0; index < this.cycleSolutions.size(); index++) {
                CycleSolution cycle = this.cycleSolutions.get(index);
                units.add(new CycleUnit(
                        Math.multiplyExact(this.topologicalPositions.get(cycle.componentIndex()), 2),
                        index,
                        cycle));
            }
            units.sort(Comparator
                    .comparingInt(OrderedUnit::rank)
                    .thenComparing(OrderedUnit::stableKey));

            ArrayList<TrinityPlanStage> stages = new ArrayList<>();
            ArrayList<Integer> stageOrder = new ArrayList<>();
            ArrayList<TrinityCycleRepeatBlock> repeatBlocks = new ArrayList<>();
            LinkedHashMap<TrinityPatternIdentity, BigInteger> patternFirings = new LinkedHashMap<>();
            LinkedHashMap<AEKey, BigInteger> netChange = new LinkedHashMap<>();
            LinkedHashMap<AEKey, BigInteger> minimumSeed = new LinkedHashMap<>();
            LinkedHashMap<AEKey, BigInteger> stackRequests = new LinkedHashMap<>();
            int repeatIndex = 0;

            for (OrderedUnit unit : units) {
                if (unit instanceof AcyclicUnit acyclic) {
                    int stageIndex = stages.size();
                    Set<Integer> dependencies = stageIndex == 0 ?
                            Set.of() :
                            Set.of(stageIndex - 1);
                    stages.add(stage(
                            stageIndex,
                            false,
                            dependencies,
                            acyclic.variant(),
                            acyclic.count(),
                            false));
                    stageOrder.add(stageIndex);
                    mergePatternFiring(patternFirings, acyclic.variant(), acyclic.count());
                    mergeScaled(netChange, acyclic.variant().netChange(), acyclic.count());
                    mergeScaled(stackRequests, acyclic.variant().inputs(), acyclic.count());
                    mergeScaled(stackRequests, acyclic.variant().outputs(), acyclic.count());
                    continue;
                }

                CycleSolution cycle = ((CycleUnit) unit).solution();
                ArrayList<Integer> blockStages = new ArrayList<>();
                for (TrinityVariantFiring batch : cycle.localOrder()) {
                    int stageIndex = stages.size();
                    Set<Integer> dependencies = stageIndex == 0 ?
                            Set.of() :
                            Set.of(stageIndex - 1);
                    stages.add(stage(
                            stageIndex,
                            true,
                            dependencies,
                            batch.variant(),
                            batch.count(),
                            true));
                    stageOrder.add(stageIndex);
                    blockStages.add(stageIndex);
                    BigInteger totalCount = batch.count().multiply(cycle.repetitions());
                    mergePatternFiring(patternFirings, batch.variant(), totalCount);
                    mergeScaled(stackRequests, batch.variant().inputs(), totalCount);
                    mergeScaled(stackRequests, batch.variant().outputs(), totalCount);
                }
                repeatBlocks.add(new TrinityCycleRepeatBlock(
                        repeatIndex++,
                        blockStages,
                        cycle.repetitions(),
                        cycle.minimumSeed(),
                        cycle.netChange()));
                cycle.minimumSeed().forEach((key, amount) -> minimumSeed.merge(key, amount, BigInteger::max));
                mergeScaled(netChange, cycle.netChange(), BigInteger.ONE);
            }
            removeZeros(netChange);
            if (stages.isEmpty()) {
                return failure(
                        TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                        "A Trinity plan requires at least one executable crafting stage",
                        Map.of("target", this.target.toString()));
            }
            return TrinityAlgorithmResult.success(new PlanAssembly(
                    Collections.unmodifiableMap(new LinkedHashMap<>(this.initialInputs)),
                    Collections.unmodifiableMap(patternFirings),
                    List.copyOf(stages),
                    List.copyOf(stageOrder),
                    List.copyOf(repeatBlocks),
                    Collections.unmodifiableMap(minimumSeed),
                    Collections.unmodifiableMap(netChange),
                    Collections.unmodifiableMap(stackRequests),
                    Math.addExact(this.scheduleStates, this.routeSearchBudget.used()),
                    this.mipNanos));
        }

        private <T> TrinityAlgorithmResult<T> insufficient(AEKey key, BigInteger amount) {
            return failure(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    "Trinity planning cannot satisfy an upstream input",
                    Map.of("key", key.toString(), "required", amount.toString()));
        }

        private <T> TrinityAlgorithmResult<T> routeSearchLimit() {
            return failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    "Trinity mixed-route search reached the configured state limit",
                    Map.of(
                            "limit", Integer.toString(this.routeSearchBudget.limit()),
                            "states", Integer.toString(this.routeSearchBudget.used())));
        }
    }

    private static TrinityPlanStage stage(
                                          int index,
                                          boolean cycle,
                                          Set<Integer> dependencies,
                                          TrinityPatternVariant variant,
                                          BigInteger count,
                                          boolean compressedCycleBatch) {
        Map<AEKey, BigInteger> required = compressedCycleBatch ?
                requiredAtStart(variant, count) :
                multiplyPositive(variant.inputs(), count);
        return new TrinityPlanStage(
                index,
                cycle,
                dependencies,
                List.of(new TrinityPlanPatternFiring(
                        variant.patternIdentity(),
                        variant.primaryOutput(),
                        variant.ordinal(),
                        count,
                        variant.declaredOutputs())),
                required,
                multiplySigned(variant.netChange(), count));
    }

    private static Map<AEKey, BigInteger> requiredAtStart(
                                                          TrinityPatternVariant variant,
                                                          BigInteger count) {
        LinkedHashMap<AEKey, BigInteger> required = new LinkedHashMap<>();
        variant.inputs().forEach((key, input) -> {
            BigInteger net = variant.netChange().getOrDefault(key, BigInteger.ZERO);
            BigInteger amount = net.signum() < 0 ?
                    input.add(net.negate().multiply(count.subtract(BigInteger.ONE))) :
                    input;
            required.put(key, amount);
        });
        return Collections.unmodifiableMap(required);
    }

    private static Map<AEKey, BigInteger> multiplyPositive(
                                                           Map<AEKey, BigInteger> amounts,
                                                           BigInteger multiplier) {
        LinkedHashMap<AEKey, BigInteger> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> result.put(key, amount.multiply(multiplier)));
        return Collections.unmodifiableMap(result);
    }

    private static Map<AEKey, BigInteger> multiplySigned(
                                                         Map<AEKey, BigInteger> amounts,
                                                         BigInteger multiplier) {
        LinkedHashMap<AEKey, BigInteger> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> {
            BigInteger multiplied = amount.multiply(multiplier);
            if (multiplied.signum() != 0) {
                result.put(key, multiplied);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private static Map<Integer, Integer> topologicalPositions(TrinityCraftingTopology topology) {
        HashMap<Integer, Integer> positions = new HashMap<>();
        for (int position = 0; position < topology.topologicalOrder().size(); position++) {
            positions.put(topology.topologicalOrder().get(position), position);
        }
        return Collections.unmodifiableMap(positions);
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

    private static void verifyLongBoundaries(
                                             BigInteger requestedAmount,
                                             Map<AEKey, BigInteger> initialInputs,
                                             Map<TrinityPatternIdentity, BigInteger> patternFirings,
                                             Map<AEKey, BigInteger> netChange,
                                             Map<AEKey, BigInteger> stackRequests) {
        verifyLongBoundary(requestedAmount);
        initialInputs.values().forEach(TrinityGraphPlannerImpl::verifyLongBoundary);
        patternFirings.values().forEach(TrinityGraphPlannerImpl::verifyLongBoundary);
        netChange.values().forEach(TrinityGraphPlannerImpl::verifyLongBoundary);
        stackRequests.values().forEach(TrinityGraphPlannerImpl::verifyLongBoundary);
    }

    private static void verifyLongBoundary(BigInteger value) {
        long exact = value.longValueExact();
        if (!BigInteger.valueOf(exact).equals(value)) {
            throw new IllegalStateException("An exact Trinity long conversion changed its source value");
        }
    }

    private static boolean hasMultiplePaths(List<TrinityPatternVariant> variants) {
        HashMap<AEKey, Integer> producerCounts = new HashMap<>();
        for (TrinityPatternVariant variant : variants) {
            for (AEKey output : variant.outputs().keySet()) {
                int count = producerCounts.merge(output, 1, Integer::sum);
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LinkedHashMap<AEKey, BigInteger> maximumAmounts(
                                                                   Map<AEKey, BigInteger> first,
                                                                   Map<AEKey, BigInteger> second) {
        LinkedHashMap<AEKey, BigInteger> maximum = new LinkedHashMap<>(first);
        second.forEach((key, amount) -> maximum.merge(key, amount, BigInteger::max));
        return maximum;
    }

    private static void mergePatternFiring(
                                           Map<TrinityPatternIdentity, BigInteger> firings,
                                           TrinityPatternVariant variant,
                                           BigInteger count) {
        firings.merge(variant.patternIdentity(), count, BigInteger::add);
    }

    private static void mergeScaled(
                                    Map<AEKey, BigInteger> target,
                                    Map<AEKey, BigInteger> source,
                                    BigInteger multiplier) {
        source.forEach((key, amount) -> merge(target, key, amount.multiply(multiplier)));
    }

    private static void merge(Map<AEKey, BigInteger> amounts, AEKey key, BigInteger amount) {
        amounts.merge(key, amount, BigInteger::add);
    }

    private static void removeZeros(Map<AEKey, BigInteger> amounts) {
        amounts.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
    }

    private static BigInteger sum(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() <= 0 || denominator.signum() <= 0) {
            throw new IllegalArgumentException("Trinity aggregate demand requires a positive producer output");
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
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
                "Trinity graph planning was cancelled",
                Map.of());
    }

    private static <T> TrinityAlgorithmResult<T> deadlineExceeded() {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                "Trinity graph planning exhausted its shared deadline",
                Map.of("phase", "graph"));
    }

    private static <T> TrinityAlgorithmResult<T> failure(
                                                         TrinityPlanningDiagnosticCode code,
                                                         String detail,
                                                         Map<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.literal(detail),
                metadata));
    }

    private sealed interface OrderedUnit permits AcyclicUnit, CycleUnit {

        int rank();

        String stableKey();
    }

    private record AcyclicUnit(
                               int rank,
                               TrinityPatternVariant variant,
                               BigInteger count)
            implements OrderedUnit {

        @Override
        public String stableKey() {
            return "0:" + this.variant.patternIdentity().publicationEncoding() + ':' + this.variant.ordinal();
        }
    }

    private record CycleUnit(
                             int rank,
                             int sequence,
                             CycleSolution solution)
            implements OrderedUnit {

        @Override
        public String stableKey() {
            return "1:" + String.format("%010d", this.sequence);
        }
    }

    private record AcyclicFiring(BigInteger count, int rank) {}

    private record CycleSolution(
                                 int componentIndex,
                                 List<TrinityVariantFiring> localOrder,
                                 BigInteger repetitions,
                                 Map<AEKey, BigInteger> minimumSeed,
                                 Map<AEKey, BigInteger> initialInputs,
                                 Map<AEKey, BigInteger> netChange,
                                 int scheduleStates,
                                 long mipNanos) {}

    private record PreparedCycle(
                                 CycleSolution solution,
                                 Map<AEKey, BigInteger> internalRequirements,
                                 Set<AEKey> producibleInputs) {}

    private record ScalarCycleDemand(
                                     AEKey target,
                                     BigInteger amount,
                                     CraftingQuantityMode quantityMode) {}

    private record PlanAssembly(
                                Map<AEKey, BigInteger> initialInputs,
                                Map<TrinityPatternIdentity, BigInteger> patternFirings,
                                List<TrinityPlanStage> stages,
                                List<Integer> stageOrder,
                                List<TrinityCycleRepeatBlock> repeatBlocks,
                                Map<AEKey, BigInteger> minimumSeed,
                                Map<AEKey, BigInteger> netChange,
                                Map<AEKey, BigInteger> stackRequests,
                                int scheduleStates,
                                long mipNanos) {}

    private enum StepSuccess {
        INSTANCE
    }

    private static final class RouteSearchBudget {

        private final int limit;
        private int used;

        private RouteSearchBudget(int limit) {
            if (limit <= 0) {
                throw new IllegalArgumentException("A Trinity route-search limit must be positive");
            }
            this.limit = limit;
        }

        private boolean consume() {
            if (this.used >= this.limit) {
                return false;
            }
            this.used = Math.incrementExact(this.used);
            return true;
        }

        private int limit() {
            return this.limit;
        }

        private int used() {
            return this.used;
        }
    }

    private enum StopState {
        RUNNING,
        CANCELLED,
        DEADLINE_EXCEEDED
    }
}
