package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

import appeng.api.networking.crafting.ICraftingPlan;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Starts and arbitrates the Trinity and AE2 planning tracks for one crafting request.
 */
public interface TrinityPlanningGateway extends AutoCloseable {

    /**
     * Creates a bounded gateway from one immutable COMMON configuration snapshot.
     *
     * @param settings planner worker, queue and timeout budgets
     * @return independently owned gateway
     */
    static TrinityPlanningGateway create(TrinityCraftingConfig.Settings settings) {
        return new TrinityPlanningGatewayImpl(settings);
    }

    /**
     * Starts AE2 and Trinity concurrently when a qualified Trinity CPU is currently available.
     *
     * @param qualifiedTrinityCpu whether the current grid has an online, idle CPU eligible for an extended plan
     * @param trinityCalculation  immutable-snapshot calculation submitted only when the CPU gate passes
     * @param ae2Calculation      original AE2 calculation, always started
     * @return one cooperative future that prefers a valid in-budget Trinity result
     */
    Future<ICraftingPlan> begin(
                                boolean qualifiedTrinityCpu,
                                Callable<TrinityPlanningAttempt> trinityCalculation,
                                Supplier<Future<ICraftingPlan>> ae2Calculation);

    /**
     * Stops gateway-owned planner workers and cooperatively interrupts queued calculations.
     */
    @Override
    void close();
}
