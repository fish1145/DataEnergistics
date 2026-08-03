package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanner;

/**
 * Converts one immutable initial request into an executable attempt with cooperative cancellation.
 */
public interface TrinityInitialPlanCalculation {

    /**
     * @return stateless calculation composed with the production graph planner
     */
    static TrinityInitialPlanCalculation create() {
        return new TrinityInitialPlanCalculationImpl(TrinityGraphPlanner.create());
    }

    /**
     * @param request immutable server-thread capture
     * @return executable plan or an explicit AE2 fallback diagnostic
     */
    TrinityPlanningAttempt calculate(TrinityInitialPlanningRequest request);
}
