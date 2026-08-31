package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.proof.TrinityAcyclicRouteHint;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.TrinityCraftingSchema;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Exposes the structural and dynamic stages used by the server-lifetime planning cache.
 */
public interface TrinityGraphPlanningPipeline extends TrinityGraphPlanner {

    /**
     * Expands one complete pattern semantic independently of target, amount, inventory, and planning budget.
     */
    TrinityAlgorithmResult<List<TrinityPatternVariant>> expandPattern(
                                                                      TrinityCraftingGraphPattern pattern,
                                                                      int maxBindingVariants,
                                                                      TrinityPlanningControl control);

    /**
     * Compacts already expanded variants and analyzes one target closure without repeating binding enumeration.
     */
    TrinityAlgorithmResult<TrinityCompiledGraph> compileExpanded(
                                                                 TrinityCraftingGraphSnapshot reachableSnapshot,
                                                                 AEKey target,
                                                                 List<TrinityPatternVariant> expandedVariants,
                                                                 int maxSccKeys,
                                                                 TrinityPlanningControl control);

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
     * @param inventory       projected relevant finite/unlimited inventory
     * @param limits          dynamic solve bounds
     * @param mode            complete optimisation or first-feasible fallback
     * @param control         lifecycle cancellation boundary
     * @return current-revision immutable plan or deterministic dynamic rejection
     */
    default TrinityAlgorithmResult<TrinityCraftingPlan> solve(
                                                              TrinityCompiledGraph compiled,
                                                              long catalogRevision,
                                                              BigInteger requestedAmount,
                                                              CraftingQuantityMode quantityMode,
                                                              TrinityPlanningInventory inventory,
                                                              TrinityPlanningLimits limits,
                                                              TrinityPlanningMode mode,
                                                              TrinityPlanningControl control) {
        return solve(
                compiled,
                Map.of(),
                catalogRevision,
                requestedAmount,
                quantityMode,
                inventory,
                limits,
                mode,
                control);
    }

    /** Solves with request-local quantity-free route hints layered over the compiled structure. */
    TrinityAlgorithmResult<TrinityCraftingPlan> solve(
                                                      TrinityCompiledGraph compiled,
                                                      Map<AEKey, TrinityAcyclicRouteHint> routeHints,
                                                      long catalogRevision,
                                                      BigInteger requestedAmount,
                                                      CraftingQuantityMode quantityMode,
                                                      TrinityPlanningInventory inventory,
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
                TrinityPlanningInventory.finite(available),
                limits,
                TrinityPlanningMode.FIRST_FEASIBLE,
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
                TrinityPlanningInventory.finite(available),
                TrinityPlanningLimits.capture(settings),
                TrinityPlanningMode.FIRST_FEASIBLE,
                control);
    }
}
