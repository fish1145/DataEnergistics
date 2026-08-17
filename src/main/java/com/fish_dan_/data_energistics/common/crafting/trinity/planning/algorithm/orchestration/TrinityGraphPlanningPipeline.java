package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
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
     * @param settings        dynamic solve bounds
     * @param control         lifecycle cancellation boundary
     * @return current-revision immutable plan or deterministic dynamic rejection
     */
    TrinityAlgorithmResult<TrinityCraftingPlan> solve(
                                                      TrinityCompiledGraph compiled,
                                                      long catalogRevision,
                                                      BigInteger requestedAmount,
                                                      CraftingQuantityMode quantityMode,
                                                      Map<AEKey, BigInteger> available,
                                                      TrinityCraftingSchema settings,
                                                      TrinityPlanningControl control);
}
