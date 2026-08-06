package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import java.util.function.Supplier;

/**
 * Converts one immutable initial request into an executable attempt with cooperative cancellation.
 */
public interface TrinityInitialPlanCalculation {

    /**
     * @param gatewaySupplier lazy access to the server-lifetime cached planning gateway
     * @return stateless calculation composed with the production cached planner
     */
    static TrinityInitialPlanCalculation create(Supplier<TrinityPlanningGateway> gatewaySupplier) {
        return new TrinityInitialPlanCalculationImpl(gatewaySupplier);
    }

    /**
     * @param request immutable server-thread capture
     * @return executable plan or an explicit AE2 fallback diagnostic
     */
    TrinityPlanningAttempt calculate(TrinityInitialPlanningRequest request) throws Exception;
}
