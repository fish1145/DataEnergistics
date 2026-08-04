package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.evaluation;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityLexicographicObjective;

/**
 * Executable candidate and its exact objective after compressed prefix scheduling.
 *
 * @param plan          exact conserved plan for one fixed firing vector
 * @param objective     true external-input, prefix-seed, firing and identity tuple
 * @param statesVisited compressed states consumed while evaluating the candidate
 */
public record TrinityJointCandidateEvaluation(
                                              TrinityJointCyclePlan plan,
                                              TrinityLexicographicObjective objective,
                                              int statesVisited) {

    /**
     * Rejects partial candidate evidence before it reaches branch-and-bound.
     */
    public TrinityJointCandidateEvaluation {
        if (plan == null || objective == null || statesVisited <= 0) {
            throw new IllegalArgumentException("A Trinity joint candidate evaluation must be complete");
        }
    }
}
