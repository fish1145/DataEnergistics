package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCycleSequence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicFiringMath;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates primitive productivity and the unique-producer residual DAG before any firing optimisation begins.
 */
final class TrinityDeterministicApplicabilityImpl implements TrinityDeterministicApplicability {

    private final TrinityDeterministicCycleSequence cycleSequence;

    TrinityDeterministicApplicabilityImpl(TrinityDeterministicCycleSequence cycleSequence) {
        if (cycleSequence == null) {
            throw new IllegalArgumentException("A deterministic applicability check requires a cycle resolver");
        }
        this.cycleSequence = cycleSequence;
    }

    @Override
    public TrinityDeterministicApplicabilityResult assess(
                                                          TrinityStronglyConnectedComponent component,
                                                          TrinityCycleDemand demand,
                                                          AEKey reservoir,
                                                          Map<AEKey, BigInteger> available) {
        if (component == null || demand == null || reservoir == null || available == null) {
            throw new IllegalArgumentException("A deterministic applicability request is incomplete");
        }
        Optional<List<TrinityVariantFiring>> primitive = this.cycleSequence.resolve(
                component,
                reservoir,
                available);
        if (primitive.isEmpty()) {
            return TrinityDeterministicApplicabilityResult.skip();
        }
        LinkedHashMap<TrinityPatternVariant, BigInteger> primitiveFirings = TrinityDeterministicFiringMath.aggregate(primitive.orElseThrow());
        Map<AEKey, BigInteger> primitiveNet = TrinityDeterministicFiringMath.netChange(primitiveFirings);
        if (!isProductiveBasis(component, demand, reservoir, primitiveNet)) {
            return TrinityDeterministicApplicabilityResult.skip();
        }
        Optional<TrinityDeterministicResidualTopology> topology = TrinityDeterministicResidualTopology.create(component, reservoir);
        if (topology.isEmpty()) {
            return TrinityDeterministicApplicabilityResult.reject();
        }
        return TrinityDeterministicApplicabilityResult.applicable(new TrinityDeterministicBasis(
                reservoir,
                primitive.orElseThrow(),
                primitiveFirings,
                primitiveNet,
                topology.orElseThrow()));
    }

    private static boolean isProductiveBasis(
                                             TrinityStronglyConnectedComponent component,
                                             TrinityCycleDemand demand,
                                             AEKey reservoir,
                                             Map<AEKey, BigInteger> primitiveNet) {
        if (primitiveNet.getOrDefault(reservoir, TrinityDeterministicFiringMath.ZERO).signum() <= 0) {
            return false;
        }
        if (component.keys().stream()
                .filter(key -> !key.equals(reservoir))
                .anyMatch(key -> primitiveNet.getOrDefault(key, TrinityDeterministicFiringMath.ZERO).signum() != 0)) {
            return false;
        }
        return demand.requiredNetChangeLowerBounds().keySet().stream()
                .noneMatch(key -> primitiveNet.getOrDefault(key, TrinityDeterministicFiringMath.ZERO).signum() < 0);
    }
}
