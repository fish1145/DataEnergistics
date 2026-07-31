package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityDeterministicCyclePlanner;
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
    private final TrinityDeterministicCyclePlanner deterministicCyclePlanner;
    private final TrinityMixedIntegerCycleSolver mixedIntegerCycleSolver;
    private final TrinityPlanByteEstimator byteEstimator;

    TrinityGraphPlannerImpl(TrinityPatternVariantExpander variantExpander,
                            TrinityGraphTopologyAnalyzer topologyAnalyzer,
                            TrinityAcyclicDemandPropagator acyclicDemandPropagator,
                            TrinityDeterministicCyclePlanner deterministicCyclePlanner,
                            TrinityMixedIntegerCycleSolver mixedIntegerCycleSolver,
                            TrinityPlanByteEstimator byteEstimator) {
        this.variantExpander = variantExpander;
        this.topologyAnalyzer = topologyAnalyzer;
        this.acyclicDemandPropagator = acyclicDemandPropagator;
        this.deterministicCyclePlanner = deterministicCyclePlanner;
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
                .emittedItems(Map.of(target, requestedAmount))
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
        private final LinkedHashMap<AEKey, BigInteger> demand = new LinkedHashMap<>();
        private final LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>();
        private final LinkedHashMap<TrinityPatternVariant, AcyclicFiring> acyclicFirings = new LinkedHashMap<>();
        private final ArrayList<CycleSolution> cycleSolutions = new ArrayList<>();
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
            this.demand.put(target, requestedAmount);
        }

        private TrinityAlgorithmResult<PlanAssembly> solve() {
            List<Integer> order = this.topology.topologicalOrder();
            for (int position = order.size() - 1; position >= 0; position--) {
                StopState state = stopState(this.control);
                if (state == StopState.CANCELLED) {
                    return cancelled();
                }
                if (state == StopState.DEADLINE_EXCEEDED) {
                    return deadlineExceeded();
                }
                TrinityStronglyConnectedComponent component = this.topology.components().get(order.get(position));
                TrinityAlgorithmResult<StepSuccess> processed = component.cyclic() ?
                        processCycleComponent(component) :
                        processAcyclicComponent(component);
                if (!processed.successful()) {
                    return TrinityAlgorithmResult.failure(processed.diagnostic());
                }
            }
            for (Map.Entry<AEKey, BigInteger> remaining : this.demand.entrySet()) {
                if (remaining.getValue().signum() > 0) {
                    return insufficient(remaining.getKey(), remaining.getValue());
                }
            }
            return assemble();
        }

        private TrinityAlgorithmResult<StepSuccess> processAcyclicComponent(
                                                                            TrinityStronglyConnectedComponent component) {
            for (AEKey key : component.keys()) {
                BigInteger required = positiveDemand(key);
                boolean forceFinalProduction = key.equals(this.target) &&
                        this.quantityMode == CraftingQuantityMode.FINAL_TOTAL;
                if (required.signum() <= 0 && !forceFinalProduction) {
                    continue;
                }

                boolean netNewTarget = key.equals(this.target) &&
                        this.quantityMode == CraftingQuantityMode.NET_NEW;
                if (!netNewTarget && required.signum() > 0) {
                    BigInteger reserved = reserveFromInventory(key, required);
                    merge(this.demand, key, reserved.negate());
                }
                BigInteger missing = positiveDemand(key);
                if (missing.signum() <= 0 && !forceFinalProduction) {
                    continue;
                }

                List<TrinityPatternVariant> candidates = producersFor(key, component.index(), false);
                this.scheduleStates = Math.addExact(
                        this.scheduleStates,
                        Math.max(1, candidates.size()));
                if (candidates.isEmpty()) {
                    return insufficient(key, missing.max(BigInteger.ONE));
                }
                TrinityPatternVariant selected = candidates.getFirst();
                BigInteger count = missing.signum() > 0 ?
                        ceilDivide(missing, selected.outputs().get(key)) :
                        BigInteger.ONE;
                registerAcyclic(
                        selected,
                        count,
                        Math.multiplyExact(this.topologicalPositions.get(component.index()), 2));
                applyReverseDemand(selected, count, null);
            }
            return TrinityAlgorithmResult.success(StepSuccess.INSTANCE);
        }

        private TrinityAlgorithmResult<StepSuccess> processCycleComponent(
                                                                          TrinityStronglyConnectedComponent component) {
            for (AEKey key : component.keys()) {
                BigInteger required = positiveDemand(key);
                if (required.signum() <= 0) {
                    continue;
                }
                CraftingQuantityMode cycleMode = key.equals(this.target) ?
                        this.quantityMode :
                        CraftingQuantityMode.NET_NEW;
                Set<AEKey> producibleInputs = producibleInputs(component);
                TrinityAlgorithmResult<CycleSolution> solved = solveCycle(
                        component,
                        key,
                        required,
                        cycleMode,
                        producibleInputs);
                if (!solved.successful()) {
                    return TrinityAlgorithmResult.failure(solved.diagnostic());
                }
                CycleSolution solution = solved.value();
                for (Map.Entry<AEKey, BigInteger> input : solution.initialInputs().entrySet()) {
                    TrinityAlgorithmResult<StepSuccess> satisfied = satisfyCycleInput(
                            component,
                            input.getKey(),
                            input.getValue(),
                            producibleInputs);
                    if (!satisfied.successful()) {
                        return satisfied;
                    }
                }
                this.cycleSolutions.add(solution);
                this.scheduleStates = Math.addExact(this.scheduleStates, solution.scheduleStates());
                this.mipNanos = Math.addExact(this.mipNanos, solution.mipNanos());
                solution.netChange().forEach((output, amount) -> {
                    if (amount.signum() > 0) {
                        merge(this.demand, output, amount.negate());
                    }
                });
                if (cycleMode == CraftingQuantityMode.FINAL_TOTAL) {
                    this.demand.put(key, BigInteger.ZERO);
                }
            }
            return TrinityAlgorithmResult.success(StepSuccess.INSTANCE);
        }

        private TrinityAlgorithmResult<CycleSolution> solveCycle(
                                                                 TrinityStronglyConnectedComponent component,
                                                                 AEKey cycleTarget,
                                                                 BigInteger amount,
                                                                 CraftingQuantityMode cycleMode,
                                                                 Set<AEKey> producibleInputs) {
            Optional<List<TrinityVariantFiring>> deterministicOrder = deterministicOrder(component, cycleTarget);
            if (deterministicOrder.isPresent()) {
                TrinityAlgorithmResult<TrinityCyclePlan> deterministic = deterministicCyclePlanner.plan(
                        deterministicOrder.orElseThrow(),
                        cycleTarget,
                        amount,
                        cycleMode,
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

            TrinityAlgorithmResult<TrinityMipCyclePlan> mip = mixedIntegerCycleSolver.solve(
                    component,
                    cycleTarget,
                    amount,
                    cycleMode,
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
                    plan.solverNanos()));
        }

        private TrinityAlgorithmResult<StepSuccess> satisfyCycleInput(
                                                                      TrinityStronglyConnectedComponent component,
                                                                      AEKey key,
                                                                      BigInteger required,
                                                                      Set<AEKey> producibleInputs) {
            BigInteger missing = required.subtract(reserveFromInventory(key, required));
            if (missing.signum() <= 0) {
                return TrinityAlgorithmResult.success(StepSuccess.INSTANCE);
            }
            if (!producibleInputs.contains(key)) {
                return insufficient(key, missing);
            }
            int inputComponent = this.topology.componentByKey().get(key);
            int cyclePosition = this.topologicalPositions.get(component.index());
            int inputPosition = this.topologicalPositions.get(inputComponent);
            if (inputPosition < cyclePosition) {
                merge(this.demand, key, missing);
                return TrinityAlgorithmResult.success(StepSuccess.INSTANCE);
            }
            if (inputComponent != component.index()) {
                return failure(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        "A Trinity cycle input producer violates condensation order",
                        Map.of("key", key.toString()));
            }
            return planCrossBoundary(component, key, missing);
        }

        private TrinityAlgorithmResult<StepSuccess> planCrossBoundary(
                                                                      TrinityStronglyConnectedComponent component,
                                                                      AEKey key,
                                                                      BigInteger missing) {
            List<TrinityPatternVariant> candidates = producersFor(key, component.index(), true);
            this.scheduleStates = Math.addExact(
                    this.scheduleStates,
                    Math.max(1, candidates.size()));
            if (candidates.isEmpty()) {
                return insufficient(key, missing);
            }
            TrinityPatternVariant selected = candidates.getFirst();
            BigInteger count = ceilDivide(missing, selected.outputs().get(key));
            int rank = Math.subtractExact(
                    Math.multiplyExact(this.topologicalPositions.get(component.index()), 2),
                    1);
            registerAcyclic(selected, count, rank);
            applyReverseDemand(selected, count, key);
            return TrinityAlgorithmResult.success(StepSuccess.INSTANCE);
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

        private Optional<List<TrinityVariantFiring>> deterministicOrder(
                                                                        TrinityStronglyConnectedComponent component,
                                                                        AEKey cycleTarget) {
            LinkedHashSet<TrinityPatternVariant> selected = new LinkedHashSet<>();
            for (AEKey key : component.keys()) {
                List<TrinityPatternVariant> producers = component.cycleVariants().stream()
                        .filter(variant -> variant.outputs().containsKey(key))
                        .toList();
                if (producers.size() != 1) {
                    return Optional.empty();
                }
                selected.add(producers.getFirst());
            }
            LinkedHashMap<AEKey, BigInteger> oneCycleNet = new LinkedHashMap<>();
            selected.forEach(variant -> variant.netChange().forEach(
                    (key, amount) -> merge(oneCycleNet, key, amount)));
            if (oneCycleNet.getOrDefault(cycleTarget, BigInteger.ZERO).signum() <= 0) {
                return Optional.empty();
            }
            for (AEKey key : component.keys()) {
                if (!key.equals(cycleTarget) &&
                        oneCycleNet.getOrDefault(key, BigInteger.ZERO).signum() != 0) {
                    return Optional.empty();
                }
            }

            ArrayList<TrinityPatternVariant> remaining = new ArrayList<>(selected.stream().sorted().toList());
            LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>(this.inventory);
            ArrayList<TrinityVariantFiring> order = new ArrayList<>(remaining.size());
            while (!remaining.isEmpty()) {
                TrinityPatternVariant executable = remaining.stream()
                        .filter(variant -> hasInputs(balances, variant.inputs()))
                        .findFirst()
                        .orElse(remaining.getFirst());
                remaining.remove(executable);
                order.add(new TrinityVariantFiring(executable, BigInteger.ONE));
                executable.netChange().forEach((key, amount) -> merge(balances, key, amount));
            }
            return Optional.of(List.copyOf(order));
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
                    this.scheduleStates,
                    this.mipNanos));
        }

        private <T> TrinityAlgorithmResult<T> insufficient(AEKey key, BigInteger amount) {
            return failure(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    "Trinity planning cannot satisfy an upstream input",
                    Map.of("key", key.toString(), "required", amount.toString()));
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
                        count)),
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

    private static boolean hasInputs(
                                     Map<AEKey, BigInteger> balances,
                                     Map<AEKey, BigInteger> inputs) {
        return inputs.entrySet().stream().allMatch(entry -> balances.getOrDefault(entry.getKey(), BigInteger.ZERO).compareTo(entry.getValue()) >= 0);
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

    private enum StopState {
        RUNNING,
        CANCELLED,
        DEADLINE_EXCEEDED
    }
}
