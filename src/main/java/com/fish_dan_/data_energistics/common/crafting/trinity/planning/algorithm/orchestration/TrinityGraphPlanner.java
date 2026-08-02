package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicComponentPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCyclePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCycleSequence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.TrinityMixedIntegerCycleSolver;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicDemandPropagator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityGraphTopologyAnalyzer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityPatternVariantExpander;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanByteEstimator;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

/**
 * Coordinates immutable graph expansion, topology analysis, exact demand solving and compact plan construction.
 */
public interface TrinityGraphPlanner {

    /**
     * @return stateless planner composed from the exact bounded algorithm implementations
     */
    static TrinityGraphPlanner create() {
        return new TrinityGraphPlannerImpl(
                TrinityPatternVariantExpander.create(),
                TrinityGraphTopologyAnalyzer.create(),
                TrinityAcyclicDemandPropagator.create(),
                TrinityDeterministicCycleSequence.create(),
                TrinityDeterministicCyclePlanner.create(),
                TrinityDeterministicComponentPlanner.create(),
                TrinityMixedIntegerCycleSolver.create(),
                TrinityPlanByteEstimator.create());
    }

    /**
     * @param snapshot        immutable graph revision
     * @param target          requested output key
     * @param requestedAmount positive requested delivery
     * @param quantityMode    net-new or final-total semantics
     * @param available       non-negative network inventory snapshot
     * @param settings        immutable planner bounds
     * @param control         cooperative cancellation and total deadline
     * @return complete Trinity-only plan or one stable fallback diagnostic
     */
    TrinityAlgorithmResult<TrinityCraftingPlan> plan(
                                                     TrinityCraftingGraphSnapshot snapshot,
                                                     AEKey target,
                                                     BigInteger requestedAmount,
                                                     CraftingQuantityMode quantityMode,
                                                     Map<AEKey, BigInteger> available,
                                                     TrinityCraftingConfig.Settings settings,
                                                     TrinityPlanningControl control);
}
