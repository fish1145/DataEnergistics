package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.TrinityCraftingSchema;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

/**
 * Exposes the structural and dynamic stages used by the server-lifetime planning cache.
 */
public interface TrinityGraphPlanningPipeline extends TrinityGraphPlanner {

    /**
     * Expands bindings and analyzes topology for one already target-reachable graph.
     *
     * @param reachableSnapshot  target-reachable immutable graph
     * @param target             requested output
     * @param maxBindingVariants binding expansion bound
     * @param maxSccKeys         SCC key bound
     * @param control            lifecycle cancellation boundary
     * @return revision-independent compiled structure or deterministic structural rejection
     */
    TrinityAlgorithmResult<TrinityCompiledGraph> compile(
                                                         TrinityCraftingGraphSnapshot reachableSnapshot,
                                                         AEKey target,
                                                         int maxBindingVariants,
                                                         int maxSccKeys,
                                                         TrinityPlanningControl control);

    /**
     * Re-runs inventory and quantity-sensitive demand solving against a compiled structure.
     *
     * @param compiled        revision-independent target structure
     * @param catalogRevision current publication revision written into the result plan
     * @param requestedAmount requested delivery amount
     * @param quantityMode    quantity semantics
     * @param available       projected relevant positive inventory
     * @param limits          dynamic solve bounds
     * @param mode            complete optimisation or first-feasible fallback
     * @param control         lifecycle cancellation boundary
     * @return current-revision immutable plan or deterministic dynamic rejection
     */
    TrinityAlgorithmResult<TrinityCraftingPlan> solve(
                                                      TrinityCompiledGraph compiled,
                                                      long catalogRevision,
                                                      BigInteger requestedAmount,
                                                      CraftingQuantityMode quantityMode,
                                                      Map<AEKey, BigInteger> available,
                                                      TrinityPlanningLimits limits,
                                                      TrinityPlanningMode mode,
                                                      TrinityPlanningControl control);

    /**
     * Compatibility entry point that preserves complete optimisation.
     */
    default TrinityAlgorithmResult<TrinityCraftingPlan> solve(
                                                              TrinityCompiledGraph compiled,
                                                              long catalogRevision,
                                                              BigInteger requestedAmount,
                                                              CraftingQuantityMode quantityMode,
                                                              Map<AEKey, BigInteger> available,
                                                              TrinityPlanningLimits limits,
                                                              TrinityPlanningControl control) {
        return solve(
                compiled,
                catalogRevision,
                requestedAmount,
                quantityMode,
                available,
                limits,
                TrinityPlanningMode.OPTIMAL,
                control);
    }

    /**
     * Compatibility entry point that captures a mutable configuration before solving.
     */
    default TrinityAlgorithmResult<TrinityCraftingPlan> solve(
                                                              TrinityCompiledGraph compiled,
                                                              long catalogRevision,
                                                              BigInteger requestedAmount,
                                                              CraftingQuantityMode quantityMode,
                                                              Map<AEKey, BigInteger> available,
                                                              TrinityCraftingSchema settings,
                                                              TrinityPlanningControl control) {
        return solve(
                compiled,
                catalogRevision,
                requestedAmount,
                quantityMode,
                available,
                TrinityPlanningLimits.capture(settings),
                TrinityPlanningMode.OPTIMAL,
                control);
    }
}
