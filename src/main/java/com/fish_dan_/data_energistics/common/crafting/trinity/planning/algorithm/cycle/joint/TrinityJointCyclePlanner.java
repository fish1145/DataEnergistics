package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
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
 * Authoritative planner for a complete cyclic-component demand. It combines exact joint firing selection with an
 * executable compressed schedule; the MIP model remains an internal feasibility backend.
 */
public interface TrinityJointCyclePlanner {

    /**
     * @return joint planner using ojAlgo 57.1.0 and exact compressed scheduling
     */
    static TrinityJointCyclePlanner create() {
        return new TrinityJointCyclePlannerImpl(TrinityJointCycleSearch.create());
    }

    /**
     * @param component       cyclic component and its externally connected cycle variants
     * @param target          requested SCC key or boundary output
     * @param requestedAmount positive requested delivery
     * @param quantityMode    net-new or final-total semantics
     * @param available       immutable non-negative inventory snapshot
     * @param maxSearchStates shared candidate/schedule search bound
     * @param control         cancellation and shared MIP/search deadline
     * @return exact lexicographic plan or stable bounded rejection
     */
    default TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
                                                               TrinityStronglyConnectedComponent component,
                                                               AEKey target,
                                                               BigInteger requestedAmount,
                                                               CraftingQuantityMode quantityMode,
                                                               Map<AEKey, BigInteger> available,
                                                               int maxSearchStates,
                                                               TrinityPlanningControl control) {
        return plan(
                component,
                target,
                requestedAmount,
                quantityMode,
                available,
                Set.of(),
                maxSearchStates,
                control);
    }

    /**
     * Upstream-craftable keys are not capped by current storage. The returned initial-input demand must be propagated
     * into condensation predecessors before the plan is executable.
     *
     * @param producibleInputs keys that can be produced by predecessor DAG stages
     */
    default TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
                                                               TrinityStronglyConnectedComponent component,
                                                               AEKey target,
                                                               BigInteger requestedAmount,
                                                               CraftingQuantityMode quantityMode,
                                                               Map<AEKey, BigInteger> available,
                                                               Set<AEKey> producibleInputs,
                                                               int maxSearchStates,
                                                               TrinityPlanningControl control) {
        return plan(
                component,
                TrinityCycleDemand.forTarget(target, requestedAmount, quantityMode, available),
                available,
                producibleInputs,
                maxSearchStates,
                control);
    }

    /**
     * Solves all final-balance and net-change lower bounds for one component in a single firing vector.
     *
     * @param component       cyclic component and its owned transition variants
     * @param demand          immutable component-wide demand, including optional boundary outputs
     * @param available       immutable non-negative inventory snapshot
     * @param maxSearchStates shared candidate/schedule search bound
     * @param control         cancellation and shared MIP/search deadline
     * @return exact lexicographic plan or stable bounded rejection
     */
    default TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
                                                               TrinityStronglyConnectedComponent component,
                                                               TrinityCycleDemand demand,
                                                               Map<AEKey, BigInteger> available,
                                                               int maxSearchStates,
                                                               TrinityPlanningControl control) {
        return plan(component, demand, available, Set.of(), maxSearchStates, control);
    }

    /**
     * Solves a generalized component demand while leaving predecessor-craftable inputs unbounded by current storage.
     *
     * @param component        cyclic component and its owned transition variants
     * @param demand           immutable component-wide demand, including optional boundary outputs
     * @param available        immutable non-negative inventory snapshot
     * @param producibleInputs keys that can be produced by predecessor DAG stages
     * @param maxSearchStates  shared candidate/schedule search bound
     * @param control          cancellation and shared MIP/search deadline
     * @return exact lexicographic plan or stable bounded rejection
     */
    TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
                                                       TrinityStronglyConnectedComponent component,
                                                       TrinityCycleDemand demand,
                                                       Map<AEKey, BigInteger> available,
                                                       Set<AEKey> producibleInputs,
                                                       int maxSearchStates,
                                                       TrinityPlanningControl control);
}
