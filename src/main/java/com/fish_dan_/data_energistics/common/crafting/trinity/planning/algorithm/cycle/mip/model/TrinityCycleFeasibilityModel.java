package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;

/**
 * Produces one exact lexicographically minimal conservation-feasible SCC firing candidate.
 */
public interface TrinityCycleFeasibilityModel {

    /**
     * @return precision-selecting ordinary/radix feasibility model
     */
    static TrinityCycleFeasibilityModel create() {
        return new PrecisionSelectingTrinityCycleFeasibilityModel();
    }

    /**
     * @param request immutable exact model request
     * @param control shared cancellation and deadline boundary for every objective pass
     * @return exactly reconstructed candidate or a stable bounded diagnostic
     */
    TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solve(
                                                                  TrinityCycleFeasibilityRequest request,
                                                                  TrinityPlanningMode mode,
                                                                  TrinityPlanningControl control);

    /**
     * Compatibility entry point that retains complete optimisation.
     */
    default TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solve(
                                                                          TrinityCycleFeasibilityRequest request,
                                                                          TrinityPlanningControl control) {
        return solve(request, TrinityPlanningMode.OPTIMAL, control);
    }
}
