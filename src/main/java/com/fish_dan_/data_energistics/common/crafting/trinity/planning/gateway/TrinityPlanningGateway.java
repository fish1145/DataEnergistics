package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningComputationResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningInput;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.TrinityCrafting;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Starts the planner selected by the current crafting CPU family for one crafting request.
 */
public interface TrinityPlanningGateway extends AutoCloseable {

    /**
     * Creates a bounded gateway from one immutable COMMON configuration snapshot.
     *
     * @param settings planner worker, queue and timeout budgets
     * @return independently owned gateway
     */
    static TrinityPlanningGateway create(TrinityCrafting settings) {
        return new ConcurrentTrinityPlanningGateway(settings);
    }

    /**
     * Starts Trinity when the Grid has an eligible online Trinity CPU; otherwise delegates to AE2.
     *
     * @param trinityPlanningAvailable whether the current Grid has an online CPU eligible for an extended plan;
     *                                 transient worker occupancy does not affect this qualification
     * @param gridScope           owning Grid publication scope
     * @param graphRevision       immutable graph revision used by the Trinity calculation
     * @param requestedOutput     requested output retained for a standalone Trinity diagnostic
     * @param trinityCalculation  immutable-snapshot calculation submitted only when the CPU gate passes
     * @param ae2Calculation      original AE2 calculation used only when the Trinity CPU gate does not pass
     * @return one cooperative future for the selected planner
     */
    Future<ICraftingPlan> begin(
                                boolean trinityPlanningAvailable,
                                long gridScope,
                                long graphRevision,
                                GenericStack requestedOutput,
                                Callable<TrinityPlanningAttempt> trinityCalculation,
                                Supplier<Future<ICraftingPlan>> ae2Calculation);

    /**
     * Submits a Trinity-only continuation such as remaining-work replanning through the same bounded pool.
     *
     * @param gridScope          owning Grid publication scope
     * @param graphRevision      immutable graph revision used by the calculation
     * @param trinityCalculation immutable-snapshot calculation
     * @return cooperative bounded future, including an explicit queue-full outcome
     */
    Future<TrinityPlanningAttempt> beginTrinity(
                                                long gridScope,
                                                long graphRevision,
                                                Callable<TrinityPlanningAttempt> trinityCalculation);

    /**
     * Executes pure planning through the shared multi-level cache on the current accepted planner worker.
     *
     * @param input immutable Grid-scoped planning input
     * @return algorithm result and exact cache path
     * @throws InterruptedException when this worker is interrupted while joining shared work
     * @throws ExecutionException   when a bottom calculation fails
     */
    TrinityPlanningComputationResult calculateTrinity(TrinityPlanningInput input)
                                                                                  throws InterruptedException, ExecutionException;

    /**
     * Cancels cached and in-flight work owned by one unloaded Grid publication scope.
     *
     * @param gridScope unloaded Grid publication scope
     */
    void clearGrid(long gridScope);

    /**
     * Stops gateway-owned planner workers and cooperatively interrupts queued calculations.
     */
    @Override
    void close();
}
