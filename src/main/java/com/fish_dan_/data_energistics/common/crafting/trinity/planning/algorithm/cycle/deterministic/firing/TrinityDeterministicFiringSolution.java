package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicBasis;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact optimized firing vector and net-change proof derived from one applicable primitive basis.
 *
 * @param basis              structural basis used by proof assembly
 * @param firings            optimized exact firing vector
 * @param totalNet           exact aggregate net change
 * @param balancePasses      bounded residual/repetition refinement passes
 * @param leastFiringsProven whether the unique-producer residual proof establishes this vector as the
 *                           componentwise least feasible vector
 */
public record TrinityDeterministicFiringSolution(
                                                 TrinityDeterministicBasis basis,
                                                 Map<TrinityPatternVariant, BigInteger> firings,
                                                 Map<AEKey, BigInteger> totalNet,
                                                 int balancePasses,
                                                 boolean leastFiringsProven) {

    public TrinityDeterministicFiringSolution {
        firings = Collections.unmodifiableMap(new LinkedHashMap<>(firings));
        totalNet = Collections.unmodifiableMap(new LinkedHashMap<>(totalNet));
    }
}
