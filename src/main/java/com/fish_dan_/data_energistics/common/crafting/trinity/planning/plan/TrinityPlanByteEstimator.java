package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

/**
 * Conservatively maps compact graph accounting to AE2 crafting CPU byte capacity.
 */
public interface TrinityPlanByteEstimator {

    /**
     * @return stateless exact estimator
     */
    static TrinityPlanByteEstimator create() {
        return new TrinityPlanByteEstimatorImpl();
    }

    /**
     * Uses AE2's eight-times stack accounting, logical firing bytes and eight bytes per tree node.
     *
     * @param input complete planner accounting
     * @return conservative capacity bytes
     * @throws ArithmeticException when the result cannot cross the AE2 {@code long} boundary exactly
     */
    long estimate(TrinityPlanByteEstimateInput input);
}
