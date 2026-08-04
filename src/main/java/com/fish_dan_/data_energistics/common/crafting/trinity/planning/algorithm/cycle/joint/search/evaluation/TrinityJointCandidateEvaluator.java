package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.evaluation;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilitySolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts one conservation-feasible firing vector into an executable candidate with the true lexicographic cost.
 */
public interface TrinityJointCandidateEvaluator {

    /**
     * @return stateless exact evaluator shared by all firing boxes
     */
    static TrinityJointCandidateEvaluator create() {
        return new TrinityJointCandidateEvaluatorImpl(
                TrinityExactConservationVerifier.create(),
                TrinityCompressedScheduler.create(),
                TrinityMinimumSeedScheduler.create());
    }

    /**
     * Schedules and verifies one canonical MIP point. A no-order diagnostic rejects only this point; search limits,
     * cancellation and timeout remain terminal for the bounded parent search.
     */
    TrinityAlgorithmResult<TrinityJointCandidateEvaluation> evaluate(
                                                                     List<TrinityPatternVariant> variants,
                                                                     Set<AEKey> internalKeys,
                                                                     TrinityCycleDemand demand,
                                                                     Map<AEKey, BigInteger> available,
                                                                     Set<AEKey> producibleInputs,
                                                                     TrinityCycleFeasibilitySolution solution,
                                                                     int maxScheduleStates,
                                                                     int solverPasses,
                                                                     long solverNanos,
                                                                     TrinityPlanningControl control);
}
