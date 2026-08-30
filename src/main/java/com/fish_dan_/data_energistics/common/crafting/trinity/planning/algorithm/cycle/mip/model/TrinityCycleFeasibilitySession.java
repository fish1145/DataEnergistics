package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Request-private feasibility boundary that may reuse immutable model structure across related firing boxes.
 * <p>
 * A session belongs to one planning request and is not thread-safe. Only firing domains and sequential objective
 * bounds may vary between solves; graph, demand and inventory identity remain fixed for the complete lifetime.
 */
public final class TrinityCycleFeasibilitySession {

    private final RequestStructure structure;
    private final Solver solver;
    private final Thread ownerThread;

    static TrinityCycleFeasibilitySession create(
                                                 TrinityCycleFeasibilityRequest request,
                                                 Solver solver) {
        return new TrinityCycleFeasibilitySession(RequestStructure.from(request), solver);
    }

    private TrinityCycleFeasibilitySession(RequestStructure structure, Solver solver) {
        this.structure = structure;
        this.solver = solver;
        this.ownerThread = Thread.currentThread();
    }

    /**
     * Solves one related firing box after rejecting structural changes that would invalidate the private template.
     * Only firing bounds, fixed external total, seed lower bound and firing lower bound may differ from the request
     * that opened this session. The call must run on the creating thread and may create mutable per-pass model copies;
     * it does not mutate shared caches or server-lifetime solver state.
     *
     * @param request related immutable request whose structural fields match the opening request
     * @param mode    optimization or first-feasible solve policy
     * @param control request-local cancellation and deadline boundary
     * @return exactly verified feasibility solution or stable diagnostic
     * @throws IllegalArgumentException if a structural request field changed
     * @throws IllegalStateException    if called from a thread other than the creating planning thread
     */
    public TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solve(
                                                                         TrinityCycleFeasibilityRequest request,
                                                                         TrinityPlanningMode mode,
                                                                         TrinityPlanningControl control) {
        if (Thread.currentThread() != this.ownerThread) {
            throw new IllegalStateException("A Trinity feasibility session cannot cross planning threads");
        }
        if (!this.structure.equals(RequestStructure.from(request))) {
            throw new IllegalArgumentException("A Trinity feasibility session cannot change structural inputs");
        }
        return this.solver.solve(request, mode, control);
    }

    /**
     * Internal request-local solver callback used to supply precision-specific session behavior.
     * <p>
     * Backends run only after structure and thread validation. They may lazily assemble private templates and
     * allocate mutable model copies, but must not retain them beyond this session or publish them to shared state.
     */
    @FunctionalInterface
    interface Solver {

        /**
         * @param request structurally validated related request
         * @param mode    optimization or first-feasible solve policy
         * @param control request-local cancellation and deadline boundary
         * @return exactly verified feasibility solution or stable diagnostic
         * @throws IllegalArgumentException if dynamic request bounds cannot be represented by the selected backend
         * @throws IllegalStateException    if a solver invariant is violated
         */
        TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solve(
                                                                      TrinityCycleFeasibilityRequest request,
                                                                      TrinityPlanningMode mode,
                                                                      TrinityPlanningControl control);
    }

    private record RequestStructure(
                                    List<TrinityPatternVariant> variants,
                                    Set<AEKey> internalKeys,
                                    TrinityCycleDemand demand,
                                    Map<AEKey, BigInteger> available,
                                    Set<AEKey> producibleInputs) {

        private static RequestStructure from(TrinityCycleFeasibilityRequest request) {
            return new RequestStructure(
                    request.variants(),
                    request.internalKeys(),
                    request.demand(),
                    request.available(),
                    request.producibleInputs());
        }
    }
}
