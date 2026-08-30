package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCyclePlanSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregates reverse demands across a condensation graph and selects bounded routes without constructing plan stages.
 * <p>
 * Branching reverse-demand implementation with one shared route-state budget across cloned search accumulators.
 */
public final class TrinityGraphDemandAggregator {

    /**
     * Creates the state-isolated demand search using the shared cycle selector.
     */
    public static TrinityGraphDemandAggregator create(TrinityCyclePlanSelector cyclePlanSelector) {
        return new TrinityGraphDemandAggregator(cyclePlanSelector);
    }

    private static final String INTERNAL_ERROR_KEY = "gui.data_energistics.trinity_planning.diagnostic.internal_error";
    private static final String INSUFFICIENT_INPUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.insufficient_input";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.diagnostic.search_limit";
    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String TIMEOUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.timeout";

    private final TrinityCyclePlanSelector cyclePlanSelector;

    TrinityGraphDemandAggregator(TrinityCyclePlanSelector cyclePlanSelector) {
        if (cyclePlanSelector == null) {
            throw new IllegalArgumentException("A Trinity graph demand aggregator requires a cycle selector");
        }
        this.cyclePlanSelector = cyclePlanSelector;
    }

    /**
     * Resolves all target, boundary, cycle-seed, and upstream demands into immutable firing selections.
     */
    public TrinityAlgorithmResult<TrinityGraphDemandSolution> aggregate(
                                                                        TrinityCraftingTopology topology,
                                                                        AEKey target,
                                                                        BigInteger requestedAmount,
                                                                        CraftingQuantityMode quantityMode,
                                                                        Map<AEKey, BigInteger> available,
                                                                        TrinityPlanningLimits limits,
                                                                        TrinityPlanningControl control) {
        if (topology == null || target == null || requestedAmount == null ||
                requestedAmount.signum() <= 0 || quantityMode == null || available == null || limits == null ||
                control == null) {
            throw new IllegalArgumentException("A Trinity graph demand request is incomplete");
        }
        return new PlanningAccumulator(
                topology,
                target,
                requestedAmount,
                quantityMode,
                available,
                limits,
                control).solve();
    }

    /**
     * Holds one isolated route branch while sharing only the bounded route counter and immutable graph context.
     */
    private final class PlanningAccumulator {

        private final TrinityCraftingTopology topology;
        private final AEKey target;
        private final CraftingQuantityMode quantityMode;
        private final LinkedHashMap<AEKey, BigInteger> inventory;
        private final TrinityPlanningLimits limits;
        private final TrinityPlanningControl control;
        private final Map<Integer, Integer> topologicalPositions;
        private final RouteSearchBudget routeSearchBudget;
        private final LinkedHashMap<AEKey, BigInteger> demand = new LinkedHashMap<>();
        private final LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>();
        private final LinkedHashMap<AEKey, InputRequirement> inputShortages = new LinkedHashMap<>();
        private final LinkedHashMap<TrinityPatternVariant, TrinityRankedPatternFiring> acyclicFirings = new LinkedHashMap<>();
        private final ArrayList<TrinityCycleSelection> cycleSolutions = new ArrayList<>();
        private final LinkedHashMap<Integer, LinkedHashMap<AEKey, BigInteger>> cycleOutputDemands = new LinkedHashMap<>();
        private int scheduleStates;
        private long mipNanos;

        private PlanningAccumulator(
                                    TrinityCraftingTopology topology,
                                    AEKey target,
                                    BigInteger requestedAmount,
                                    CraftingQuantityMode quantityMode,
                                    Map<AEKey, BigInteger> available,
                                    TrinityPlanningLimits limits,
                                    TrinityPlanningControl control) {
            this.topology = topology;
            this.target = target;
            this.quantityMode = quantityMode;
            this.inventory = new LinkedHashMap<>(available);
            this.limits = limits;
            this.control = control;
            this.topologicalPositions = topologicalPositions(topology);
            this.routeSearchBudget = new RouteSearchBudget(limits.maxScheduleStates());
            this.demand.put(target, requestedAmount);
        }

