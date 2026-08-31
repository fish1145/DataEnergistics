package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCyclePlanSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityDiagnosticMaterialAccumulator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayDeque;
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
 * Branching reverse-demand implementation with explicit DFS frames, journaled rollback and one shared route-state
 * budget.
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
                                                                        TrinityPlanningMode mode,
                                                                        TrinityPlanningControl control) {
        if (topology == null || target == null || requestedAmount == null ||
                requestedAmount.signum() <= 0 || quantityMode == null || available == null || limits == null ||
                mode == null || control == null) {
            throw new IllegalArgumentException("A Trinity graph demand request is incomplete");
        }
        return new PlanningAccumulator(
                topology,
                target,
                requestedAmount,
                quantityMode,
                available,
                limits,
                mode,
                control).solve();
    }

    /**
     * Compatibility entry point that retains complete optimisation.
     */
    public TrinityAlgorithmResult<TrinityGraphDemandSolution> aggregate(
                                                                        TrinityCraftingTopology topology,
                                                                        AEKey target,
                                                                        BigInteger requestedAmount,
                                                                        CraftingQuantityMode quantityMode,
                                                                        Map<AEKey, BigInteger> available,
                                                                        TrinityPlanningLimits limits,
                                                                        TrinityPlanningControl control) {
        return aggregate(
                topology,
                target,
                requestedAmount,
                quantityMode,
                available,
                limits,
                TrinityPlanningMode.OPTIMAL,
                control);
    }

    /**
     * Owns the single thread-confined demand state traversed by explicit cursors and reversible producer choices.
     */
    private final class PlanningAccumulator {

        private final TrinityCraftingTopology topology;
        private final AEKey target;
        private final CraftingQuantityMode quantityMode;
        private final LinkedHashMap<AEKey, BigInteger> inventory;
        private final TrinityPlanningLimits limits;
        private final TrinityPlanningMode mode;
        private final TrinityPlanningControl control;
        private final Map<Integer, Integer> topologicalPositions;
        private final RouteSearchBudget routeSearchBudget;
        private final LinkedHashMap<AEKey, BigInteger> demand = new LinkedHashMap<>();
        private final LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>();
        private final LinkedHashMap<AEKey, InputRequirement> inputShortages = new LinkedHashMap<>();
        private final LinkedHashMap<TrinityPatternVariant, TrinityRankedPatternFiring> acyclicFirings = new LinkedHashMap<>();
        private final ArrayList<TrinityCycleSelection> cycleSolutions = new ArrayList<>();
        private final LinkedHashMap<Integer, LinkedHashMap<AEKey, BigInteger>> cycleOutputDemands = new LinkedHashMap<>();
        private final MutationJournal mutationJournal = new MutationJournal();
        private int scheduleStates;
        private long mipNanos;

        private PlanningAccumulator(
                                    TrinityCraftingTopology topology,
                                    AEKey target,
                                    BigInteger requestedAmount,
                                    CraftingQuantityMode quantityMode,
                                    Map<AEKey, BigInteger> available,
                                    TrinityPlanningLimits limits,
                                    TrinityPlanningMode mode,
                                    TrinityPlanningControl control) {
            this.topology = topology;
            this.target = target;
            this.quantityMode = quantityMode;
            this.inventory = new LinkedHashMap<>(available);
            this.limits = limits;
            this.mode = mode;
            this.control = control;
            this.topologicalPositions = topologicalPositions(topology);
            this.routeSearchBudget = new RouteSearchBudget(limits.maxScheduleStates(), control);
            this.demand.put(target, requestedAmount);
        }

        private TrinityAlgorithmResult<TrinityGraphDemandSolution> solve() {
            ArrayDeque<SearchFrame> frames = new ArrayDeque<>();
            frames.push(new ComponentCursor(this.topology.topologicalOrder().size() - 1));
            TrinityPlanningDiagnostic pendingFailure = null;
            while (!frames.isEmpty()) {
                SearchFrame frame = frames.pop();
                if (frame instanceof ProducerChoiceFrame choice) {
                    if (pendingFailure != null) {
                        this.mutationJournal.rollback(choice.checkpoint);
                        choice.recordFirstFailure(pendingFailure);
                        pendingFailure = null;
                    }
                    SearchAction action = advanceChoice(choice);
                    if (action instanceof ContinueAction continuation) {
                        frames.push(choice);
                        frames.push(continuation.cursor());
                    } else if (action instanceof FailureAction failure) {
                        pendingFailure = failure.diagnostic();
                    } else {
                        throw new IllegalStateException("A Trinity producer choice returned an invalid search action");
                    }
                    continue;
                }
                if (pendingFailure != null) {
                    throw new IllegalStateException("A Trinity search failure bypassed its producer choice");
                }

                SearchAction action = advanceCursor((SearchCursor) frame);
                if (action instanceof ContinueAction continuation) {
                    frames.push(continuation.cursor());
                } else if (action instanceof ChoiceAction choice) {
                    frames.push(choice.choice());
                } else if (action instanceof CompleteAction complete) {
                    return TrinityAlgorithmResult.success(complete.solution());
                } else if (action instanceof FailureAction failure) {
                    pendingFailure = failure.diagnostic();
                }
            }
            if (pendingFailure != null) {
                return TrinityAlgorithmResult.failure(pendingFailure);
            }
            return failed(TrinityPlanningDiagnosticCode.INTERNAL_ERROR, INTERNAL_ERROR_KEY, Map.of());
        }

        private SearchAction advanceCursor(SearchCursor cursor) {
            return switch (cursor) {
                case ComponentCursor component -> advanceComponent(component);
                case AcyclicKeyCursor acyclic -> advanceAcyclic(acyclic);
                case CycleInputCursor cycleInput -> advanceCycleInput(cycleInput);
            };
        }

        private SearchAction advanceComponent(ComponentCursor cursor) {
            StopState state = stopState(this.control);
            if (state == StopState.CANCELLED) {
                return failureAction(failed(cancelled().diagnostic()));
            }
            if (state == StopState.DEADLINE_EXCEEDED) {
                return failureAction(failed(deadlineExceeded().diagnostic()));
            }
            if (cursor.position() < 0) {
                TrinityAlgorithmResult<TrinityGraphDemandSolution> completed = completeDemand();
                return completed.successful() ?
                        new CompleteAction(completed.value()) :
                        new FailureAction(completed.diagnostic());
            }
            int componentIndex = this.topology.topologicalOrder().get(cursor.position());
            TrinityStronglyConnectedComponent component = this.topology.components().get(componentIndex);
            if (!component.cyclic()) {
                return new ContinueAction(new AcyclicKeyCursor(component, 0, cursor.position()));
            }
            TrinityAlgorithmResult<Optional<PreparedCycle>> prepared = prepareCycleComponent(component);
            if (!prepared.successful()) {
                return failureAction(failed(prepared.diagnostic()));
            }
            if (prepared.value().isEmpty()) {
                return new ContinueAction(new ComponentCursor(cursor.position() - 1));
            }
            PreparedCycle cycle = prepared.value().orElseThrow();
            return new ContinueAction(new CycleInputCursor(
                    component,
                    cycle,
                    List.copyOf(cycle.solution().initialInputs().entrySet()),
                    0,
                    cursor.position()));
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
                    putState(this.demand, remaining.getKey(), BigInteger.ZERO);
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

        private SearchAction advanceAcyclic(AcyclicKeyCursor cursor) {
            TrinityStronglyConnectedComponent component = cursor.component();
            if (cursor.keyIndex() >= component.keys().size()) {
                return new ContinueAction(new ComponentCursor(cursor.position() - 1));
            }
            SearchCursor continuation = new AcyclicKeyCursor(
                    component,
                    cursor.keyIndex() + 1,
                    cursor.position());
            AEKey key = component.keys().get(cursor.keyIndex());
            BigInteger required = positiveDemand(key);
            boolean forceFinalProduction = key.equals(this.target) &&
                    this.quantityMode == CraftingQuantityMode.FINAL_TOTAL;
            if (required.signum() <= 0 && !forceFinalProduction) {
                return new ContinueAction(continuation);
            }

            boolean netNewTarget = key.equals(this.target) &&
                    this.quantityMode == CraftingQuantityMode.NET_NEW;
            if (!netNewTarget && required.signum() > 0) {
                BigInteger reserved = reserveFromInventory(key, required);
                mergeState(this.demand, key, reserved.negate());
            }
            BigInteger missing = positiveDemand(key);
            if (missing.signum() <= 0 && !forceFinalProduction) {
                return new ContinueAction(continuation);
            }

            List<TrinityPatternVariant> candidates = producersFor(key, component.index(), false);
            if (candidates.isEmpty()) {
                if (missing.signum() > 0) {
                    BigInteger allocated = required.subtract(missing);
                    recordShortage(key, required, allocated, missing);
                    mergeState(this.demand, key, missing.negate());
                    return new ContinueAction(continuation);
                }
                return failureAction(failed(
                        TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                        INSUFFICIENT_INPUT_KEY,
                        Map.of("key", key.toString(), "required", required.toString())));
            }
            BigInteger outputDemand = missing.signum() > 0 ? missing : BigInteger.ONE;
            return new ChoiceAction(new ProducerChoiceFrame(
                    component,
                    key,
                    outputDemand,
                    false,
                    candidates,
                    continuation,
                    this.mutationJournal.checkpoint()));
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
                    mergeState(this.demand, key, reserved.negate());
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
                    this.mode,
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

        private SearchAction advanceCycleInput(CycleInputCursor cursor) {
            TrinityStronglyConnectedComponent component = cursor.component();
            PreparedCycle prepared = cursor.prepared();
            if (cursor.inputIndex() >= cursor.inputs().size()) {
                return finishCycleComponent(component, prepared, cursor.position());
            }
            SearchCursor continuation = new CycleInputCursor(
                    component,
                    prepared,
                    cursor.inputs(),
                    cursor.inputIndex() + 1,
                    cursor.position());
            Map.Entry<AEKey, BigInteger> input = cursor.inputs().get(cursor.inputIndex());
            AEKey key = input.getKey();
            BigInteger required = input.getValue();
            BigInteger available = this.inventory.getOrDefault(key, BigInteger.ZERO);
            if (available.compareTo(required) >= 0) {
                reserveFromInventory(key, required);
                return new ContinueAction(continuation);
            }
            if (!prepared.producibleInputs().contains(key)) {
                BigInteger reserved = reserveFromInventory(key, required);
                BigInteger missing = required.subtract(reserved);
                recordShortage(key, required, reserved, missing);
                return new ContinueAction(continuation);
            }
            int inputComponent = this.topology.componentByKey().get(key);
            int cyclePosition = this.topologicalPositions.get(component.index());
            int inputPosition = this.topologicalPositions.get(inputComponent);
            if (inputPosition < cyclePosition) {
                mergeState(this.demand, key, required);
                return new ContinueAction(continuation);
            }
            if (inputComponent != component.index()) {
                return failureAction(failed(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        INTERNAL_ERROR_KEY,
                        Map.of("key", key.toString())));
            }
            BigInteger missing = required.subtract(reserveFromInventory(key, required));
            List<TrinityPatternVariant> candidates = producersFor(key, component.index(), true);
            if (candidates.isEmpty()) {
                recordShortage(key, required, required.subtract(missing), missing);
                return new ContinueAction(continuation);
            }
            return new ChoiceAction(new ProducerChoiceFrame(
                    component,
                    key,
                    missing,
                    true,
                    candidates,
                    continuation,
                    this.mutationJournal.checkpoint()));
        }

        private SearchAction finishCycleComponent(
                                                  TrinityStronglyConnectedComponent component,
                                                  PreparedCycle prepared,
                                                  int position) {
            TrinityCycleSelection solution = prepared.solution();
            addState(this.cycleSolutions, solution);
            setScheduleStates(Math.addExact(this.scheduleStates, solution.scheduleStates()));
            setMipNanos(Math.addExact(this.mipNanos, solution.mipNanos()));
            prepared.internalRequirements().keySet().forEach(key -> putState(this.demand, key, BigInteger.ZERO));
            if (this.cycleOutputDemands.containsKey(component.index())) {
                putState(this.cycleOutputDemands, component.index(), new LinkedHashMap<>());
            }
            return new ContinueAction(new ComponentCursor(position - 1));
        }

        private SearchAction advanceChoice(ProducerChoiceFrame choice) {
            while (choice.nextCandidateIndex < choice.candidates.size()) {
                if (!this.routeSearchBudget.tryConsume()) {
                    return failureAction(routeSearchLimit());
                }
                TrinityPatternVariant selected = choice.candidates.get(choice.nextCandidateIndex++);
                TrinityAlgorithmResult<StepSuccess> applied = applyProducerChoice(
                        choice.outputComponent,
                        choice.key,
                        selected,
                        choice.outputDemand,
                        choice.crossBoundaryInput);
                if (applied.successful()) {
                    return new ContinueAction(choice.continuation);
                }
                TrinityPlanningDiagnostic diagnostic = applied.diagnostic();
                this.mutationJournal.rollback(choice.checkpoint);
                choice.recordFirstFailure(diagnostic);
            }
            if (choice.bestDiagnostic != null) {
                return failureAction(failed(choice.bestDiagnostic));
            }
            return failureAction(failed(
                    TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                    INTERNAL_ERROR_KEY,
                    Map.of("key", choice.key.toString())));
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
                mergeCycleOutputDemand(cyclicOwner, key, outputDemand);
                if (!crossBoundaryInput) {
                    mergeState(this.demand, key, outputDemand.negate());
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
                                        @Nullable AEKey satisfiedBoundary) {
            variant.inputs().forEach((key, amount) -> mergeState(this.demand, key, amount.multiply(count)));
            variant.outputs().forEach((key, amount) -> {
                if (!key.equals(satisfiedBoundary)) {
                    mergeState(this.demand, key, amount.multiply(count).negate());
                }
            });
        }

        private void registerAcyclic(TrinityPatternVariant variant, BigInteger count, int rank) {
            TrinityRankedPatternFiring added = new TrinityRankedPatternFiring(count, rank);
            TrinityRankedPatternFiring existing = this.acyclicFirings.get(variant);
            putState(
                    this.acyclicFirings,
                    variant,
                    existing == null ? added : new TrinityRankedPatternFiring(
                            existing.count().add(added.count()),
                            Math.min(existing.rank(), added.rank())));
        }

        private BigInteger reserveFromInventory(AEKey key, BigInteger required) {
            BigInteger available = this.inventory.getOrDefault(key, BigInteger.ZERO);
            BigInteger reserved = required.min(available);
            if (reserved.signum() > 0) {
                BigInteger remaining = available.subtract(reserved);
                putState(this.inventory, key, remaining);
                mergeState(this.initialInputs, key, reserved);
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
            TrinityPlanningDiagnostic.PartialPlan accumulated = partialPlan();
            TrinityPlanningDiagnostic.PartialPlan progress = diagnostic.partialPlan()
                    .or(() -> diagnostic.inputShortage().map(
                            TrinityDiagnosticMaterialAccumulator::fromShortage))
                    .map(partial -> mergeProgress(accumulated, partial))
                    .orElse(accumulated);
            return TrinityAlgorithmResult.failure(diagnostic.withDetail(progress));
        }

        private static TrinityPlanningDiagnostic.PartialPlan mergeProgress(
                                                                           TrinityPlanningDiagnostic.PartialPlan accumulated,
                                                                           TrinityPlanningDiagnostic.PartialPlan nested) {
            TrinityDiagnosticMaterialAccumulator accumulator = TrinityDiagnosticMaterialAccumulator.create(
                    accumulated);
            accumulator.add(nested);
            return accumulator.snapshot();
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
            InputRequirement existing = this.inputShortages.get(key);
            putState(
                    this.inputShortages,
                    key,
                    existing == null ? added : new InputRequirement(
                            existing.required().add(added.required()),
                            existing.available().add(added.available()),
                            existing.missing().add(added.missing())));
        }

        private FailureAction failureAction(TrinityAlgorithmResult<?> result) {
            if (result.successful()) {
                throw new IllegalArgumentException("A successful Trinity result cannot become a search failure");
            }
            return new FailureAction(result.diagnostic());
        }

        private void mergeCycleOutputDemand(int component, AEKey key, BigInteger amount) {
            LinkedHashMap<AEKey, BigInteger> amounts = this.cycleOutputDemands.get(component);
            if (amounts == null) {
                amounts = new LinkedHashMap<>();
                putState(this.cycleOutputDemands, component, amounts);
            }
            mergeState(amounts, key, amount);
        }

        private void mergeState(Map<AEKey, BigInteger> amounts, AEKey key, BigInteger amount) {
            putState(amounts, key, amounts.getOrDefault(key, BigInteger.ZERO).add(amount));
        }

        private <K, V> void putState(Map<K, V> map, K key, V value) {
            boolean contained = map.containsKey(key);
            V previous = map.get(key);
            this.mutationJournal.record(() -> {
                if (contained) {
                    map.put(key, previous);
                } else {
                    map.remove(key);
                }
            });
            map.put(key, value);
        }

        private <E> void addState(List<E> values, E value) {
            int index = values.size();
            this.mutationJournal.record(() -> values.remove(index));
            values.add(value);
        }

        private void setScheduleStates(int value) {
            int previous = this.scheduleStates;
            this.mutationJournal.record(() -> this.scheduleStates = previous);
            this.scheduleStates = value;
        }

        private void setMipNanos(long value) {
            long previous = this.mipNanos;
            this.mutationJournal.record(() -> this.mipNanos = previous);
            this.mipNanos = value;
        }

        private sealed interface SearchFrame permits SearchCursor, ProducerChoiceFrame {}

        private sealed interface SearchCursor extends SearchFrame
                                              permits ComponentCursor, AcyclicKeyCursor, CycleInputCursor {}

        private record ComponentCursor(int position) implements SearchCursor {}

        private record AcyclicKeyCursor(
                                        TrinityStronglyConnectedComponent component,
                                        int keyIndex,
                                        int position)
                implements SearchCursor {}

        private record CycleInputCursor(
                                        TrinityStronglyConnectedComponent component,
                                        PreparedCycle prepared,
                                        List<Map.Entry<AEKey, BigInteger>> inputs,
                                        int inputIndex,
                                        int position)
                implements SearchCursor {}

        private static final class ProducerChoiceFrame implements SearchFrame {

            private final TrinityStronglyConnectedComponent outputComponent;
            private final AEKey key;
            private final BigInteger outputDemand;
            private final boolean crossBoundaryInput;
            private final List<TrinityPatternVariant> candidates;
            private final SearchCursor continuation;
            private final int checkpoint;
            private int nextCandidateIndex;
            @Nullable
            private TrinityPlanningDiagnostic bestDiagnostic;

            private ProducerChoiceFrame(
                                        TrinityStronglyConnectedComponent outputComponent,
                                        AEKey key,
                                        BigInteger outputDemand,
                                        boolean crossBoundaryInput,
                                        List<TrinityPatternVariant> candidates,
                                        SearchCursor continuation,
                                        int checkpoint) {
                this.outputComponent = outputComponent;
                this.key = key;
                this.outputDemand = outputDemand;
                this.crossBoundaryInput = crossBoundaryInput;
                this.candidates = candidates;
                this.continuation = continuation;
                this.checkpoint = checkpoint;
            }

            private void recordFirstFailure(TrinityPlanningDiagnostic diagnostic) {
                if (this.bestDiagnostic == null) {
                    this.bestDiagnostic = diagnostic;
                }
            }
        }

        private sealed interface SearchAction
                                              permits ContinueAction, ChoiceAction, CompleteAction, FailureAction {}

        private record ContinueAction(SearchCursor cursor) implements SearchAction {}

        private record ChoiceAction(ProducerChoiceFrame choice) implements SearchAction {}

        private record CompleteAction(TrinityGraphDemandSolution solution) implements SearchAction {}

        private record FailureAction(TrinityPlanningDiagnostic diagnostic) implements SearchAction {}

        private static final class MutationJournal {

            private final ArrayList<Runnable> undoActions = new ArrayList<>();

            private int checkpoint() {
                return this.undoActions.size();
            }

            private void record(Runnable action) {
                this.undoActions.add(action);
            }

            private void rollback(int checkpoint) {
                if (checkpoint < 0 || checkpoint > this.undoActions.size()) {
                    throw new IllegalArgumentException("A Trinity demand rollback checkpoint is invalid");
                }
                for (int index = this.undoActions.size() - 1; index >= checkpoint; index--) {
                    this.undoActions.remove(index).run();
                }
            }
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
     * Shares a single exact route-state allowance across every speculative branch.
     */
    private static final class RouteSearchBudget {

        private final int limit;
        private final TrinityPlanningControl control;
        private int used;

        private RouteSearchBudget(int limit, TrinityPlanningControl control) {
            if (limit <= 0) {
                throw new IllegalArgumentException("A Trinity route-search budget requires a positive limit");
            }
            this.limit = limit;
            this.control = control;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private boolean tryConsume() {
            if (this.used >= this.limit) {
                return false;
            }
            this.used = Math.incrementExact(this.used);
            this.control.recordRouteStates(1);
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
