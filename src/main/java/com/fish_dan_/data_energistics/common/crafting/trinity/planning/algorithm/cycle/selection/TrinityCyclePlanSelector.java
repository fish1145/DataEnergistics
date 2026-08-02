package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicComponentPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCyclePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCycleSequence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.selector.TrinityPlanningAttemptSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Owns the ordered scalar, deterministic-component and authoritative joint-cycle selection policy.
 */
public interface TrinityCyclePlanSelector {

    /**
     * @return selector composed from exact deterministic and joint-cycle implementations
     */
    static TrinityCyclePlanSelector create() {
        return new TrinityCyclePlanSelectorImpl(
                TrinityDeterministicCycleSequence.create(),
                TrinityDeterministicCyclePlanner.create(),
                TrinityDeterministicComponentPlanner.create(),
                TrinityJointCyclePlanner.create(),
                TrinityPlanningAttemptSelector.create());
    }

    /**
     * Selects one fully executable cycle representation under the shared planning bounds.
     *
     * @param component        immutable cyclic SCC
     * @param demand           complete component demand
     * @param available        non-negative inventory snapshot
     * @param producibleInputs inputs predecessor graph stages may provide
     * @param maxStates        positive compressed-search bound
     * @param control          cooperative cancellation and deadline boundary
     * @return selected executable cycle or stable terminal diagnostic
     */
    TrinityAlgorithmResult<TrinityCycleSelection> select(
                                                         TrinityStronglyConnectedComponent component,
                                                         TrinityCycleDemand demand,
                                                         Map<AEKey, BigInteger> available,
                                                         Set<AEKey> producibleInputs,
                                                         int maxStates,
                                                         TrinityPlanningControl control);
}
