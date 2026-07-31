package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGateway;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Owns the bounded asynchronous calculation for replacing a compact plan's remaining work.
 */
public interface TrinityRemainingPlanCalculation {

    /**
     * Result of one server-thread advancement.
     */
    sealed interface Result permits Waiting, Ready, Rejected, Fault {}

    /**
     * No result is ready, or the current catalog revision has already been attempted.
     */
    record Waiting() implements Result {}

    /**
     * @param plan     validated replacement plan
     * @param revision graph revision used by the calculation
     */
    record Ready(TrinityCraftingPlan plan, long revision) implements Result {}

    /**
     * @param diagnostic structured planning rejection
     * @param revision   graph revision that was rejected
     */
    record Rejected(TrinityPlanningDiagnostic diagnostic, long revision) implements Result {}

    /**
     * @param cause asynchronous calculation failure
     */
    record Fault(Throwable cause) implements Result {}

    /**
     * Creates a calculation owner using the shared bounded planning gateway.
     *
     * @param gatewaySupplier lazy access to the server-lifetime bounded planning entry point
     * @return empty remaining-work calculation
     */
    static TrinityRemainingPlanCalculation create(Supplier<TrinityPlanningGateway> gatewaySupplier) {
        return new TrinityRemainingPlanCalculationImpl(gatewaySupplier);
    }

    /**
     * Polls a pending calculation or starts one for a newer immutable graph revision.
     *
     * @param snapshot          current immutable graph, if one has been published
     * @param availableSupplier server-thread capture of CPU and network material
     * @param target            remaining requested key
     * @param requestedAmount   remaining delivery amount
     * @param quantityMode      retained quantity semantics
     * @param settings          immutable planner budgets
     * @return current advancement result
     */
    Result advance(Optional<TrinityCraftingGraphSnapshot> snapshot,
                   Supplier<Map<AEKey, BigInteger>> availableSupplier,
                   AEKey target,
                   BigInteger requestedAmount,
                   CraftingQuantityMode quantityMode,
                   TrinityCraftingConfig.Settings settings);

    /**
     * Cooperatively cancels pending background work and clears its revision gate.
     */
    void cancel();
}
