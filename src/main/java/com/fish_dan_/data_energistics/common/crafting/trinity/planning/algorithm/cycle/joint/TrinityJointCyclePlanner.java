package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.TrinityJointCycleSearch;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative planner for a complete cyclic-component demand. It combines exact joint firing selection with an
 * executable compressed schedule; the MIP model remains an internal feasibility backend.
 * <p>
 * Thin authoritative entry point; exact box search and compressed scheduling remain behind one dedicated contract.
 */
public final class TrinityJointCyclePlanner {

    /**
     * @return joint planner using ojAlgo 57.1.0 and exact compressed scheduling
     */
    public static TrinityJointCyclePlanner create() {
        return new TrinityJointCyclePlanner(TrinityJointCycleSearch.create());
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
    public TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
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
    public TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
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
    public TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
                                                              TrinityStronglyConnectedComponent component,
                                                              TrinityCycleDemand demand,
                                                              Map<AEKey, BigInteger> available,
                                                              int maxSearchStates,
                                                              TrinityPlanningControl control) {
        return plan(component, demand, available, Set.of(), maxSearchStates, control);
    }

    private final TrinityJointCycleSearch search;

    TrinityJointCyclePlanner(TrinityJointCycleSearch search) {
        if (search == null) {
            throw new IllegalArgumentException("A Trinity joint planner requires an exact search");
        }
        this.search = search;
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
    public TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
                                                              TrinityStronglyConnectedComponent component,
                                                              TrinityCycleDemand demand,
                                                              Map<AEKey, BigInteger> available,
                                                              Set<AEKey> producibleInputs,
                                                              int maxSearchStates,
                                                              TrinityPlanningMode mode,
                                                              TrinityPlanningControl control) {
        return plan(
                component,
                demand,
                available,
                producibleInputs,
                maxSearchStates,
                mode,
                control,
                TrinityMipCoefficientTemplate.create(
                        component.cycleVariants(),
                        new ObjectArrayList<>(component.keys())));
    }

    /** Uses a cached sparse coefficient layout while keeping the model and request bounds private. */
    public TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
                                                              TrinityStronglyConnectedComponent component,
                                                              TrinityCycleDemand demand,
                                                              Map<AEKey, BigInteger> available,
                                                              Set<AEKey> producibleInputs,
                                                              int maxSearchStates,
                                                              TrinityPlanningMode mode,
                                                              TrinityPlanningControl control,
                                                              TrinityMipCoefficientTemplate coefficientTemplate) {
        return this.search.search(
                component,
                demand,
                available,
                producibleInputs,
                maxSearchStates,
                mode,
                control,
                coefficientTemplate);
    }

    /**
     * Compatibility entry point that retains complete optimisation.
     */
    public TrinityAlgorithmResult<TrinityJointCyclePlan> plan(
                                                              TrinityStronglyConnectedComponent component,
                                                              TrinityCycleDemand demand,
                                                              Map<AEKey, BigInteger> available,
                                                              Set<AEKey> producibleInputs,
                                                              int maxSearchStates,
                                                              TrinityPlanningControl control) {
        return plan(
                component,
                demand,
                available,
                producibleInputs,
                maxSearchStates,
                TrinityPlanningMode.OPTIMAL,
                control);
    }

    static int diagnosticStates(TrinityPlanningDiagnostic diagnostic) {
        return TrinityJointCycleSearch.diagnosticStates(diagnostic);
    }
}