        private PlanningAccumulator(PlanningAccumulator source) {
            this.topology = source.topology;
            this.target = source.target;
            this.quantityMode = source.quantityMode;
            this.inventory = new LinkedHashMap<>(source.inventory);
            this.limits = source.limits;
            this.control = source.control;
            this.topologicalPositions = source.topologicalPositions;
            this.routeSearchBudget = source.routeSearchBudget;
            this.demand.putAll(source.demand);
            this.initialInputs.putAll(source.initialInputs);
            this.inputShortages.putAll(source.inputShortages);
            this.acyclicFirings.putAll(source.acyclicFirings);
            this.cycleSolutions.addAll(source.cycleSolutions);
            source.cycleOutputDemands.forEach((component, amounts) -> this.cycleOutputDemands.put(component, new LinkedHashMap<>(amounts)));
            this.scheduleStates = source.scheduleStates;
            this.mipNanos = source.mipNanos;
        }

        private TrinityAlgorithmResult<TrinityGraphDemandSolution> solve() {
            return solveComponent(this.topology.topologicalOrder().size() - 1);
        }

        private TrinityAlgorithmResult<TrinityGraphDemandSolution> solveComponent(int position) {
            StopState state = stopState(this.control);
            if (state == StopState.CANCELLED) {
                return failed(cancelled().diagnostic());
            }
            if (state == StopState.DEADLINE_EXCEEDED) {
                return failed(deadlineExceeded().diagnostic());
            }
            if (position < 0) {
                return completeDemand();
            }
            int componentIndex = this.topology.topologicalOrder().get(position);
            TrinityStronglyConnectedComponent component = this.topology.components().get(componentIndex);
            if (!component.cyclic()) {
                return processAcyclicComponent(component, 0, position);
            }
            TrinityAlgorithmResult<Optional<PreparedCycle>> prepared = prepareCycleComponent(component);
            if (!prepared.successful()) {
                return failed(prepared.diagnostic());
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

        private TrinityAlgorithmResult<TrinityGraphDemandSolution> completeDemand() {
            if (this.cycleOutputDemands.values().stream()
                    .flatMap(amounts -> amounts.values().stream())
                    .anyMatch(amount -> amount.signum() > 0)) {
                return failed(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        INTERNAL_ERROR_KEY,
                        Map.of());
            }
            for (Map.Entry<AEKey, BigInteger> remaining : this.demand.entrySet()) {
                BigInteger required = remaining.getValue().max(BigInteger.ZERO);
                if (required.signum() > 0) {
                    BigInteger reserved = reserveFromInventory(remaining.getKey(), required);
                    BigInteger missing = required.subtract(reserved);
                    if (missing.signum() > 0) {
                        recordShortage(remaining.getKey(), required, reserved, missing);
                    }
                    remaining.setValue(BigInteger.ZERO);
                }
            }
            if (!this.inputShortages.isEmpty()) {
                return insufficient();
            }
            return TrinityAlgorithmResult.success(new TrinityGraphDemandSolution(
                    this.initialInputs,
                    this.acyclicFirings,
                    this.cycleSolutions,
                    Math.addExact(this.scheduleStates, this.routeSearchBudget.used()),
                    this.mipNanos));
        }

        private TrinityAlgorithmResult<TrinityGraphDemandSolution> processAcyclicComponent(
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
                if (missing.signum() > 0) {
                    BigInteger allocated = required.subtract(missing);
                    recordShortage(key, required, allocated, missing);
                    merge(this.demand, key, missing.negate());
                    return processAcyclicComponent(component, keyIndex + 1, position);
                }
                return failed(
                        TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                        INSUFFICIENT_INPUT_KEY,
                        Map.of("key", key.toString(), "required", required.toString()));
            }
            TrinityPlanningDiagnostic bestDiagnostic = null;
            for (TrinityPatternVariant selected : candidates) {
                if (!this.routeSearchBudget.tryConsume()) {
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
                    if (bestDiagnostic == null) {
                        bestDiagnostic = applied.diagnostic();
                    }
                    continue;
                }
                TrinityAlgorithmResult<TrinityGraphDemandSolution> result = branch.processAcyclicComponent(
                        component,
                        keyIndex + 1,
                        position);
                if (result.successful()) {
                    return result;
                }
                if (bestDiagnostic == null) {
                    bestDiagnostic = result.diagnostic();
                }
            }
            return bestDiagnostic == null ?
                    failed(
                            TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                            INTERNAL_ERROR_KEY,
                            Map.of("key", key.toString())) :
                    failed(bestDiagnostic);
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
                if (key.equals(this.target)) {
                    shortage = shortage.max(BigInteger.ONE);
                }
                if (shortage.signum() > 0) {
                    requiredNetChanges.put(key, shortage);
                }
            }
            Map<AEKey, BigInteger> requestedCycleOutputs = Map.copyOf(
                    this.cycleOutputDemands.getOrDefault(component.index(), new LinkedHashMap<>()));
            requestedCycleOutputs.forEach((key, amount) -> merge(requiredNetChanges, key, amount));
            TrinityCycleDemand cycleDemand = new TrinityCycleDemand(finalBalances, requiredNetChanges);
            Set<AEKey> producibleInputs = producibleInputs(component);
            TrinityAlgorithmResult<TrinityCycleSelection> solved = TrinityGraphDemandAggregator.this.cyclePlanSelector.select(
                    component,
                    cycleDemand,
                    this.inventory,
                    producibleInputs,
                    this.limits.maxScheduleStates(),
                    this.control);
            if (!solved.successful()) {
                return failedNested(solved.diagnostic());
            }
            TrinityCycleSelection selection = solved.value();
            if (requiredNetChanges.entrySet().stream()
                    .anyMatch(entry -> selection.exportableNet()
                            .getOrDefault(entry.getKey(), BigInteger.ZERO)
                            .compareTo(entry.getValue()) < 0)) {
                return failed(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        INTERNAL_ERROR_KEY,
                        Map.of("component", Integer.toString(component.index())));
            }
            return TrinityAlgorithmResult.success(Optional.of(new PreparedCycle(
                    selection,
                    internalRequirements,
                    producibleInputs)));
        }

