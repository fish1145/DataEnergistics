package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Selects one exact aggregate firing vector when an acyclic request has competing or mixable routes.
 */
public interface TrinityAcyclicRouteOptimizer {

    /**
     * @return ojAlgo-backed optimizer with exact integer and conservation verification
     */
    static TrinityAcyclicRouteOptimizer create() {
        return new TrinityAcyclicRouteOptimizerImpl(
                TrinityIntegerResultVerifier.create(),
                TrinityExactConservationVerifier.create(),
                TrinityAcyclicRoutePruner.create());
    }

    /**
     * Solves the complete target-reachable acyclic region without expanding one state per requested item.
     *
     * @param topology        analyzed acyclic topology
     * @param variants        complete stable transition set
     * @param target          requested output
     * @param requestedAmount positive requested quantity
     * @param quantityMode    target inventory semantics
     * @param available       immutable inventory snapshot
     * @param maxSearchStates maximum sequential optimization passes
     * @param control         cooperative cancellation and deadline
     * @return exact executable aggregate plan or a stable fallback diagnostic
     */
    TrinityAlgorithmResult<TrinityAcyclicPlan> optimize(
                                                        TrinityCraftingTopology topology,
                                                        List<TrinityPatternVariant> variants,
                                                        AEKey target,
                                                        BigInteger requestedAmount,
                                                        CraftingQuantityMode quantityMode,
                                                        Map<AEKey, BigInteger> available,
                                                        int maxSearchStates,
                                                        TrinityPlanningControl control);
}
