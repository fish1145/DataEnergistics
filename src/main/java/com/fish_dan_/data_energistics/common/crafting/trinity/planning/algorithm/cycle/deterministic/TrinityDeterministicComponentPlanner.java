package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityShiftedFiringOptimizer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Solves every demanded output of a unique-producer SCC without expanding logical firing counts.
 */
public interface TrinityDeterministicComponentPlanner {

    /**
     * @return planner composed from exact cycle-ratio and repeat-scheduling implementations
     */
    static TrinityDeterministicComponentPlanner create() {
        return new TrinityDeterministicComponentPlannerImpl(
                TrinityDeterministicCycleSequence.create(),
                TrinityShiftedFiringOptimizer.create(),
                TrinityMinimumSeedScheduler.create(),
                TrinityDeterministicRepeatScheduler.create());
    }

    /**
     * Attempts the structural fast path for one component.
     *
     * @param component        immutable cyclic component
     * @param demand           all internal and boundary-output lower bounds owned by the component
     * @param available        immutable non-negative inventory snapshot
     * @param producibleInputs inputs that predecessor DAG stages may supply
     * @param maxStates        positive graph-bounded planning and scheduling limit
     * @param control          cancellation and shared deadline boundary
     * @return exact proved plan, structural miss requiring MIP, or terminal shared-budget failure
     */
    TrinityPlanningAttempt<TrinityDeterministicComponentPlan> plan(
                                                                   TrinityStronglyConnectedComponent component,
                                                                   TrinityCycleDemand demand,
                                                                   Map<AEKey, BigInteger> available,
                                                                   Set<AEKey> producibleInputs,
                                                                   int maxStates,
                                                                   TrinityPlanningControl control);
}
