package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicApplicability;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicApplicabilityResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing.TrinityDeterministicFiringCalculator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing.TrinityDeterministicFiringSolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.proof.TrinityDeterministicCandidate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.proof.TrinityDeterministicProofAssembler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicDiagnostics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityFiringVector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Coordinates deterministic applicability, exact firing calculation, and executable proof assembly.
 */
final class TrinityDeterministicComponentPlannerImpl implements TrinityDeterministicComponentPlanner {

    private final TrinityDeterministicApplicability applicability;
    private final TrinityDeterministicFiringCalculator firingCalculator;
    private final TrinityDeterministicProofAssembler proofAssembler;

    TrinityDeterministicComponentPlannerImpl(
                                             TrinityDeterministicApplicability applicability,
                                             TrinityDeterministicFiringCalculator firingCalculator,
                                             TrinityDeterministicProofAssembler proofAssembler) {
        this.applicability = applicability;
        this.firingCalculator = firingCalculator;
        this.proofAssembler = proofAssembler;
    }

    @Override
    public TrinityPlanningAttempt<TrinityDeterministicComponentPlan> plan(
                                                                          TrinityStronglyConnectedComponent component,
                                                                          TrinityCycleDemand demand,
                                                                          Map<AEKey, BigInteger> available,
                                                                          Set<AEKey> producibleInputs,
                                                                          int maxStates,
                                                                          TrinityPlanningControl control) {
        if (component == null || !component.cyclic() || demand == null || available == null ||
                producibleInputs == null || maxStates <= 0 || control == null) {
            throw new IllegalArgumentException("A deterministic Trinity component request is incomplete");
        }
        Map<AEKey, BigInteger> inventory = copyAvailable(available);
        Set<AEKey> producible = Set.copyOf(producibleInputs);
        ArrayList<TrinityDeterministicCandidate> completeCandidates = new ArrayList<>();
        ArrayList<TrinityDeterministicCandidate> localCandidates = new ArrayList<>();
        boolean rejectedReservoir = false;
        boolean basisLocalCandidate = false;

        for (AEKey reservoir : component.keys()) {
            TrinityDeterministicDiagnostics.StopState state = TrinityDeterministicDiagnostics.stopState(control);
            if (state != TrinityDeterministicDiagnostics.StopState.RUNNING) {
                return TrinityPlanningAttempt.terminal(TrinityDeterministicDiagnostics.stopped(state).diagnostic());
            }
            TrinityDeterministicApplicabilityResult assessed = this.applicability.assess(
                    component,
                    demand,
                    reservoir,
                    inventory);
            if (assessed.kind() == TrinityDeterministicApplicabilityResult.Kind.SKIP_RESERVOIR) {
                continue;
            }
            if (assessed.kind() == TrinityDeterministicApplicabilityResult.Kind.REJECT_RESERVOIR) {
                rejectedReservoir = true;
                continue;
            }
            TrinityAlgorithmResult<TrinityDeterministicFiringSolution> calculated = this.firingCalculator.calculate(
                    component,
                    demand,
                    inventory,
                    producible,
                    assessed.basis(),
                    control);
            if (!calculated.successful()) {
                TrinityPlanningAttempt<TrinityDeterministicComponentPlan> failure = handleFailure(calculated);
                if (failure.kind() == TrinityPlanningAttempt.Kind.TERMINAL) {
                    return failure;
                }
                continue;
            }
            TrinityAlgorithmResult<TrinityDeterministicCandidate> assembled = this.proofAssembler.assemble(
                    component,
                    demand,
                    inventory,
                    producible,
                    calculated.value(),
                    maxStates,
                    control);
            if (!assembled.successful()) {
                TrinityPlanningAttempt<TrinityDeterministicComponentPlan> failure = handleFailure(assembled);
                if (failure.kind() == TrinityPlanningAttempt.Kind.TERMINAL) {
                    return failure;
                }
                continue;
            }
            if (calculated.value().completeComponentProof()) {
                if (assembled.value().plan().macro().isPresent()) {
                    return TrinityPlanningAttempt.provedOptimal(assembled.value().plan());
                }
                completeCandidates.add(assembled.value());
                continue;
            }
            basisLocalCandidate |= calculated.value().leastFiringsProven() &&
                    !calculated.value().completeComponentProof();
            localCandidates.add(assembled.value());
        }
        if (!completeCandidates.isEmpty()) {
            completeCandidates.sort(TrinityDeterministicCandidate.ORDER);
            return TrinityPlanningAttempt.provedOptimal(completeCandidates.getFirst().plan());
        }
        if (rejectedReservoir && basisLocalCandidate) {
            return TrinityDeterministicDiagnostics.notApplicable();
        }
        if (localCandidates.isEmpty()) {
            return TrinityDeterministicDiagnostics.notApplicable();
        }
        TrinityFiringVector firstVector = localCandidates.getFirst().objective().identity();
        if (localCandidates.stream().anyMatch(candidate -> !candidate.objective().identity().equals(firstVector))) {
            return TrinityDeterministicDiagnostics.notApplicable();
        }
        localCandidates.sort(TrinityDeterministicCandidate.ORDER);
        return TrinityPlanningAttempt.provedOptimal(localCandidates.getFirst().plan());
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

    private static Map<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity deterministic-component inventory cannot be negative");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(copied);
    }
}
