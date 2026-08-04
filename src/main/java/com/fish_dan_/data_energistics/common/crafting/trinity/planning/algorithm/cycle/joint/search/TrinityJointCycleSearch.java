package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.cut.TrinityExternalPrefixCut;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.evaluation.TrinityJointCandidateEvaluator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityModel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Proves the global executable cycle objective with exact firing boxes and compressed schedule checks.
 */
public interface TrinityJointCycleSearch {

    /**
     * @return exact bounded branch-and-bound search
     */
    static TrinityJointCycleSearch create() {
        return new TrinityJointCycleSearchImpl(
                TrinityCycleFeasibilityModel.create(),
                TrinityJointCandidateEvaluator.create(),
                TrinityExternalPrefixCut.create());
    }

    /**
     * Searches every firing box that can improve the executable incumbent until optimality is proved or a shared
     * bound terminates the search.
     */
    TrinityAlgorithmResult<TrinityJointCyclePlan> search(
                                                         TrinityStronglyConnectedComponent component,
                                                         TrinityCycleDemand demand,
                                                         Map<AEKey, BigInteger> available,
                                                         Set<AEKey> producibleInputs,
                                                         int maxSearchStates,
                                                         TrinityPlanningControl control);

    /**
     * Decodes the mandatory compressed-state count from a scheduler diagnostic.
     */
    static int diagnosticStates(TrinityPlanningDiagnostic diagnostic) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("A Trinity schedule diagnostic is required");
        }
        String encodedStates = diagnostic.metadata().get("states");
        if (encodedStates == null) {
            throw new IllegalStateException("A Trinity schedule diagnostic must report visited states");
        }
        try {
            int states = Integer.parseInt(encodedStates);
            if (states < 0) {
                throw new IllegalStateException("Trinity schedule diagnostic states cannot be negative");
            }
            return states;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Trinity schedule diagnostic states must be an integer", exception);
        }
    }
}
