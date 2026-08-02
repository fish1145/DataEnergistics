package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicComponentPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicComponentPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCyclePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCycleSequence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.TrinityMipCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.TrinityMixedIntegerCycleSolver;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.selector.TrinityPlanningAttemptSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Selection implementation that keeps every general fallback behind the shared three-state opportunity boundary.
 */
final class TrinityCyclePlanSelectorImpl implements TrinityCyclePlanSelector {

    private final TrinityDeterministicCycleSequence deterministicCycleSequence;
    private final TrinityDeterministicCyclePlanner deterministicCyclePlanner;
    private final TrinityDeterministicComponentPlanner deterministicComponentPlanner;
    private final TrinityMixedIntegerCycleSolver mixedIntegerCycleSolver;
    private final TrinityPlanningAttemptSelector planningAttemptSelector;

    TrinityCyclePlanSelectorImpl(TrinityDeterministicCycleSequence deterministicCycleSequence,
                                 TrinityDeterministicCyclePlanner deterministicCyclePlanner,
                                 TrinityDeterministicComponentPlanner deterministicComponentPlanner,
                                 TrinityMixedIntegerCycleSolver mixedIntegerCycleSolver,
                                 TrinityPlanningAttemptSelector planningAttemptSelector) {
        this.deterministicCycleSequence = deterministicCycleSequence;
        this.deterministicCyclePlanner = deterministicCyclePlanner;
        this.deterministicComponentPlanner = deterministicComponentPlanner;
        this.mixedIntegerCycleSolver = mixedIntegerCycleSolver;
        this.planningAttemptSelector = planningAttemptSelector;
    }

    @Override
    public TrinityAlgorithmResult<TrinityCycleSelection> select(
                                                                TrinityStronglyConnectedComponent component,
                                                                TrinityCycleDemand demand,
                                                                Map<AEKey, BigInteger> available,
                                                                Set<AEKey> producibleInputs,
                                                                int maxStates,
                                                                TrinityPlanningControl control) {
        if (component == null || !component.cyclic() || demand == null || available == null ||
                producibleInputs == null || maxStates <= 0 || control == null) {
            throw new IllegalArgumentException("A Trinity cycle selection request is incomplete");
        }
        Map<AEKey, BigInteger> inventory = copyAvailable(available);
        Set<AEKey> producible = Set.copyOf(producibleInputs);
        Optional<ScalarDemand> scalar = scalarDemand(component, demand);
        if (scalar.isPresent()) {
            ScalarDemand request = scalar.orElseThrow();
            Optional<List<TrinityVariantFiring>> deterministicOrder = this.deterministicCycleSequence.resolve(
                    component,
                    request.target(),
                    inventory);
            if (deterministicOrder.isPresent() && completeUniqueRoute(
                    component,
                    deterministicOrder.orElseThrow())) {
                TrinityAlgorithmResult<TrinityCyclePlan> deterministic = this.deterministicCyclePlanner.plan(
                        deterministicOrder.orElseThrow(),
                        request.target(),
                        request.amount(),
                        request.quantityMode(),
                        inventory,
                        maxStates,
                        control);
                if (deterministic.successful()) {
                    return TrinityAlgorithmResult.success(fromScalar(component.index(), deterministic.value()));
                }
                if (deterministic.diagnostic().code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED ||
                        deterministic.diagnostic().code() == TrinityPlanningDiagnosticCode.MIP_TIMEOUT) {
                    return TrinityAlgorithmResult.failure(deterministic.diagnostic());
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
                        control);
        long componentNanos = Math.max(0L, System.nanoTime() - componentStartedNanos);
        return this.planningAttemptSelector.select(
                deterministic,
                plan -> fromDeterministic(component.index(), plan, componentNanos),
                () -> solveMixedInteger(component, demand, inventory, producible, maxStates, control, componentNanos));
    }

    private TrinityAlgorithmResult<TrinityCycleSelection> solveMixedInteger(
                                                                            TrinityStronglyConnectedComponent component,
                                                                            TrinityCycleDemand demand,
                                                                            Map<AEKey, BigInteger> available,
                                                                            Set<AEKey> producibleInputs,
                                                                            int maxStates,
                                                                            TrinityPlanningControl control,
                                                                            long componentNanos) {
        TrinityAlgorithmResult<TrinityMipCyclePlan> mip = this.mixedIntegerCycleSolver.solve(
                component,
                demand,
                available,
                producibleInputs,
                maxStates,
                control);
        if (!mip.successful()) {
            return TrinityAlgorithmResult.failure(mip.diagnostic());
        }
        TrinityMipCyclePlan plan = mip.value();
        return TrinityAlgorithmResult.success(new TrinityCycleSelection(
                component.index(),
                plan.schedule().batches(),
                BigInteger.ONE,
                maximumAmounts(plan.minimumSeed(), plan.externalInputs()),
                plan.initialInputs(),
                plan.netChange(),
                plan.schedule().statesVisited(),
                Math.addExact(componentNanos, plan.solverNanos())));
    }

    private static TrinityCycleSelection fromScalar(int componentIndex, TrinityCyclePlan plan) {
        return new TrinityCycleSelection(
                componentIndex,
                plan.oneCycleOrder(),
                plan.repetitions(),
                plan.minimumSeed(),
                plan.initialInputs(),
                plan.netChange(),
                plan.schedule().statesVisited(),
                0L);
    }

    private static TrinityCycleSelection fromDeterministic(
                                                            int componentIndex,
                                                            TrinityDeterministicComponentPlan plan,
                                                            long componentNanos) {
        return new TrinityCycleSelection(
                componentIndex,
                plan.schedule().batches(),
                BigInteger.ONE,
                plan.minimumSeed(),
                plan.initialInputs(),
                plan.netChange(),
                plan.schedule().statesVisited(),
                componentNanos);
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
        if (demand.finalBalanceLowerBounds().isEmpty()) {
            return Optional.of(new ScalarDemand(
                    net.getKey(),
                    net.getValue(),
                    CraftingQuantityMode.NET_NEW));
        }
        if (demand.finalBalanceLowerBounds().size() != 1 ||
                !demand.finalBalanceLowerBounds().containsKey(net.getKey())) {
            return Optional.empty();
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
                .collect(Collectors.toUnmodifiableSet());
        if (selected.size() != order.size() || !selected.equals(Set.copyOf(component.cycleVariants()))) {
            return false;
        }
        return component.keys().stream().allMatch(key -> component.cycleVariants().stream()
                .filter(variant -> variant.outputs().containsKey(key))
                .limit(2L)
                .count() == 1L);
    }

    private static LinkedHashMap<AEKey, BigInteger> maximumAmounts(
                                                                   Map<AEKey, BigInteger> first,
                                                                   Map<AEKey, BigInteger> second) {
        LinkedHashMap<AEKey, BigInteger> maximum = new LinkedHashMap<>(first);
        second.forEach((key, amount) -> maximum.merge(key, amount, BigInteger::max));
        return maximum;
    }

    private static Map<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("A Trinity cycle inventory amount cannot be negative");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private record ScalarDemand(
                                AEKey target,
                                BigInteger amount,
                                CraftingQuantityMode quantityMode) {}
}
