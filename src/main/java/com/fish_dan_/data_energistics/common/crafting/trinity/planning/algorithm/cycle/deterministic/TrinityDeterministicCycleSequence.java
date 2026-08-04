package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the minimal exact firing ratio of a cycle that has one unambiguous producer for every internal key.
 */
public interface TrinityDeterministicCycleSequence {

    /**
     * @return stateless exact-rational resolver
     */
    static TrinityDeterministicCycleSequence create() {
        return new TrinityDeterministicCycleSequenceImpl();
    }

    /**
     * @param component immutable cyclic component
     * @param target    productive internal key
     * @param available immutable inventory used only to choose an executable stable block order
     * @return minimal positive integer cycle sequence, or empty when route choice remains ambiguous
     */
    Optional<List<TrinityVariantFiring>> resolve(
                                                 TrinityStronglyConnectedComponent component,
                                                 AEKey target,
                                                 Map<AEKey, BigInteger> available);
}
