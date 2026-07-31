package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization.TrinityAcyclicRouteOptimizer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Propagates aggregate demand through acyclic keys without expanding one state per requested item.
 */
public interface TrinityAcyclicDemandPropagator {

    /**
     * @return stateless exact propagator
     */
    static TrinityAcyclicDemandPropagator create() {
        return new TrinityAcyclicDemandPropagatorImpl(TrinityAcyclicRouteOptimizer.create());
    }

    /**
     * @param topology        analyzed graph topology
     * @param variants        complete identity-ordered transition set
     * @param target          requested output key
     * @param requestedAmount positive requested amount
     * @param quantityMode    net-new or final-total semantics
     * @param available       immutable non-negative inventory snapshot
     * @param maxSearchStates maximum aggregate route-optimization states
     * @param control         cooperative cancellation and shared deadline
     * @return compact plan, or an explicit cycle/unsupported diagnostic
     */
    TrinityAlgorithmResult<TrinityAcyclicPlan> propagate(
                                                         TrinityCraftingTopology topology,
                                                         List<TrinityPatternVariant> variants,
                                                         AEKey target,
                                                         BigInteger requestedAmount,
                                                         CraftingQuantityMode quantityMode,
                                                         Map<AEKey, BigInteger> available,
                                                         int maxSearchStates,
                                                         TrinityPlanningControl control);
}
