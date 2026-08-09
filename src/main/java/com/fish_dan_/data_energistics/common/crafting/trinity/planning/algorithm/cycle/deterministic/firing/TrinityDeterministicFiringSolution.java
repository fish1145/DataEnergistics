package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicBasis;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityFiringOptimization;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Exact optimized firing vector and net-change proof derived from one applicable primitive basis.
 *
 * @param basis                  structural basis used by proof assembly
 * @param firings                optimized exact firing vector
 * @param totalNet               exact aggregate net change
 * @param balancePasses          bounded residual/repetition refinement passes
 * @param leastFiringsProven     whether the unique-producer residual proof establishes this vector as the
 *                               componentwise least feasible vector
 * @param completeComponentProof whether the proof covers the complete non-negative firing cone rather than only
 *                               the selected reservoir coordinate
 * @param globalOptimization     full-domain objective proof when the vector required global optimization
 */
public record TrinityDeterministicFiringSolution(
                                                 TrinityDeterministicBasis basis,
                                                 Map<TrinityPatternVariant, BigInteger> firings,
                                                 Map<AEKey, BigInteger> totalNet,
                                                 int balancePasses,
                                                 boolean leastFiringsProven,
                                                 boolean completeComponentProof,
                                                 Optional<TrinityFiringOptimization> globalOptimization) {

    public TrinityDeterministicFiringSolution {
        firings = Collections.unmodifiableMap(new LinkedHashMap<>(firings));
        totalNet = Collections.unmodifiableMap(new LinkedHashMap<>(totalNet));
        if (completeComponentProof && !leastFiringsProven && globalOptimization.isEmpty()) {
            throw new IllegalArgumentException(
                    "A complete Trinity component proof requires either least firings or global optimization");
        }
        if (globalOptimization.isPresent() && !globalOptimization.orElseThrow().firings().equals(firings)) {
            throw new IllegalArgumentException("A Trinity global firing proof must match its selected vector");
        }
    }
}
