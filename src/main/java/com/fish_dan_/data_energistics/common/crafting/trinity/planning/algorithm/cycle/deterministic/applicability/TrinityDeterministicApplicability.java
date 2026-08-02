package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCycleSequence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

/**
 * Determines whether one reservoir yields the unique productive basis required by the deterministic fast path.
 */
public interface TrinityDeterministicApplicability {

    /**
     * Creates the structural assessment using the exact primitive-cycle resolver.
     */
    static TrinityDeterministicApplicability create(TrinityDeterministicCycleSequence cycleSequence) {
        return new TrinityDeterministicApplicabilityImpl(cycleSequence);
    }

    /**
     * Returns an applicable basis, a reservoir-local miss, or a component-wide structural rejection.
     */
    TrinityDeterministicApplicabilityResult assess(
                                                   TrinityStronglyConnectedComponent component,
                                                   TrinityCycleDemand demand,
                                                   AEKey reservoir,
                                                   Map<AEKey, BigInteger> available);
}
