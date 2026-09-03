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
     * Opens one request-private solve boundary so related firing boxes may reuse immutable structural coefficients.
     * The returned session accepts changes only to firing domains, fixed external total, seed lower bound and firing
     * lower bound. It is confined to the creating thread and current planning request; it must not be cached or shared.
     *
     * @param request initial request defining the invariant graph, demand, inventory and producible-input structure
     * @return thread-confined session for this request family
     * @throws IllegalArgumentException if the implementation cannot establish a complete session structure
     * @implNote Opening a session may assemble a request-private template. Mutable per-pass models remain inside the
     *           session and no shared solver state is changed.
     */
    default TrinityCycleFeasibilitySession openSession(TrinityCycleFeasibilityRequest request) {
        return TrinityCycleFeasibilitySession.create(request, this::solve);
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
}
