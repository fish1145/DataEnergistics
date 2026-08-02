package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.TrinityJointCycleSearch;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Thin authoritative entry point; exact box search and compressed scheduling remain behind one dedicated contract.
 */
final class TrinityJointCyclePlannerImpl implements TrinityJointCyclePlanner {

    private final TrinityJointCycleSearch search;

    TrinityJointCyclePlannerImpl(TrinityJointCycleSearch search) {
        if (search == null) {
            throw new IllegalArgumentException("A Trinity joint planner requires an exact search");
        }
        this.search = search;
    }

    @Override
    public TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
                                                              TrinityStronglyConnectedComponent component,
                                                              TrinityCycleDemand demand,
                                                              Map<AEKey, BigInteger> available,
                                                              Set<AEKey> producibleInputs,
                                                              int maxSearchStates,
                                                              TrinityPlanningControl control) {
        return this.search.search(
                component,
                demand,
                available,
                producibleInputs,
                maxSearchStates,
                control);
    }

    static int diagnosticStates(TrinityPlanningDiagnostic diagnostic) {
        return TrinityJointCycleSearch.diagnosticStates(diagnostic);
    }
}
