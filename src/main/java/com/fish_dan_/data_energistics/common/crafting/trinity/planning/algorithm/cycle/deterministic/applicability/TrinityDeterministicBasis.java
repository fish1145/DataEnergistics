package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proof-carrying applicability result for one primitive reservoir basis and its acyclic residual topology.
 *
 * @param reservoir        productive internal axis
 * @param primitiveOrder   executable primitive cycle order
 * @param primitiveFirings aggregate primitive firing vector
 * @param primitiveNet     exact primitive net change
 * @param residualTopology unique-producer residual topology
 */
public record TrinityDeterministicBasis(
                                        AEKey reservoir,
                                        List<TrinityVariantFiring> primitiveOrder,
                                        Map<TrinityPatternVariant, BigInteger> primitiveFirings,
                                        Map<AEKey, BigInteger> primitiveNet,
                                        TrinityDeterministicResidualTopology residualTopology) {

    public TrinityDeterministicBasis {
        primitiveOrder = List.copyOf(primitiveOrder);
        primitiveFirings = Collections.unmodifiableMap(new LinkedHashMap<>(primitiveFirings));
        primitiveNet = Collections.unmodifiableMap(new LinkedHashMap<>(primitiveNet));
    }
}
