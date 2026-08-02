package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilitySolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityFiringVector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityLexicographicObjective;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.List;

/**
 * Optimistic exact objective for one firing box. Conservation model seed is a valid lower bound because every
 * executable prefix must start with balances satisfying the same final conservation rows; therefore
 * {@code modelSeed <= truePrefixSeed}. Equality is never assumed until compressed scheduling proves it.
 *
 * @param externalInput exact minimum external total in the box
 * @param modelSeed     exact conservation lower bound for true prefix seed
 * @param firings       exact minimum firing total at the two preceding model levels
 * @param identity      best stable identity at those model levels
 */
public record TrinityJointSearchLowerBound(
                                           BigInteger externalInput,
                                           BigInteger modelSeed,
                                           BigInteger firings,
                                           TrinityFiringVector identity)
        implements Comparable<TrinityJointSearchLowerBound> {

    /**
     * Creates the exact optimistic tuple decoded from one sequential MIP solve.
     */
    public static TrinityJointSearchLowerBound from(
                                                    List<TrinityPatternVariant> variants,
                                                    TrinityCycleFeasibilitySolution solution) {
        if (variants == null || solution == null) {
            throw new IllegalArgumentException("A Trinity joint lower bound requires variants and a solution");
        }
        return new TrinityJointSearchLowerBound(
                solution.externalTotal(),
                solution.seedTotal(),
                solution.firingTotal(),
                TrinityFiringVector.from(variants, solution.firings()));
    }

    /**
     * Validates one complete optimistic tuple.
     */
    public TrinityJointSearchLowerBound {
        if (externalInput == null || modelSeed == null || firings == null || identity == null ||
                externalInput.signum() < 0 || modelSeed.signum() < 0 || firings.signum() < 0) {
            throw new IllegalArgumentException("A Trinity joint lower bound must be complete and non-negative");
        }
    }

    /**
     * Returns true only while this optimistic tuple is strictly better than the executable incumbent.
     */
    public boolean canImprove(TrinityLexicographicObjective incumbent) {
        if (incumbent == null) {
            return true;
        }
        return asOptimisticObjective().compareTo(incumbent) < 0;
    }

    /**
     * Tests whether scheduling proved every optimistic level for the canonical candidate.
     */
    public boolean provenBy(TrinityLexicographicObjective candidate) {
        return candidate != null && asOptimisticObjective().equals(candidate);
    }

    @Override
    public int compareTo(@NotNull TrinityJointSearchLowerBound other) {
        return asOptimisticObjective().compareTo(other.asOptimisticObjective());
    }

    private TrinityLexicographicObjective asOptimisticObjective() {
        return new TrinityLexicographicObjective(externalInput, modelSeed, firings, identity);
    }
}
