package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicBasis;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityShiftedFiringOptimizer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Calculates the exact lexicographic firing vector for one applicable deterministic cycle basis.
 */
public interface TrinityDeterministicFiringCalculator {

    /**
     * Creates the calculator using the shared shifted-firing optimizer.
     */
    static TrinityDeterministicFiringCalculator create(TrinityShiftedFiringOptimizer firingOptimizer) {
        return new TrinityDeterministicFiringCalculatorImpl(firingOptimizer);
    }

    /**
     * Solves repeated primitive firings plus the unique acyclic residual, then applies exact firing optimization.
     */
    TrinityAlgorithmResult<TrinityDeterministicFiringSolution> calculate(
                                                                         TrinityStronglyConnectedComponent component,
                                                                         TrinityCycleDemand demand,
                                                                         Map<AEKey, BigInteger> available,
                                                                         Set<AEKey> producibleInputs,
                                                                         TrinityDeterministicBasis basis,
                                                                         TrinityPlanningControl control);
}
