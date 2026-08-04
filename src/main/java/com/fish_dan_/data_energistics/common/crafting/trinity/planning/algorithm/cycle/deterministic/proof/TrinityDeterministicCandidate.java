package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.proof;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicComponentPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityLexicographicObjective;

import java.util.Comparator;

/**
 * Couples one executable deterministic proof with its complete lexicographic objective.
 *
 * @param plan      executable deterministic component plan
 * @param objective exact comparison tuple
 */
public record TrinityDeterministicCandidate(
                                            TrinityDeterministicComponentPlan plan,
                                            TrinityLexicographicObjective objective) {

    public static final Comparator<TrinityDeterministicCandidate> ORDER = Comparator.comparing(TrinityDeterministicCandidate::objective);
}
