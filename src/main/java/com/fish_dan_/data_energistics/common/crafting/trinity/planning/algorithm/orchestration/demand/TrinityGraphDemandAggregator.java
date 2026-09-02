package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof.TrinityCycleUnitProof;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCyclePlanSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticEvidence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticOutcome;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityDiagnosticMaterialAccumulator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
                                                                        TrinityPlanningInventory inventory,
                                                                        TrinityPlanningLimits limits,
                                                                        TrinityPlanningMode mode,
                                                                        TrinityPlanningControl control) {
        if (topology == null || target == null || requestedAmount == null ||
                requestedAmount.signum() <= 0 || quantityMode == null || inventory == null || limits == null ||
                mode == null || control == null) {
            throw new IllegalArgumentException("A Trinity graph demand request is incomplete");
        }
        return new PlanningAccumulator(
                topology,
                target,
                requestedAmount,
                quantityMode,
                inventory,
                limits,
                mode,
                control,
                Map.of(),
                Int2ObjectMaps.emptyMap()).solve();
    }

    /** Resolves demand with quantity-independent cycle and coefficient proofs attached to the compiled structure. */
    public TrinityAlgorithmResult<TrinityGraphDemandSolution> aggregate(
                                                                        TrinityCraftingTopology topology,
                                                                        AEKey target,
                                                                        BigInteger requestedAmount,
                                                                        CraftingQuantityMode quantityMode,
                                                                        TrinityPlanningInventory inventory,
                                                                        TrinityPlanningLimits limits,
                                                                        TrinityPlanningMode mode,
                                                                        TrinityPlanningControl control,
                                                                        Map<AEKey, TrinityCycleUnitProof> cycleUnitProofs,
                                                                        Int2ObjectMap<TrinityMipCoefficientTemplate> cycleMipTemplates) {
        return new PlanningAccumulator(
                topology,
                target,
                requestedAmount,
                quantityMode,
                inventory,
                limits,
                mode,
                control,
                cycleUnitProofs,
                cycleMipTemplates).solve();
    }

    /**
     * Compatibility entry point that retains complete optimisation.
     */
    public TrinityAlgorithmResult<TrinityGraphDemandSolution> aggregate(
                                                                        TrinityCraftingTopology topology,
                                                                        AEKey target,
                                                                        BigInteger requestedAmount,
                                                                        CraftingQuantityMode quantityMode,
                                                                        TrinityPlanningInventory inventory,
                                                                        TrinityPlanningLimits limits,
                                                                        TrinityPlanningControl control) {
        return aggregate(
                topology,
                target,
                requestedAmount,
                quantityMode,
                inventory,
                limits,
                TrinityPlanningMode.FIRST_FEASIBLE,
                control);
    }

    /**
     * Owns the single thread-confined demand state traversed by explicit cursors and reversible producer choices.
     */
    private final class PlanningAccumulator {

        private final TrinityCraftingTopology topology;
        private final AEKey target;
        private final BigInteger requestedAmount;
        private final CraftingQuantityMode quantityMode;
        private final LinkedHashMap<AEKey, BigInteger> inventory;
        private final Set<AEKey> unlimitedInventory;
        private final TrinityPlanningLimits limits;
        private final TrinityPlanningMode mode;
        private final TrinityPlanningControl control;
        private final Map<AEKey, TrinityCycleUnitProof> cycleUnitProofs;
        private final Int2ObjectMap<TrinityMipCoefficientTemplate> cycleMipTemplates;
        private final Map<Integer, Integer> topologicalPositions;
        private final RouteSearchBudget routeSearchBudget;
        private final LinkedHashMap<AEKey, BigInteger> demand = new LinkedHashMap<>();
        private final LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>();
        private final LinkedHashMap<AEKey, InputRequirement> inputShortages = new LinkedHashMap<>();
        private final LinkedHashMap<TrinityPatternVariant, TrinityRankedPatternFiring> acyclicFirings = new LinkedHashMap<>();
        private final ArrayList<TrinityCycleSelection> cycleSolutions = new ArrayList<>();
        private final ArrayList<TrinityCycleDiagnosticEvidence> diagnosticCycles = new ArrayList<>();
        private final ArrayList<TrinityPlanningDiagnostic.PartialPlan> diagnosticMaterials = new ArrayList<>();
        private final LinkedHashMap<AEKey, BigInteger> unresolvedDemands = new LinkedHashMap<>();
        private final LinkedHashSet<Integer> unprovedCycleComponents = new LinkedHashSet<>();
        private final HashMap<Integer, TrinityCycleDiagnosticEvidence> retainedCycleEvidence = new HashMap<>();
        private final LinkedHashMap<Integer, LinkedHashMap<AEKey, BigInteger>> cycleOutputDemands = new LinkedHashMap<>();
        private final MutationJournal mutationJournal = new MutationJournal();
        private int scheduleStates;
        private long mipNanos;
        private boolean diagnosticMode;
        private int diagnosticStartedRouteStates;
        private @Nullable TrinityPlanningDiagnostic diagnosticRootFailure;
        private @Nullable String diagnosticCycleProofStop;

        private PlanningAccumulator(
                                    TrinityCraftingTopology topology,
                                    AEKey target,
                                    BigInteger requestedAmount,
                                    CraftingQuantityMode quantityMode,
                                    TrinityPlanningInventory available,
                                    TrinityPlanningLimits limits,
                                    TrinityPlanningMode mode,
                                    TrinityPlanningControl control,
                                    Map<AEKey, TrinityCycleUnitProof> cycleUnitProofs,
                                    Int2ObjectMap<TrinityMipCoefficientTemplate> cycleMipTemplates) {
            this.topology = topology;
            this.target = target;
            this.requestedAmount = requestedAmount;
            this.quantityMode = quantityMode;
            this.inventory = new LinkedHashMap<>(available.finiteAmounts());
            this.unlimitedInventory = available.unlimitedKeys();
            this.limits = limits;
            this.mode = mode;
            this.control = control;
            this.cycleUnitProofs = cycleUnitProofs;
            this.cycleMipTemplates = cycleMipTemplates;
            this.topologicalPositions = topologicalPositions(topology);
            this.routeSearchBudget = new RouteSearchBudget(limits.maxScheduleStates(), control);
            this.demand.put(target, requestedAmount);
        }

        private TrinityAlgorithmResult<TrinityGraphDemandSolution> solve() {
            TrinityAlgorithmResult<TrinityGraphDemandSolution> result = solvePass();
            if (result.successful() || this.diagnosticMode ||
                    result.diagnostic().code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED ||
                    this.control.cancellationRequested()) {
                return result;
            }
            if (this.mode == TrinityPlanningMode.OPTIMAL && this.control.deadlineExceeded()) {
                LinkedHashMap<String, String> metadata = new LinkedHashMap<>(result.diagnostic().metadata());
                metadata.put("phase", "graph");
                metadata.put("priorCode", result.diagnostic().code().name());
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        Component.translatable(TIMEOUT_KEY),
                        metadata,
                        result.diagnostic().detail()));
            }
            this.mutationJournal.rollback(0);
            this.diagnosticMode = true;
            this.diagnosticRootFailure = result.diagnostic();
            this.diagnosticStartedRouteStates = this.routeSearchBudget.used();
            result.diagnostic().cycleEvidence().forEach(evidence -> this.retainedCycleEvidence.putIfAbsent(
                    evidence.componentIndex(),
                    evidence));
            return solvePass();
        }

        private TrinityAlgorithmResult<TrinityGraphDemandSolution> solvePass() {
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
                return failureAction(cancelled());
            }
            if (state == StopState.DEADLINE_EXCEEDED &&
                    (!this.diagnosticMode || this.mode == TrinityPlanningMode.OPTIMAL)) {
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
            if (this.diagnosticMode) {
                return advanceDiagnosticCycle(component, cursor.position());
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
            boolean unresolvedCycleOutput = this.cycleOutputDemands.values().stream()
                    .flatMap(amounts -> amounts.values().stream())
                    .anyMatch(amount -> amount.signum() > 0);
            if (unresolvedCycleOutput && !this.diagnosticMode) {
                return failed(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        INTERNAL_ERROR_KEY,
                        Map.of());
            }
            if (unresolvedCycleOutput) {
                this.cycleOutputDemands.values().forEach(amounts -> amounts.forEach(
                        this::recordUnresolvedMaximum));
            }
            for (Map.Entry<AEKey, BigInteger> remaining : this.demand.entrySet()) {
                BigInteger required = remaining.getValue().max(BigInteger.ZERO);
                if (required.signum() > 0) {
                    if (this.diagnosticMode && !this.topology.variantsByOutputKey()
                            .getOrDefault(remaining.getKey(), List.of())
                            .isEmpty()) {
                        recordUnresolvedMaximum(remaining.getKey(), required);
                        putState(this.demand, remaining.getKey(), BigInteger.ZERO);
                        continue;
                    }
                    BigInteger reserved = reserveFromInventory(remaining.getKey(), required);
                    BigInteger missing = required.subtract(reserved);
                    if (missing.signum() > 0) {
                        recordShortage(remaining.getKey(), required, reserved, missing);
                    }
                    putState(this.demand, remaining.getKey(), BigInteger.ZERO);
                }
            }
            if (this.diagnosticMode) {
                return diagnosticFailure();
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
            if (this.diagnosticMode) {
                if (!this.routeSearchBudget.tryConsume()) {
                    recordUnresolvedMaximum(key, outputDemand);
                    mergeState(this.demand, key, outputDemand.negate());
                    return new ContinueAction(continuation);
                }
                TrinityAlgorithmResult<StepSuccess> applied = applyProducerChoice(
                        component,
                        key,
                        candidates.get(0),
                        outputDemand,
                        false);
                if (!applied.successful()) {
                    recordUnresolvedMaximum(key, outputDemand);
                    mergeState(this.demand, key, outputDemand.negate());
                }
                return new ContinueAction(continuation);
            }
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
            Optional<CyclePreparation> prepared = prepareCycleRequest(component);
            if (prepared.isEmpty()) {
                return TrinityAlgorithmResult.success(Optional.empty());
            }
            CyclePreparation request = prepared.orElseThrow();
            TrinityAlgorithmResult<TrinityCycleSelection> solved = selectCycle(component, request);
            if (!solved.successful()) {
                return failedNested(solved.diagnostic());
            }
            TrinityCycleSelection selection = solved.value();
            if (request.demand().requiredNetChangeLowerBounds().entrySet().stream()
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
                    request.internalRequirements(),
                    request.producibleInputs())));
        }

        private Optional<CyclePreparation> prepareCycleRequest(TrinityStronglyConnectedComponent component) {
            LinkedHashMap<AEKey, BigInteger> internalRequirements = new LinkedHashMap<>();
            Map<AEKey, BigInteger> recordedCycleOutputs = this.cycleOutputDemands.get(component.index());
            Map<AEKey, BigInteger> requestedCycleOutputs = recordedCycleOutputs == null ?
                    Map.of() : recordedCycleOutputs;
            boolean requiresCycle = !requestedCycleOutputs.isEmpty();
            for (AEKey key : component.keys()) {
                BigInteger required = positiveDemand(key);
                if (required.signum() <= 0) {
                    continue;
                }
                internalRequirements.put(key, required);
            }
            ObjectOpenHashSet<AEKey> demandedCycleKeys = new ObjectOpenHashSet<>(internalRequirements.keySet());
            demandedCycleKeys.addAll(requestedCycleOutputs.keySet());
            TrinityCycleUnitProof unitProof = selectUnitProof(component, demandedCycleKeys);
            Map<AEKey, BigInteger> retainedSeed = unitProof == null ? Map.of() : unitProof.internalSeed();
            for (Map.Entry<AEKey, BigInteger> requirement : internalRequirements.entrySet()) {
                AEKey key = requirement.getKey();
                BigInteger usefulInventory = availableUpTo(
                        key,
                        requirement.getValue().add(retainedSeed.getOrDefault(key, BigInteger.ZERO)));
                BigInteger safeSurplus = usefulInventory
                        .subtract(retainedSeed.getOrDefault(key, BigInteger.ZERO))
                        .max(BigInteger.ZERO);
                if (key.equals(this.target) || safeSurplus.compareTo(requirement.getValue()) < 0) {
                    requiresCycle = true;
                }
            }
            if (!requiresCycle) {
                internalRequirements.forEach((key, required) -> {
                    BigInteger reserved = reserveFromInventory(key, required);
                    mergeState(this.demand, key, reserved.negate());
                });
                return Optional.empty();
            }

            LinkedHashMap<AEKey, BigInteger> settledWithdrawals = new LinkedHashMap<>();
            LinkedHashMap<AEKey, BigInteger> terminalBalances = new LinkedHashMap<>();
            LinkedHashMap<AEKey, BigInteger> requiredNetChanges = new LinkedHashMap<>();
            for (Map.Entry<AEKey, BigInteger> requirement : internalRequirements.entrySet()) {
                AEKey key = requirement.getKey();
                BigInteger required = requirement.getValue();
                if (key.equals(this.target) && this.quantityMode == CraftingQuantityMode.NET_NEW) {
                    merge(requiredNetChanges, key, required);
                    continue;
                }
                if (key.equals(this.target)) {
                    terminalBalances.put(key, required);
                } else {
                    settledWithdrawals.put(key, required);
                }
                BigInteger shortage = required
                        .subtract(availableUpTo(key, required))
                        .max(BigInteger.ZERO);
                if (key.equals(this.target)) {
                    shortage = shortage.max(BigInteger.ONE);
                }
                if (shortage.signum() > 0) {
                    requiredNetChanges.put(key, shortage);
                }
            }
            requestedCycleOutputs.forEach((key, amount) -> {
                merge(settledWithdrawals, key, amount);
                merge(requiredNetChanges, key, amount);
            });
            TrinityCycleDemand cycleDemand = new TrinityCycleDemand(
                    settledWithdrawals,
                    terminalBalances,
                    requiredNetChanges,
                    this.quantityMode == CraftingQuantityMode.NET_NEW && component.keys().contains(this.target) ?
                            Set.of(this.target) : Set.of());
            if (!retainedSeed.isEmpty()) {
                cycleDemand = cycleDemand.withRetainedSeed(retainedSeed);
            }
            Set<AEKey> producibleInputs = producibleInputs(component);
            return Optional.of(new CyclePreparation(
                    internalRequirements,
                    cycleDemand,
                    producibleInputs,
                    unitProof));
        }

        private @Nullable TrinityCycleUnitProof selectUnitProof(
                                                                TrinityStronglyConnectedComponent component,
                                                                Set<AEKey> demandedKeys) {
            for (AEKey key : component.keys()) {
                if (demandedKeys.contains(key)) {
                    TrinityCycleUnitProof proof = this.cycleUnitProofs.get(key);
                    if (proof != null) {
                        return proof.instantiate(this.inventory, component.keys());
                    }
                }
            }
            return null;
        }

        private TrinityAlgorithmResult<TrinityCycleSelection> selectCycle(
                                                                          TrinityStronglyConnectedComponent component,
                                                                          CyclePreparation request) {
            TrinityMipCoefficientTemplate template = this.cycleMipTemplates.get(component.index());
            if (template == null) {
                return TrinityGraphDemandAggregator.this.cyclePlanSelector.select(
                        component,
                        request.demand(),
                        this.inventory,
                        request.producibleInputs(),
                        this.limits.maxScheduleStates(),
                        this.mode,
                        this.control);
            }
            return TrinityGraphDemandAggregator.this.cyclePlanSelector.select(
                    component,
                    request.demand(),
                    this.inventory,
                    request.producibleInputs(),
                    this.limits.maxScheduleStates(),
                    this.mode,
                    this.control,
                    request.unitProof(),
                    template);
        }

        private SearchAction advanceDiagnosticCycle(
                                                    TrinityStronglyConnectedComponent component,
                                                    int position) {
            Optional<CyclePreparation> prepared = prepareCycleRequest(component);
            if (prepared.isEmpty()) {
                return new ContinueAction(new ComponentCursor(position - 1));
            }
            CyclePreparation request = prepared.orElseThrow();
            TrinityCycleDiagnosticEvidence evidence = this.retainedCycleEvidence.get(component.index());
            if (evidence != null && !satisfies(evidence, request.demand())) {
                evidence = null;
            }
            TrinityPlanningDiagnostic cycleFailure = null;
            if (evidence == null) {
                TrinityAlgorithmResult<TrinityCycleSelection> selected = selectCycle(component, request);
                if (selected.successful()) {
                    evidence = TrinityCycleDiagnosticEvidence.fromSelection(selected.value(), request.demand());
                } else {
                    cycleFailure = selected.diagnostic();
                    if (cycleFailure.code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED ||
                            this.control.cancellationRequested()) {
                        return failureAction(cancelled());
                    }
                    evidence = cycleFailure.cycleEvidence().stream()
                            .filter(candidate -> candidate.componentIndex() == component.index())
                            .filter(candidate -> satisfies(candidate, request.demand()))
                            .findFirst()
                            .orElse(null);
                }
            }
            if (evidence == null) {
                Map<AEKey, BigInteger> coveredMissing = Map.of();
                if (cycleFailure != null) {
                    Optional<TrinityPlanningDiagnostic.PartialPlan> partial = cycleFailure.partialPlan();
                    if (partial.isPresent()) {
                        TrinityPlanningDiagnostic.PartialPlan retained = partial.orElseThrow();
                        coveredMissing = retained.missingItems();
                        retainDiagnosticMaterial(retained);
                    }
                    if (this.diagnosticCycleProofStop == null) {
                        this.diagnosticCycleProofStop = cycleFailure.metadata().getOrDefault(
                                "diagnosticCycleProofStop",
                                cycleFailure.code().name());
                    }
                }
                Map<AEKey, BigInteger> provedMissing = coveredMissing;
                request.internalRequirements().forEach((key, amount) -> recordResidualUnresolved(
                        key,
                        amount,
                        provedMissing));
                this.cycleOutputDemands
                        .getOrDefault(component.index(), new LinkedHashMap<>())
                        .forEach((key, amount) -> recordResidualUnresolved(key, amount, provedMissing));
                request.internalRequirements().keySet().forEach(key -> putState(this.demand, key, BigInteger.ZERO));
                if (this.cycleOutputDemands.containsKey(component.index())) {
                    putState(this.cycleOutputDemands, component.index(), new LinkedHashMap<>());
                }
                this.unprovedCycleComponents.add(component.index());
                return new ContinueAction(new ComponentCursor(position - 1));
            }

            TrinityCycleDiagnosticOutcome outcome = TrinityCycleDiagnosticOutcome.create(
                    evidence,
                    this.inventory,
                    request.producibleInputs());
            for (Map.Entry<AEKey, BigInteger> actual : outcome.actualInputs().entrySet()) {
                BigInteger reserved = reserveFromInventory(actual.getKey(), actual.getValue());
                if (!reserved.equals(actual.getValue())) {
                    return failureAction(failed(
                            TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                            INTERNAL_ERROR_KEY,
                            Map.of("component", Integer.toString(component.index()))));
                }
            }
            outcome.inputRequirements().forEach((key, requirement) -> recordShortage(
                    key,
                    requirement.required(),
                    requirement.available(),
                    requirement.missing()));
            outcome.boundaryInputs().forEach((key, amount) -> mergeState(this.demand, key, amount));
            this.diagnosticCycles.add(evidence);
            setScheduleStates(Math.addExact(this.scheduleStates, evidence.scheduleStates()));
            setMipNanos(Math.addExact(this.mipNanos, evidence.mipNanos()));
            request.internalRequirements().keySet().forEach(key -> putState(this.demand, key, BigInteger.ZERO));
            if (this.cycleOutputDemands.containsKey(component.index())) {
                putState(this.cycleOutputDemands, component.index(), new LinkedHashMap<>());
            }
            return new ContinueAction(new ComponentCursor(position - 1));
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
            if (this.unlimitedInventory.contains(key)) {
                reserveFromInventory(key, required);
                return new ContinueAction(continuation);
            }
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
            inputs.stream().filter(this.unlimitedInventory::contains).forEach(producible::add);
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
            if (this.unlimitedInventory.contains(key)) {
                mergeState(this.initialInputs, key, required);
                return required;
            }
            BigInteger available = this.inventory.getOrDefault(key, BigInteger.ZERO);
            BigInteger reserved = required.min(available);
            if (reserved.signum() > 0) {
                BigInteger remaining = available.subtract(reserved);
                putState(this.inventory, key, remaining);
                mergeState(this.initialInputs, key, reserved);
            }
            return reserved;
        }

        private BigInteger availableUpTo(AEKey key, BigInteger usefulUpper) {
            return this.unlimitedInventory.contains(key) ?
                    usefulUpper : this.inventory.getOrDefault(key, BigInteger.ZERO).min(usefulUpper);
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
            if (diagnostic.code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED) {
                return TrinityAlgorithmResult.failure(diagnostic);
            }
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
            TrinityPlanningDiagnostic.Detail detail = diagnostic.cycleEvidence().isEmpty() ?
                    progress :
                    new TrinityPlanningDiagnostic.CompositeEvidence(progress, diagnostic.cycleEvidence());
            return TrinityAlgorithmResult.failure(diagnostic.withDetail(detail));
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
            List<TrinityVariantFiring> selectedFirings = new ObjectArrayList<>();
            this.acyclicFirings.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<TrinityPatternVariant, TrinityRankedPatternFiring>>comparingInt(
                            entry -> entry.getValue().rank()).thenComparing(Map.Entry::getKey))
                    .forEach(entry -> selectedFirings.add(new TrinityVariantFiring(
                            entry.getKey(), entry.getValue().count())));
            this.acyclicFirings.forEach((variant, firing) -> mergeScaled(
                    emitted,
                    variant.outputs(),
                    firing.count()));
            IntSet diagnosticComponents = new IntOpenHashSet();
            this.diagnosticCycles.forEach(cycle -> diagnosticComponents.add(cycle.componentIndex()));
            for (TrinityCycleSelection cycle : this.cycleSolutions) {
                mergeFiringOutputs(emitted, cycle.prefixOrder(), BigInteger.ONE);
                mergeFiringOutputs(emitted, cycle.localOrder(), cycle.repetitions());
                mergeFiringOutputs(emitted, cycle.suffixOrder(), BigInteger.ONE);
                if (!diagnosticComponents.contains(cycle.componentIndex())) {
                    selectedFirings.addAll(cycle.prefixOrder());
                    cycle.localOrder().forEach(firing -> selectedFirings.add(new TrinityVariantFiring(
                            firing.variant(), firing.count().multiply(cycle.repetitions()))));
                    selectedFirings.addAll(cycle.suffixOrder());
                }
            }
            this.diagnosticCycles.forEach(cycle -> cycle.emittedItems().forEach(
                    (key, amount) -> emitted.merge(key, amount, BigInteger::add)));

            LinkedHashMap<AEKey, BigInteger> unresolved = new LinkedHashMap<>();
            this.inputShortages.forEach((key, requirement) -> unresolved.put(key, requirement.missing()));
            this.unresolvedDemands.forEach((key, amount) -> unresolved.merge(key, amount, BigInteger::max));
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
            TrinityPlanningDiagnostic.PartialPlan base = new TrinityPlanningDiagnostic.PartialPlan(
                    this.initialInputs,
                    emitted,
                    unresolved,
                    exactRequirements,
                    selectedFirings);
            TrinityDiagnosticMaterialAccumulator accumulator = TrinityDiagnosticMaterialAccumulator.create(base);
            this.diagnosticMaterials.forEach(accumulator::add);
            return accumulator.snapshot();
        }

        private TrinityAlgorithmResult<TrinityGraphDemandSolution> diagnosticFailure() {
            TrinityPlanningDiagnostic root = this.diagnosticRootFailure;
            if (root == null) {
                throw new IllegalStateException("A Trinity diagnostic graph pass requires its original failure");
            }
            TrinityPlanningDiagnostic.PartialPlan materials = partialPlan();
            if (materials.missingItems().isEmpty()) {
                recordUnresolvedMaximum(this.target, this.requestedAmount);
                materials = partialPlan();
            }
            TrinityPlanningDiagnostic.PartialPlan completeMaterials = materials;
            int exactKinds = completeMaterials.inputRequirements().size();
            int unresolvedKinds = (int) completeMaterials.missingItems().keySet().stream()
                    .filter(key -> !completeMaterials.inputRequirements().containsKey(key))
                    .count();
            LinkedHashMap<String, String> metadata = new LinkedHashMap<>(root.metadata());
            metadata.put("diagnosticExactShortageKinds", Integer.toString(exactKinds));
            metadata.put("diagnosticUnresolvedKinds", Integer.toString(unresolvedKinds));
            metadata.put("diagnosticProvedCycles", Integer.toString(this.diagnosticCycles.size()));
            metadata.put(
                    "diagnosticUnprovedCycleComponents",
                    Integer.toString(this.unprovedCycleComponents.size()));
            metadata.put(
                    "diagnosticContinuationStates",
                    Integer.toString(Math.max(
                            0,
                            this.routeSearchBudget.used() - this.diagnosticStartedRouteStates)));
            if (this.diagnosticCycleProofStop != null) {
                metadata.put("diagnosticCycleProofStop", this.diagnosticCycleProofStop);
            }
            metadata.put("shortageKinds", Integer.toString(exactKinds));
            completeMaterials.inputRequirements().entrySet().stream().findFirst().ifPresent(shortage -> {
                metadata.put("key", shortage.getKey().toString());
                metadata.put("required", shortage.getValue().required().toString());
                metadata.put("available", shortage.getValue().available().toString());
                metadata.put("missing", shortage.getValue().missing().toString());
            });
            TrinityPlanningDiagnostic.Detail detail = this.diagnosticCycles.isEmpty() ?
                    completeMaterials :
                    new TrinityPlanningDiagnostic.CompositeEvidence(completeMaterials, this.diagnosticCycles);
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    root.code(),
                    root.message(),
                    metadata,
                    detail));
        }

        private void retainDiagnosticMaterial(TrinityPlanningDiagnostic.PartialPlan partial) {
            partial.usedItems().forEach((key, amount) -> {
                BigInteger reserved = reserveFromInventory(key, amount);
                if (!reserved.equals(amount)) {
                    throw new IllegalStateException(
                            "A retained Trinity diagnostic branch exceeded its captured inventory");
                }
            });
            this.diagnosticMaterials.add(new TrinityPlanningDiagnostic.PartialPlan(
                    Map.of(),
                    partial.emittedItems(),
                    partial.missingItems(),
                    partial.inputRequirements(),
                    partial.selectedFirings()));
        }

        private void recordUnresolvedMaximum(AEKey key, BigInteger amount) {
            if (amount.signum() <= 0) {
                return;
            }
            putState(
                    this.unresolvedDemands,
                    key,
                    this.unresolvedDemands.getOrDefault(key, BigInteger.ZERO).max(amount));
        }

        private void recordResidualUnresolved(
                                              AEKey key,
                                              BigInteger required,
                                              Map<AEKey, BigInteger> provedMissing) {
            BigInteger unresolved = required
                    .subtract(provedMissing.getOrDefault(key, BigInteger.ZERO))
                    .max(BigInteger.ZERO);
            recordUnresolvedMaximum(key, unresolved);
        }

        private static boolean satisfies(
                                         TrinityCycleDiagnosticEvidence evidence,
                                         TrinityCycleDemand demand) {
            if (!evidence.demand().equals(demand)) {
                return false;
            }
            for (Map.Entry<AEKey, BigInteger> required : demand.requiredNetChangeLowerBounds().entrySet()) {
                if (evidence.netChange().getOrDefault(required.getKey(), BigInteger.ZERO)
                        .compareTo(required.getValue()) < 0) {
                    return false;
                }
            }
            for (Map.Entry<AEKey, BigInteger> required : demand.finalBalanceLowerBounds().entrySet()) {
                BigInteger finalBalance = evidence.initialInputs().getOrDefault(required.getKey(), BigInteger.ZERO)
                        .add(evidence.netChange().getOrDefault(required.getKey(), BigInteger.ZERO));
                if (finalBalance.compareTo(required.getValue()) < 0) {
                    return false;
                }
            }
            return true;
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

    private record CyclePreparation(
                                    Map<AEKey, BigInteger> internalRequirements,
                                    TrinityCycleDemand demand,
                                    Set<AEKey> producibleInputs,
                                    @Nullable TrinityCycleUnitProof unitProof) {}

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
