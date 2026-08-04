package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Removes target routes that cannot be reached from the captured inventory in an acyclic transition graph.
 */
public interface TrinityAcyclicRoutePruner {

    /**
     * @return stateless event-driven pruner
     */
    static TrinityAcyclicRoutePruner create() {
        return new TrinityAcyclicRoutePrunerImpl();
    }

    /**
     * Retains every and only structurally executable transition that can still contribute to the target.
     * Quantities remain the responsibility of the exact propagator or MIP; this boundary only proves that each
     * retained input key has an inventory-backed production path.
     *
     * @param variants  complete transition set
     * @param target    requested output
     * @param available non-negative captured inventory
     * @return stable identity-ordered executable target routes
     */
    List<TrinityPatternVariant> retainExecutableTargetRoutes(
                                                             List<TrinityPatternVariant> variants,
                                                             AEKey target,
                                                             Map<AEKey, BigInteger> available);
}
