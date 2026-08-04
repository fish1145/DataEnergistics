package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanContext;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand.TrinityGraphDemandSolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanByteEstimator;

import appeng.api.stacks.AEKey;

/**
 * Converts solved graph demands into compact execution stages and final immutable Trinity crafting plans.
 */
public interface TrinityGraphPlanAssembler {

    /**
     * Creates a plan assembler using the shared conservative AE2 byte estimator.
     */
    static TrinityGraphPlanAssembler create(TrinityPlanByteEstimator byteEstimator) {
        return new TrinityGraphPlanAssemblerImpl(byteEstimator);
    }

    /**
     * Converts the dedicated DAG propagator result into the common plan payload.
     */
    TrinityGraphPlanAssembly assembleAcyclic(TrinityAcyclicPlan acyclicPlan);

    /**
     * Converts aggregate acyclic firings and selected cycle blocks into the common plan payload.
     */
    TrinityAlgorithmResult<TrinityGraphPlanAssembly> assembleDemand(
                                                                    AEKey target,
                                                                    TrinityCraftingTopology topology,
                                                                    TrinityGraphDemandSolution demandSolution);

    /**
     * Applies exact long-boundary checks, byte estimation, statistics, and the final immutable plan builder.
     */
    TrinityCraftingPlan finalizePlan(
                                     TrinityGraphPlanContext context,
                                     TrinityGraphPlanAssembly assembly);
}