        private TrinityAlgorithmResult<TrinityGraphDemandSolution> satisfyCycleInputs(
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
                BigInteger reserved = reserveFromInventory(key, required);
                BigInteger missing = required.subtract(reserved);
                recordShortage(key, required, reserved, missing);
                return satisfyCycleInputs(component, prepared, inputs, inputIndex + 1, position);
            }
            int inputComponent = this.topology.componentByKey().get(key);
            int cyclePosition = this.topologicalPositions.get(component.index());
            int inputPosition = this.topologicalPositions.get(inputComponent);
            if (inputPosition < cyclePosition) {
                merge(this.demand, key, required);
                return satisfyCycleInputs(component, prepared, inputs, inputIndex + 1, position);
            }
            if (inputComponent != component.index()) {
                return failed(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        INTERNAL_ERROR_KEY,
                        Map.of("key", key.toString()));
            }
            BigInteger missing = required.subtract(reserveFromInventory(key, required));
            List<TrinityPatternVariant> candidates = producersFor(key, component.index(), true);
            if (candidates.isEmpty()) {
                recordShortage(key, required, required.subtract(missing), missing);
                return satisfyCycleInputs(component, prepared, inputs, inputIndex + 1, position);
            }
            TrinityPlanningDiagnostic bestDiagnostic = null;
            for (TrinityPatternVariant selected : candidates) {
                if (!this.routeSearchBudget.tryConsume()) {
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
                    if (bestDiagnostic == null) {
                        bestDiagnostic = applied.diagnostic();
                    }
                    continue;
                }
                TrinityAlgorithmResult<TrinityGraphDemandSolution> result = branch.satisfyCycleInputs(
                        component,
                        prepared,
                        inputs,
                        inputIndex + 1,
                        position);
                if (result.successful()) {
                    return result;
                }
                if (bestDiagnostic == null) {
                    bestDiagnostic = result.diagnostic();
                }
            }
            return bestDiagnostic == null ?
                    failed(
                            TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                            INTERNAL_ERROR_KEY,
                            Map.of("key", key.toString())) :
                    failed(bestDiagnostic);
        }

