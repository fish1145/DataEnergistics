package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicComponentPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicComponentPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCyclePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCycleSequence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof.TrinityCycleUnitProof;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed.TrinityCycleSeedRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns the ordered scalar, deterministic-component and authoritative joint-cycle selection policy.
 * <p>
 * Selection implementation that keeps the single joint-cycle fallback behind the shared three-state opportunity
 * boundary.
 */
public final class TrinityCyclePlanSelector {

    /**
     * @return selector composed from exact deterministic and joint-cycle implementations
     */
    public static TrinityCyclePlanSelector create() {
        return new TrinityCyclePlanSelector(
                TrinityDeterministicCycleSequence.create(),
                TrinityDeterministicCyclePlanner.create(),
                TrinityDeterministicComponentPlanner.create(),
                TrinityJointCyclePlanner.create());
    }

    private final TrinityDeterministicCycleSequence deterministicCycleSequence;
    private final TrinityDeterministicCyclePlanner deterministicCyclePlanner;
    private final TrinityDeterministicComponentPlanner deterministicComponentPlanner;
    private final TrinityJointCyclePlanner jointCyclePlanner;

    TrinityCyclePlanSelector(TrinityDeterministicCycleSequence deterministicCycleSequence,
                             TrinityDeterministicCyclePlanner deterministicCyclePlanner,
                             TrinityDeterministicComponentPlanner deterministicComponentPlanner,
                             TrinityJointCyclePlanner jointCyclePlanner) {
        this.deterministicCycleSequence = deterministicCycleSequence;
        this.deterministicCyclePlanner = deterministicCyclePlanner;
        this.deterministicComponentPlanner = deterministicComponentPlanner;
        this.jointCyclePlanner = jointCyclePlanner;
    }

    /**
     * Selects one fully executable cycle representation under the shared planning bounds.
     *
     * @param component        immutable cyclic SCC
     * @param demand           complete component demand
     * @param available        non-negative inventory snapshot
     * @param producibleInputs inputs predecessor graph stages may provide
     * @param maxStates        positive compressed-search bound
     * @param control          cooperative cancellation and deadline boundary
     * @return selected executable cycle or stable terminal diagnostic
     */
    public TrinityAlgorithmResult<TrinityCycleSelection> select(
                                                                TrinityStronglyConnectedComponent component,
                                                                TrinityCycleDemand demand,
                                                                Map<AEKey, BigInteger> available,
                                                                Set<AEKey> producibleInputs,
                                                                int maxStates,
                                                                TrinityPlanningMode mode,
                                                                TrinityPlanningControl control) {
        if (!component.cyclic() || maxStates <= 0) {
            throw new IllegalArgumentException("A Trinity cycle selection request is incomplete");
        }
        TrinityMipCoefficientTemplate coefficientTemplate = TrinityMipCoefficientTemplate.create(
                component.cycleVariants(),
                component.keys());
        return selectRetainingSeed(
                component,
                demand,
                available,
                producibleInputs,
                maxStates,
                mode,
                control,
                null,
                coefficientTemplate);
    }

    /** Selects with cached unit and sparse coefficient proofs from the compiled target structure. */
    public TrinityAlgorithmResult<TrinityCycleSelection> select(
                                                                TrinityStronglyConnectedComponent component,
                                                                TrinityCycleDemand demand,
                                                                Map<AEKey, BigInteger> available,
                                                                Set<AEKey> producibleInputs,
                                                                int maxStates,
                                                                TrinityPlanningMode mode,
                                                                TrinityPlanningControl control,
                                                                @Nullable TrinityCycleUnitProof unitProof,
                                                                TrinityMipCoefficientTemplate coefficientTemplate) {
        return selectRetainingSeed(
                component,
                demand,
                available,
                producibleInputs,
                maxStates,
                mode,
                control,
                unitProof,
                coefficientTemplate);
    }

