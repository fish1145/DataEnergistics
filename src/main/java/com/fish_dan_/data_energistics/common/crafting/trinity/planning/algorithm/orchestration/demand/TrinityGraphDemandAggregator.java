package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCyclePlanSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Aggregates reverse demands across a condensation graph and selects bounded routes without constructing plan stages.
 */
public interface TrinityGraphDemandAggregator {

    /**
     * Creates the state-isolated demand search using the shared cycle selector.
     */
    static TrinityGraphDemandAggregator create(TrinityCyclePlanSelector cyclePlanSelector) {
        return new TrinityGraphDemandAggregatorImpl(cyclePlanSelector);
    }

    /**
     * Resolves all target, boundary, cycle-seed, and upstream demands into immutable firing selections.
     */
    TrinityAlgorithmResult<TrinityGraphDemandSolution> aggregate(
                                                                 TrinityCraftingTopology topology,
                                                                 List<TrinityPatternVariant> variants,
                                                                 AEKey target,
                                                                 BigInteger requestedAmount,
                                                                 CraftingQuantityMode quantityMode,
                                                                 Map<AEKey, BigInteger> available,
                                                                 TrinityCraftingConfig.Settings settings,
                                                                 TrinityPlanningControl control);
}