        private TrinityAlgorithmResult<TrinityGraphDemandSolution> finishCycleComponent(
                                                                                        TrinityStronglyConnectedComponent component,
                                                                                        PreparedCycle prepared,
                                                                                        int position) {
            TrinityCycleSelection solution = prepared.solution();
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
                    return failed(
                            TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                            INTERNAL_ERROR_KEY,
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

        private List<TrinityPatternVariant> producersFor(
                                                         AEKey key,
                                                         int outputComponent,
                                                         boolean crossBoundaryOnly) {
            int outputPosition = this.topologicalPositions.get(outputComponent);
            return this.topology.variantsByOutputKey()
                    .getOrDefault(key, List.of())
                    .stream()
                    .filter(variant -> !crossBoundaryOnly || variant.inputs().keySet().stream().allMatch(input -> this.topologicalPositions.get(this.topology.componentByKey().get(input)) < outputPosition))
                    .toList();
        }

        private Set<AEKey> producibleInputs(TrinityStronglyConnectedComponent component) {
            int cyclePosition = this.topologicalPositions.get(component.index());
            LinkedHashSet<AEKey> inputs = new LinkedHashSet<>(component.keys());
            component.cycleVariants().forEach(variant -> inputs.addAll(variant.inputs().keySet()));
            LinkedHashSet<AEKey> producible = new LinkedHashSet<>();
            for (AEKey key : inputs) {
                boolean hasEarlierProducer = this.topology.variantsByOutputKey()
                        .getOrDefault(key, List.of())
                        .stream()
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
                    new TrinityRankedPatternFiring(count, rank),
                    (existing, added) -> new TrinityRankedPatternFiring(
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

        private <T> TrinityAlgorithmResult<T> insufficient() {
            LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
            metadata.put("shortageKinds", Integer.toString(this.inputShortages.size()));
            if (this.inputShortages.size() == 1) {
                Map.Entry<AEKey, InputRequirement> shortage = this.inputShortages.entrySet().iterator().next();
                metadata.put("key", shortage.getKey().toString());
                metadata.put("required", shortage.getValue().required().toString());
                metadata.put("available", shortage.getValue().available().toString());
                metadata.put("missing", shortage.getValue().missing().toString());
            }
            return failed(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    INSUFFICIENT_INPUT_KEY,
                    metadata);
        }

        private <T> TrinityAlgorithmResult<T> routeSearchLimit() {
            return failed(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    SEARCH_LIMIT_KEY,
                    Map.of(
                            "limit", Integer.toString(this.routeSearchBudget.limit()),
                            "states", Integer.toString(this.routeSearchBudget.used())));
        }

        private <T> TrinityAlgorithmResult<T> failed(
                                                     TrinityPlanningDiagnosticCode code,
                                                     String translationKey,
                                                     Map<String, String> metadata) {
            return failed(new TrinityPlanningDiagnostic(
                    code,
                    Component.translatable(translationKey),
                    metadata));
        }

        private <T> TrinityAlgorithmResult<T> failed(TrinityPlanningDiagnostic diagnostic) {
            if (diagnostic.inputShortage().isPresent() || diagnostic.partialPlan().isPresent()) {
                return TrinityAlgorithmResult.failure(diagnostic);
            }
            return TrinityAlgorithmResult.failure(diagnostic.withDetail(partialPlan()));
        }

        private <T> TrinityAlgorithmResult<T> failedNested(TrinityPlanningDiagnostic diagnostic) {
            if (diagnostic.inputShortage().isPresent()) {
                return TrinityAlgorithmResult.failure(diagnostic);
            }
            TrinityPlanningDiagnostic.PartialPlan accumulated = partialPlan();
            TrinityPlanningDiagnostic.PartialPlan progress = diagnostic.partialPlan()
                    .map(partial -> mergeProgress(accumulated, partial))
                    .orElse(accumulated);
            return TrinityAlgorithmResult.failure(diagnostic.withDetail(progress));
        }

        private static TrinityPlanningDiagnostic.PartialPlan mergeProgress(
                                                                           TrinityPlanningDiagnostic.PartialPlan accumulated,
                                                                           TrinityPlanningDiagnostic.PartialPlan nested) {
            LinkedHashMap<AEKey, BigInteger> used = sum(
                    accumulated.usedItems(),
                    nested.usedItems());
            LinkedHashMap<AEKey, BigInteger> emitted = sum(
                    accumulated.emittedItems(),
                    nested.emittedItems());
            LinkedHashMap<AEKey, BigInteger> missing = new LinkedHashMap<>(accumulated.missingItems());
            nested.emittedItems().forEach((key, amount) -> {
                BigInteger unresolved = missing.get(key);
                if (unresolved == null) {
                    return;
                }
                BigInteger remainder = unresolved.subtract(amount);
                if (remainder.signum() > 0) {
                    missing.put(key, remainder);
                } else {
                    missing.remove(key);
                }
            });
            nested.missingItems().forEach((key, amount) -> missing.merge(key, amount, BigInteger::add));
            return new TrinityPlanningDiagnostic.PartialPlan(used, emitted, missing);
        }

        private static LinkedHashMap<AEKey, BigInteger> sum(
                                                            Map<AEKey, BigInteger> left,
                                                            Map<AEKey, BigInteger> right) {
            LinkedHashMap<AEKey, BigInteger> result = new LinkedHashMap<>(left);
            right.forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
            return result;
        }

        private TrinityPlanningDiagnostic.PartialPlan partialPlan() {
            LinkedHashMap<AEKey, BigInteger> emitted = new LinkedHashMap<>();
            this.acyclicFirings.forEach((variant, firing) -> mergeScaled(
                    emitted,
                    variant.outputs(),
                    firing.count()));
            for (TrinityCycleSelection cycle : this.cycleSolutions) {
                mergeFiringOutputs(emitted, cycle.prefixOrder(), BigInteger.ONE);
                mergeFiringOutputs(emitted, cycle.localOrder(), cycle.repetitions());
                mergeFiringOutputs(emitted, cycle.suffixOrder(), BigInteger.ONE);
            }

            LinkedHashMap<AEKey, BigInteger> unresolved = new LinkedHashMap<>();
            this.inputShortages.forEach((key, requirement) -> unresolved.put(key, requirement.missing()));
            this.demand.forEach((key, amount) -> {
                if (amount.signum() > 0) {
                    unresolved.merge(key, amount, BigInteger::add);
                }
            });
            this.cycleOutputDemands.values().forEach(amounts -> amounts.forEach((key, amount) -> {
                if (amount.signum() > 0) {
                    unresolved.merge(key, amount, BigInteger::max);
                }
            }));
            LinkedHashMap<AEKey, InputRequirement> exactRequirements = new LinkedHashMap<>();
            this.inputShortages.forEach((key, requirement) -> {
                if (requirement.missing().equals(unresolved.get(key))) {
                    exactRequirements.put(key, requirement);
                }
            });
            return new TrinityPlanningDiagnostic.PartialPlan(
                    this.initialInputs,
                    emitted,
                    unresolved,
                    exactRequirements);
        }

        private void recordShortage(
                                    AEKey key,
                                    BigInteger required,
                                    BigInteger available,
                                    BigInteger missing) {
            InputRequirement added = new InputRequirement(required, available, missing);
            this.inputShortages.merge(key, added, (existing, value) -> new InputRequirement(
                    existing.required().add(value.required()),
                    existing.available().add(value.available()),
                    existing.missing().add(value.missing())));
        }

        private static void mergeFiringOutputs(
                                               Map<AEKey, BigInteger> target,
                                               List<TrinityVariantFiring> firings,
                                               BigInteger multiplier) {
            firings.forEach(firing -> mergeScaled(
                    target,
                    firing.variant().outputs(),
                    firing.count().multiply(multiplier)));
        }

        private static void mergeScaled(
                                        Map<AEKey, BigInteger> target,
                                        Map<AEKey, BigInteger> amounts,
                                        BigInteger multiplier) {
            amounts.forEach((key, amount) -> target.merge(key, amount.multiply(multiplier), BigInteger::add));
        }
    }

    private static Map<Integer, Integer> topologicalPositions(TrinityCraftingTopology topology) {
        HashMap<Integer, Integer> positions = new HashMap<>();
        for (int position = 0; position < topology.topologicalOrder().size(); position++) {
            positions.put(topology.topologicalOrder().get(position), position);
        }
        return Collections.unmodifiableMap(positions);
    }

    private static void merge(Map<AEKey, BigInteger> amounts, AEKey key, BigInteger amount) {
        amounts.merge(key, amount, BigInteger::add);
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

    private record PreparedCycle(
                                 TrinityCycleSelection solution,
                                 Map<AEKey, BigInteger> internalRequirements,
                                 Set<AEKey> producibleInputs) {}

    private enum StepSuccess {
        INSTANCE
    }

    /**
     * Shares a single exact route-state allowance across every speculative branch clone.
     */
    private static final class RouteSearchBudget {

        private final int limit;
        private int used;

        private RouteSearchBudget(int limit) {
            if (limit <= 0) {
                throw new IllegalArgumentException("A Trinity route-search limit must be positive");
            }
            this.limit = limit;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private boolean tryConsume() {
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
