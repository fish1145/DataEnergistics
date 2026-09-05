package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCycleSequence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicFiringMath;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof.TrinityCycleUnitProof;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Determines whether one reservoir yields the unique productive basis required by the deterministic fast path.
 * <p>
 * Validates primitive productivity and the unique-producer residual DAG before any firing optimisation begins.
 */
public final class TrinityDeterministicApplicability {

    /**
     * Creates the structural assessment using the exact primitive-cycle resolver.
     */
    public static TrinityDeterministicApplicability create(TrinityDeterministicCycleSequence cycleSequence) {
        return new TrinityDeterministicApplicability(cycleSequence);
    }

    private final TrinityDeterministicCycleSequence cycleSequence;

    TrinityDeterministicApplicability(TrinityDeterministicCycleSequence cycleSequence) {
        this.cycleSequence = cycleSequence;
    }

    /**
     * Returns an applicable basis, a reservoir-local miss, or a component-wide structural rejection.
     */
    public TrinityDeterministicApplicabilityResult assess(
                                                          TrinityStronglyConnectedComponent component,
                                                          TrinityCycleDemand demand,
                                                          AEKey reservoir,
                                                          Map<AEKey, BigInteger> available) {
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

    /** Reuses a semantic unit proof while recomputing only its inventory-dependent order and prefix reserve. */
    public TrinityDeterministicApplicabilityResult assess(
                                                          TrinityStronglyConnectedComponent component,
                                                          TrinityCycleDemand demand,
                                                          TrinityCycleUnitProof unitProof,
                                                          Map<AEKey, BigInteger> available) {
        if (!component.keys().contains(unitProof.reservoir()) ||
                !new ObjectOpenHashSet<>(unitProof.firings().keySet())
                        .equals(new ObjectOpenHashSet<>(component.cycleVariants()))) {
            return TrinityDeterministicApplicabilityResult.skip();
        }
        TrinityCycleUnitProof instantiated = unitProof.instantiate(available, component.keys());
        LinkedHashMap<TrinityPatternVariant, BigInteger> firings = TrinityDeterministicFiringMath.aggregate(instantiated.order());
        Map<AEKey, BigInteger> net = TrinityDeterministicFiringMath.netChange(firings);
        if (!firings.equals(instantiated.firings()) || !net.equals(instantiated.netChange()) ||
                !isProductiveBasis(component, demand, instantiated.reservoir(), net)) {
            return TrinityDeterministicApplicabilityResult.skip();
        }
        Optional<TrinityDeterministicResidualTopology> topology = TrinityDeterministicResidualTopology.create(
                component,
                instantiated.reservoir());
        return topology.map(value -> TrinityDeterministicApplicabilityResult.applicable(new TrinityDeterministicBasis(
                instantiated.reservoir(),
                instantiated.order(),
                instantiated.firings(),
                instantiated.netChange(),
                value))).orElseGet(TrinityDeterministicApplicabilityResult::skip);
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
