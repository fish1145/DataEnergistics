package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Refines a component-wise feasible firing upper bound by optimizing exact integer reductions from that bound.
 */
public interface TrinityShiftedFiringOptimizer {

    /**
     * @return optimizer using ojAlgo only on bounded reductions and exact BigInteger result verification
     */
    static TrinityShiftedFiringOptimizer create() {
        return new TrinityShiftedFiringOptimizerImpl(TrinityIntegerResultVerifier.create());
    }

    /**
     * @param component        unique-producer component
     * @param demand           complete component-wide lower bounds
     * @param available        immutable inventory snapshot
     * @param producibleInputs inputs supplied by predecessor DAG stages
     * @param firingUpperBound component-wise feasible firing vector without consuming optional internal stock
     * @param control          cancellation and shared deadline
     * @return empty when shifted dominance cannot be proven; otherwise the exact lexicographic vector or failure
     */
    Optional<TrinityAlgorithmResult<Map<TrinityPatternVariant, BigInteger>>> optimize(
                                                                                      TrinityStronglyConnectedComponent component,
                                                                                      TrinityCycleDemand demand,
                                                                                      Map<AEKey, BigInteger> available,
                                                                                      Set<AEKey> producibleInputs,
                                                                                      Map<TrinityPatternVariant, BigInteger> firingUpperBound,
                                                                                      TrinityPlanningControl control);
}