    private TrinityAlgorithmResult<TrinityCycleSelection> selectRetainingSeed(
                                                                              TrinityStronglyConnectedComponent component,
                                                                              TrinityCycleDemand demand,
                                                                              Map<AEKey, BigInteger> inventory,
                                                                              Set<AEKey> producibleInputs,
                                                                              int maxStates,
                                                                              TrinityPlanningMode mode,
                                                                              TrinityPlanningControl control,
                                                                              @Nullable TrinityCycleUnitProof unitProof,
                                                                              TrinityMipCoefficientTemplate coefficientTemplate) {
        TrinityCycleDemand refinedDemand = demand;
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> retainedSeed = new Object2ObjectLinkedOpenHashMap<>();
        if (unitProof != null) {
            retainedSeed.putAll(unitProof.internalSeed());
        }
        // Replenishable inputs are charged to this execution, not retained as an ever-growing restart reserve.
        retainedSeed.keySet().removeAll(producibleInputs);
        int remainingStates = maxStates;
        int accumulatedStates = 0;
        long accumulatedNanos = 0L;
        int seedRefinementPasses = 0;
        TrinityPlanQuality retainedQuality = TrinityPlanQuality.PROVED_OPTIMAL;
        Set<AEKey> internalKeys = new ObjectOpenHashSet<>(component.keys());
        while (true) {
            TrinityAlgorithmResult<TrinityCycleSelection> selectedResult = selectOnce(
                    component,
                    refinedDemand,
                    inventory,
                    producibleInputs,
                    remainingStates,
                    mode,
                    control,
                    unitProof,
                    coefficientTemplate);
            if (!selectedResult.successful()) {
                return selectedResult;
            }
            TrinityCycleSelection selected = selectedResult.value();
            if (!satisfiesDemand(selected, refinedDemand)) {
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        Component.translatable("gui.data_energistics.trinity_planning.diagnostic.internal_error"),
                        Map.of(
                                "component", Integer.toString(component.index()),
                                "phase", "seed_retention_demand_validation")));
            }
            accumulatedStates = Math.addExact(accumulatedStates, selected.scheduleStates());
            accumulatedNanos = Math.addExact(accumulatedNanos, selected.mipNanos());
            if (selected.quality() == TrinityPlanQuality.VERIFIED_FEASIBLE) {
                retainedQuality = TrinityPlanQuality.VERIFIED_FEASIBLE;
            }
            TrinityCycleSeedRequirement.fromSelection(selected, internalKeys)
                    .amounts()
                    .forEach((key, amount) -> {
                        if (!producibleInputs.contains(key)) {
                            retainedSeed.merge(key, amount, BigInteger::max);
                        }
                    });
            TrinityCycleDemand nextDemand = demand.withRetainedSeed(retainedSeed);
            if (nextDemand.equals(refinedDemand) || satisfiesFinalBounds(selected, nextDemand)) {
                if (control.cancellationRequested()) {
                    return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                            TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                            Component.translatable("gui.data_energistics.trinity_planning.diagnostic.cancelled"),
                            Map.of()));
                }
                return TrinityAlgorithmResult.success(new TrinityCycleSelection(
                        selected.componentIndex(),
                        selected.prefixOrder(),
                        selected.localOrder(),
                        selected.repetitions(),
                        selected.suffixOrder(),
                        selected.minimumSeed(),
                        selected.initialInputs(),
                        selected.netChange(),
                        selected.exportableNet(),
                        accumulatedStates,
                        accumulatedNanos,
                        retainedQuality,
                        retainedSeed,
                        seedRefinementPasses));
            }
            int chargedStates = Math.max(1, selected.scheduleStates());
            if (selected.scheduleStates() == 0) {
                accumulatedStates = Math.addExact(accumulatedStates, 1);
            }
            seedRefinementPasses = Math.incrementExact(seedRefinementPasses);
            remainingStates -= chargedStates;
            if (remainingStates <= 0) {
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                        Component.translatable(
                                "gui.data_energistics.trinity_planning.mip.schedule_search_limit"),
                        Map.of(
                                "limit", Integer.toString(maxStates),
                                "states", Integer.toString(maxStates),
                                "phase", "seed_retention")));
            }
            refinedDemand = nextDemand;
        }
    }

    private static boolean satisfiesFinalBounds(
                                                TrinityCycleSelection selection,
                                                TrinityCycleDemand demand) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> finalBalances = new Object2ObjectLinkedOpenHashMap<>(selection.initialInputs());
        selection.netChange().forEach((key, amount) -> finalBalances.merge(key, amount, BigInteger::add));
        return demand.finalBalanceLowerBounds().entrySet().stream().allMatch(
                entry -> finalBalances.getOrDefault(entry.getKey(), BigInteger.ZERO)
                        .compareTo(entry.getValue()) >= 0);
    }

    private static boolean satisfiesDemand(
                                           TrinityCycleSelection selection,
                                           TrinityCycleDemand demand) {
        if (selection.netChange().values().stream().allMatch(amount -> amount.signum() == 0) ||
                demand.requiredNetChangeLowerBounds().entrySet().stream().anyMatch(
                        entry -> selection.netChange().getOrDefault(entry.getKey(), BigInteger.ZERO)
                                .compareTo(entry.getValue()) < 0)) {
            return false;
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> finalBalances = new Object2ObjectLinkedOpenHashMap<>(selection.initialInputs());
        selection.netChange().forEach((key, amount) -> finalBalances.merge(key, amount, BigInteger::add));
        return finalBalances.values().stream().noneMatch(amount -> amount.signum() < 0) &&
                demand.finalBalanceLowerBounds().entrySet().stream().allMatch(
                        entry -> finalBalances.getOrDefault(entry.getKey(), BigInteger.ZERO)
                                .compareTo(entry.getValue()) >= 0);
    }

    private TrinityAlgorithmResult<TrinityCycleSelection> selectOnce(
                                                                     TrinityStronglyConnectedComponent component,
                                                                     TrinityCycleDemand demand,
                                                                     Map<AEKey, BigInteger> inventory,
                                                                     Set<AEKey> producible,
                                                                     int maxStates,
                                                                     TrinityPlanningMode mode,
                                                                     TrinityPlanningControl control,
                                                                     @Nullable TrinityCycleUnitProof unitProof,
                                                                     TrinityMipCoefficientTemplate coefficientTemplate) {
        Optional<ScalarDemand> scalar = scalarDemand(component, demand);
        if (scalar.isPresent()) {
            ScalarDemand request = scalar.orElseThrow();
            Optional<List<TrinityVariantFiring>> deterministicOrder = unitProof != null &&
                    unitProof.reservoir().equals(request.target()) ?
                            Optional.of(unitProof.order()) :
                            this.deterministicCycleSequence.resolve(component, request.target(), inventory);
            if (deterministicOrder.isPresent() && completeUniqueRoute(
                    component,
                    deterministicOrder.orElseThrow())) {
                TrinityAlgorithmResult<TrinityCyclePlan> deterministic = this.deterministicCyclePlanner.plan(
                        component.index(),
                        demand,
                        deterministicOrder.orElseThrow(),
                        request.target(),
                        request.amount(),
                        request.quantityMode(),
                        inventory,
                        producible,
                        maxStates,
                        control);
                if (deterministic.successful()) {
                    TrinityCycleSelection selected = fromScalar(component, deterministic.value());
                    if (satisfiesDemand(selected, demand)) {
                        return TrinityAlgorithmResult.success(selected);
                    }
                } else {
                    if (deterministic.diagnostic().code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED ||
                            deterministic.diagnostic().code() == TrinityPlanningDiagnosticCode.MIP_TIMEOUT) {
                        return TrinityAlgorithmResult.failure(deterministic.diagnostic());
                    }
                    if (deterministic.diagnostic().code() == TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT &&
                            !deterministic.diagnostic().cycleEvidence().isEmpty()) {
                        return TrinityAlgorithmResult.failure(deterministic.diagnostic());
                    }
                }
            }
        }

        long componentStartedNanos = System.nanoTime();
        TrinityPlanningAttempt<TrinityDeterministicComponentPlan> deterministic = this.deterministicComponentPlanner
                .plan(
                        component,
                        demand,
                        inventory,
                        producible,
                        maxStates,
                        control,
                        unitProof);
        long componentNanos = Math.max(0L, System.nanoTime() - componentStartedNanos);
        return switch (deterministic.kind()) {
            case PROVED_OPTIMAL -> TrinityAlgorithmResult.success(fromDeterministic(
                    component,
                    demand,
                    deterministic.value(),
                    componentNanos,
                    TrinityPlanQuality.PROVED_OPTIMAL));
            case FEASIBLE -> TrinityAlgorithmResult.success(fromDeterministic(
                    component,
                    demand,
                    deterministic.value(),
                    componentNanos,
                    TrinityPlanQuality.VERIFIED_FEASIBLE));
            case NOT_APPLICABLE -> planJointCycle(
                    component,
                    demand,
                    inventory,
                    producible,
                    maxStates,
                    mode,
                    control,
                    componentNanos,
                    coefficientTemplate);
            case TERMINAL -> TrinityAlgorithmResult.failure(deterministic.diagnostic());
        };
    }

    private TrinityAlgorithmResult<TrinityCycleSelection> planJointCycle(
                                                                         TrinityStronglyConnectedComponent component,
                                                                         TrinityCycleDemand demand,
                                                                         Map<AEKey, BigInteger> available,
                                                                         Set<AEKey> producibleInputs,
                                                                         int maxStates,
                                                                         TrinityPlanningMode mode,
                                                                         TrinityPlanningControl control,
                                                                         long componentNanos,
                                                                         TrinityMipCoefficientTemplate coefficientTemplate) {
        TrinityAlgorithmResult<TrinityJointCyclePlan> joint = this.jointCyclePlanner.plan(
                component,
                demand,
                available,
                producibleInputs,
                maxStates,
                mode,
                control,
                coefficientTemplate);
        if (!joint.successful()) {
            return TrinityAlgorithmResult.failure(joint.diagnostic());
        }
        TrinityJointCyclePlan plan = joint.value();
        return TrinityAlgorithmResult.success(new TrinityCycleSelection(
                component.index(),
                List.of(),
                plan.schedule().batches(),
                BigInteger.ONE,
                List.of(),
                maximumAmounts(plan.minimumSeed(), plan.externalInputs()),
                plan.initialInputs(),
                plan.netChange(),
                settledExports(component, demand, plan.netChange()),
                plan.searchStates(),
                Math.addExact(componentNanos, plan.solverNanos()),
                plan.quality(),
                Map.of(),
                0));
    }

    private static TrinityCycleSelection fromScalar(
                                                    TrinityStronglyConnectedComponent component,
                                                    TrinityCyclePlan plan) {
        return new TrinityCycleSelection(
                component.index(),
                List.of(),
                plan.oneCycleOrder(),
                plan.repetitions(),
                List.of(),
                plan.minimumSeed(),
                plan.initialInputs(),
                plan.netChange(),
                positiveAmounts(plan.netChange()),
                plan.schedule().statesVisited(),
                0L,
                TrinityPlanQuality.VERIFIED_FEASIBLE,
                Map.of(),
                0);
    }

    private static TrinityCycleSelection fromDeterministic(
                                                           TrinityStronglyConnectedComponent component,
                                                           TrinityCycleDemand demand,
                                                           TrinityDeterministicComponentPlan plan,
                                                           long componentNanos,
                                                           TrinityPlanQuality quality) {
        var schedule = plan.schedule();
        boolean repeated = schedule.hasRepeatBlock();
        return new TrinityCycleSelection(
                component.index(),
                repeated ? schedule.prefixBatches() : List.of(),
                repeated ? schedule.repeatUnit() : schedule.batches(),
                repeated ? schedule.repeatCount() : BigInteger.ONE,
                repeated ? schedule.suffixBatches() : List.of(),
                plan.minimumSeed(),
                plan.initialInputs(),
                plan.netChange(),
                settledExports(component, demand, plan.netChange()),
                plan.schedule().statesVisited(),
                componentNanos,
                quality,
                Map.of(),
                0);
    }

    private static Optional<ScalarDemand> scalarDemand(
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
        if (demand.netNewKeys().contains(net.getKey()) ||
                !demand.finalBalanceLowerBounds().containsKey(net.getKey())) {
            return Optional.of(new ScalarDemand(
                    net.getKey(),
                    net.getValue(),
                    CraftingQuantityMode.NET_NEW));
        }
        return Optional.of(new ScalarDemand(
                net.getKey(),
                demand.finalBalanceLowerBounds().get(net.getKey()),
                CraftingQuantityMode.FINAL_TOTAL));
    }

    private static boolean completeUniqueRoute(
                                               TrinityStronglyConnectedComponent component,
                                               List<TrinityVariantFiring> order) {
        Set<TrinityPatternVariant> selected = order.stream()
                .map(TrinityVariantFiring::variant)
                .collect(Collectors.toCollection(ObjectOpenHashSet::new));
        if (selected.size() != order.size() ||
                !selected.equals(new ObjectOpenHashSet<>(component.cycleVariants()))) {
            return false;
        }
        return component.keys().stream().allMatch(key -> component.cycleVariants().stream()
                .filter(variant -> variant.outputs().containsKey(key))
                .limit(2L)
                .count() == 1L);
    }

    private static Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> maximumAmounts(
                                                                                    Map<AEKey, BigInteger> first,
                                                                                    Map<AEKey, BigInteger> second) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> maximum = new Object2ObjectLinkedOpenHashMap<>(first);
        second.forEach((key, amount) -> maximum.merge(key, amount, BigInteger::max));
        return maximum;
    }

    private static Map<AEKey, BigInteger> settledExports(
                                                         TrinityStronglyConnectedComponent component,
                                                         TrinityCycleDemand demand,
                                                         Map<AEKey, BigInteger> netChange) {
        Set<AEKey> internalKeys = new ObjectOpenHashSet<>(component.keys());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> exports = new Object2ObjectLinkedOpenHashMap<>();
        netChange.forEach((key, amount) -> {
            if (amount.signum() > 0 &&
                    (!internalKeys.contains(key) ||
                            demand.requiredNetChangeLowerBounds().containsKey(key))) {
                exports.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(exports);
    }

    private static Map<AEKey, BigInteger> positiveAmounts(Map<AEKey, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> positive = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() > 0) {
                positive.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(positive);
    }

    private record ScalarDemand(
                                AEKey target,
                                BigInteger amount,
                                CraftingQuantityMode quantityMode) {}
}
