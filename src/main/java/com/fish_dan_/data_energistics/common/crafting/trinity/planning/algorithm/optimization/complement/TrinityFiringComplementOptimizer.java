package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.complement;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Completes an externally optimal cycle vector through a unique-producer acyclic remainder.
 */
public interface TrinityFiringComplementOptimizer {

    /**
     * @return stateless exact complement optimizer
     */
    static TrinityFiringComplementOptimizer create() {
        return new TrinityFiringComplementOptimizerImpl();
    }

    /**
     * @param component        cycle component whose published variants define the entire search domain
     * @param demand           exact component lower bounds
     * @param available        immutable stored inventory snapshot
     * @param producibleInputs keys supplied by predecessor DAG stages
     * @param firingUpperBound verified feasible incumbent
     * @param reductions       externally optimal reductions from that incumbent
     * @param fixedVariants    variants whose external cost fixes their firing count
     * @return componentwise least complement, or empty when the unfixed remainder is not a unique DAG
     */
    Optional<Map<TrinityPatternVariant, BigInteger>> minimize(
                                                              TrinityStronglyConnectedComponent component,
                                                              TrinityCycleDemand demand,
                                                              Map<AEKey, BigInteger> available,
                                                              Set<AEKey> producibleInputs,
                                                              Map<TrinityPatternVariant, BigInteger> firingUpperBound,
                                                              Map<TrinityPatternVariant, BigInteger> reductions,
                                                              Set<TrinityPatternVariant> fixedVariants);
}
