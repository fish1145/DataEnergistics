package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicApplicability;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicApplicabilityResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicBasis;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing.TrinityDeterministicFiringCalculator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing.TrinityDeterministicFiringSolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.proof.TrinityDeterministicProofAssembler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicDiagnostics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof.TrinityCycleUnitProof;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Solves every demanded output of a unique-producer SCC without expanding logical firing counts.
 * <p>
 * Coordinates deterministic applicability, exact firing calculation, and executable proof assembly.
 */
public final class TrinityDeterministicComponentPlanner {

    /**
     * @return planner composed from exact cycle-ratio and repeat-scheduling implementations
     */
    public static TrinityDeterministicComponentPlanner create() {
        return new TrinityDeterministicComponentPlanner(
                TrinityDeterministicApplicability.create(TrinityDeterministicCycleSequence.create()),
                TrinityDeterministicFiringCalculator.create(),
                TrinityDeterministicProofAssembler.create(TrinityDeterministicRepeatScheduler.create()));
    }

    private final TrinityDeterministicApplicability applicability;
    private final TrinityDeterministicFiringCalculator firingCalculator;
    private final TrinityDeterministicProofAssembler proofAssembler;

    TrinityDeterministicComponentPlanner(
                                         TrinityDeterministicApplicability applicability,
                                         TrinityDeterministicFiringCalculator firingCalculator,
                                         TrinityDeterministicProofAssembler proofAssembler) {
        this.applicability = applicability;
        this.firingCalculator = firingCalculator;
        this.proofAssembler = proofAssembler;
    }

    /**
     * Attempts the structural fast path for one component.
     *
     * @param component        immutable cyclic component
     * @param demand           all internal and boundary-output lower bounds owned by the component
     * @param available        immutable non-negative inventory snapshot
     * @param producibleInputs inputs that predecessor DAG stages may supply
     * @param maxStates        positive graph-bounded planning and scheduling limit
     * @param control          cancellation and shared deadline boundary
     * @return exact feasible plan, structural miss requiring the general solver, or terminal shared-budget failure
     */
    public TrinityPlanningAttempt<TrinityDeterministicComponentPlan> plan(
                                                                          TrinityStronglyConnectedComponent component,
                                                                          TrinityCycleDemand demand,
                                                                          Map<AEKey, BigInteger> available,
                                                                          Set<AEKey> producibleInputs,
                                                                          int maxStates,
                                                                          TrinityPlanningControl control) {
        return plan(component, demand, available, producibleInputs, maxStates, control, null);
    }

    /** Attempts a cached semantic unit before deriving the same primitive basis again. */
    public TrinityPlanningAttempt<TrinityDeterministicComponentPlan> plan(
                                                                          TrinityStronglyConnectedComponent component,
                                                                          TrinityCycleDemand demand,
                                                                          Map<AEKey, BigInteger> available,
                                                                          Set<AEKey> producibleInputs,
                                                                          int maxStates,
                                                                          TrinityPlanningControl control,
                                                                          @Nullable TrinityCycleUnitProof unitProof) {
        if (!component.cyclic() || maxStates <= 0) {
            throw new IllegalArgumentException("A deterministic Trinity component request is incomplete");
        }
        TrinityPlanningAttempt<TrinityDeterministicComponentPlan> supplied = TrinitySuppliedSingleRecipePlanner.plan(
                component, demand, available, producibleInputs, maxStates, control);
        if (supplied.kind() != TrinityPlanningAttempt.Kind.NOT_APPLICABLE) {
            return supplied;
        }
        if (unitProof != null) {
            TrinityDeterministicApplicabilityResult cached = this.applicability.assess(
                    component,
                    demand,
                    unitProof,
                    available);
            if (cached.kind() == TrinityDeterministicApplicabilityResult.Kind.APPLICABLE) {
                TrinityPlanningAttempt<TrinityDeterministicComponentPlan> attempted = attemptBasis(
                        component,
                        demand,
                        available,
                        producibleInputs,
                        maxStates,
                        control,
                        cached.basis());
                if (attempted.kind() != TrinityPlanningAttempt.Kind.NOT_APPLICABLE) {
                    return attempted;
                }
            }
        }
        for (AEKey reservoir : component.keys()) {
            TrinityDeterministicDiagnostics.StopState state = TrinityDeterministicDiagnostics.stopState(control);
            if (state != TrinityDeterministicDiagnostics.StopState.RUNNING) {
                return TrinityPlanningAttempt.terminal(TrinityDeterministicDiagnostics.stopped(state).diagnostic());
            }
            TrinityDeterministicApplicabilityResult assessed = this.applicability.assess(
                    component,
                    demand,
                    reservoir,
                    available);
            if (assessed.kind() == TrinityDeterministicApplicabilityResult.Kind.SKIP_RESERVOIR) {
                continue;
            }
            if (assessed.kind() == TrinityDeterministicApplicabilityResult.Kind.REJECT_RESERVOIR) {
                continue;
            }
            TrinityPlanningAttempt<TrinityDeterministicComponentPlan> attempted = attemptBasis(
                    component,
                    demand,
                    available,
                    producibleInputs,
                    maxStates,
                    control,
                    assessed.basis());
            if (attempted.kind() != TrinityPlanningAttempt.Kind.NOT_APPLICABLE) {
                return attempted;
            }
        }
        return TrinityDeterministicDiagnostics.notApplicable();
    }

    private TrinityPlanningAttempt<TrinityDeterministicComponentPlan> attemptBasis(
                                                                                   TrinityStronglyConnectedComponent component,
                                                                                   TrinityCycleDemand demand,
                                                                                   Map<AEKey, BigInteger> available,
                                                                                   Set<AEKey> producibleInputs,
                                                                                   int maxStates,
                                                                                   TrinityPlanningControl control,
                                                                                   TrinityDeterministicBasis basis) {
        TrinityAlgorithmResult<TrinityDeterministicFiringSolution> calculated = this.firingCalculator.calculate(
                component,
                demand,
                available,
                producibleInputs,
                basis,
                control);
        if (!calculated.successful()) {
            return handleFailure(calculated);
        }
        TrinityAlgorithmResult<TrinityDeterministicComponentPlan> assembled = this.proofAssembler.assemble(
                component,
                demand,
                available,
                producibleInputs,
                calculated.value(),
                maxStates,
                control);
        return assembled.successful() ?
                TrinityPlanningAttempt.feasible(assembled.value()) : handleFailure(assembled);
    }

    private static TrinityPlanningAttempt<TrinityDeterministicComponentPlan> handleFailure(
                                                                                           TrinityAlgorithmResult<?> failed) {
        TrinityPlanningDiagnosticCode code = failed.diagnostic().code();
        if (code == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED ||
                code == TrinityPlanningDiagnosticCode.MIP_TIMEOUT) {
            return TrinityPlanningAttempt.terminal(failed.diagnostic());
        }
        return TrinityPlanningAttempt.notApplicable(failed.diagnostic());
    }
}
