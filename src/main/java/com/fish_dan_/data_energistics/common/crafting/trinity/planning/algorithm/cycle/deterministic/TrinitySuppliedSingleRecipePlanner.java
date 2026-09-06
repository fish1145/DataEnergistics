package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicDiagnostics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicFiringMath;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed.TrinityCycleSeedRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact affine execution for one recipe whose feedback ingredients have proven external supply. A partial return
 * creates a structural self-loop, but does not create an ordering or integer-search choice. Requested net output still
 * comes entirely from recipe firings. Final withdrawals and finite side inputs remain independently accounted.
 */
final class TrinitySuppliedSingleRecipePlanner {

    private static final TrinityDeterministicRepeatScheduler SCHEDULER = TrinityDeterministicRepeatScheduler.create();

    private TrinitySuppliedSingleRecipePlanner() {}

    static TrinityPlanningAttempt<TrinityDeterministicComponentPlan> plan(
                                                                          TrinityStronglyConnectedComponent component,
                                                                          TrinityCycleDemand demand,
                                                                          Map<AEKey, BigInteger> available,
                                                                          Set<AEKey> producibleInputs,
                                                                          int maxStates,
                                                                          TrinityPlanningControl control) {
        if (component.cycleVariants().size() != 1 || !producibleInputs.containsAll(component.keys())) {
            return TrinityDeterministicDiagnostics.notApplicable();
        }
        var variant = component.cycleVariants().getFirst();
        BigInteger count = BigInteger.ONE;
        for (var bound : demand.requiredNetChangeLowerBounds().entrySet()) {
            BigInteger gain = variant.netChange().getOrDefault(bound.getKey(), BigInteger.ZERO);
            if (gain.signum() <= 0) {
                return TrinityDeterministicDiagnostics.notApplicable();
            }
            count = count.max(TrinityDeterministicFiringMath.ceilDivide(bound.getValue(), gain));
        }
        for (var bound : demand.finalBalanceLowerBounds().entrySet()) {
            AEKey key = bound.getKey();
            if (producibleInputs.contains(key)) {
                continue;
            }
            BigInteger missing = bound.getValue().subtract(available.getOrDefault(key, BigInteger.ZERO));
            if (missing.signum() > 0) {
                BigInteger gain = variant.netChange().getOrDefault(key, BigInteger.ZERO);
                if (gain.signum() <= 0) {
                    return TrinityDeterministicDiagnostics.notApplicable();
                }
                count = count.max(TrinityDeterministicFiringMath.ceilDivide(missing, gain));
            }
        }

        BigInteger repetitions = count;
        List<TrinityVariantFiring> unit = List.of(new TrinityVariantFiring(variant, BigInteger.ONE));
        Map<AEKey, BigInteger> minimumInputs = TrinityCycleSeedRequirement.repeatedMinimumInputs(unit, repetitions);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        variant.netChange().forEach((key, amount) -> net.put(key, amount.multiply(repetitions)));
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> initial = new Object2ObjectLinkedOpenHashMap<>(minimumInputs);
        demand.finalBalanceLowerBounds().forEach((key, amount) -> {
            BigInteger required = amount.subtract(net.getOrDefault(key, BigInteger.ZERO));
            if (required.signum() > 0) {
                initial.merge(key, required, BigInteger::max);
            }
        });
        for (var input : initial.object2ObjectEntrySet()) {
            if (!producibleInputs.contains(input.getKey()) &&
                    input.getValue().compareTo(available.getOrDefault(input.getKey(), BigInteger.ZERO)) > 0) {
                return TrinityDeterministicDiagnostics.notApplicable();
            }
        }
        var scheduled = SCHEDULER.schedule(unit, repetitions, initial, maxStates, control);
        if (!scheduled.successful()) {
            return TrinityPlanningAttempt.terminal(scheduled.diagnostic());
        }
        // This proves feasibility, not a global external-input optimum among larger co-product firing counts.
        return TrinityPlanningAttempt.feasible(new TrinityDeterministicComponentPlan(
                Object2ObjectMaps.singleton(variant, repetitions), minimumInputs, initial, net, scheduled.value()));
    }
}
