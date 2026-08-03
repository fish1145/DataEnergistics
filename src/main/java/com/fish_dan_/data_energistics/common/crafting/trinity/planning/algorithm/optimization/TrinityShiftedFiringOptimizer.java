package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.complement.TrinityFiringComplementOptimizer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Minimizes a structurally verified firing incumbent through exact non-negative integer reductions.
 */
public interface TrinityShiftedFiringOptimizer {

    /**
     * @return optimizer using sequential ojAlgo passes and exact BigInteger result verification
     */
    static TrinityShiftedFiringOptimizer create() {
        return new TrinityShiftedFiringOptimizerImpl(
                TrinityIntegerResultVerifier.create(),
                TrinityFiringComplementOptimizer.create());
    }

    /**
     * @param component         unique-producer component
     * @param demand            complete component-wide lower bounds
     * @param available         immutable inventory snapshot
     * @param producibleInputs  inputs supplied by predecessor DAG stages
     * @param feasibleIncumbent a structurally derived feasible upper vector; callers may claim a global proof only
     *                          when their component proof establishes that every better vector is componentwise lower
     * @param control           cancellation and shared deadline
     * @return exact optimum, non-terminal structural miss, or terminal shared-budget failure
     */
    TrinityPlanningAttempt<TrinityFiringOptimization> optimize(
                                                               TrinityStronglyConnectedComponent component,
                                                               TrinityCycleDemand demand,
                                                               Map<AEKey, BigInteger> available,
                                                               Set<AEKey> producibleInputs,
                                                               Map<TrinityPatternVariant, BigInteger> feasibleIncumbent,
                                                               TrinityPlanningControl control);
}
