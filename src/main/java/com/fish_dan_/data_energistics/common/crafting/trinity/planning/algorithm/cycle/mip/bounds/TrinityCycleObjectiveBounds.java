package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Derives exact objective bounds and reserve domains shared by every Trinity cycle MIP representation.
 */
public interface TrinityCycleObjectiveBounds {

    /**
     * @return stateless exact-bound calculator
     */
    static TrinityCycleObjectiveBounds create() {
        return new TrinityCycleObjectiveBoundsImpl();
    }

    /**
     * Finds the mandatory first-firing SCC reserve floor.
     */
    BigInteger minimumFirstInternalInput(TrinityCycleFeasibilityRequest request);

    /**
     * Finds the mandatory first-firing boundary reserve floor.
     */
    BigInteger minimumFirstExternalInput(TrinityCycleFeasibilityRequest request);

    /**
     * Derives a conservation-based exact lower bound for total firings at fixed reserve objectives.
     */
    BigInteger conservationFiringLowerBound(
                                            TrinityCycleFeasibilityRequest request,
                                            BigInteger fixedExternal,
                                            BigInteger fixedSeed);

    /**
     * Derives an exact necessary upper bound for one deterministic identity axis.
     */
    BigInteger identityObjectiveUpperBound(
                                           TrinityCycleFeasibilityRequest request,
                                           BigInteger fixedExternal,
                                           BigInteger fixedSeed,
                                           BigInteger fixedFirings,
                                           Map<TrinityPatternVariant, BigInteger> fixedCounts,
                                           TrinityPatternVariant variant);

    /**
     * Identifies boundary keys that may receive an initial external reserve variable.
     */
    Set<AEKey> externalReserveKeys(TrinityCycleFeasibilityRequest request);

    /**
     * Captures finite current-inventory caps for exact post-solve conservation replay.
     */
    Map<AEKey, BigInteger> finiteInputUpperBounds(TrinityCycleFeasibilityRequest request);
}
