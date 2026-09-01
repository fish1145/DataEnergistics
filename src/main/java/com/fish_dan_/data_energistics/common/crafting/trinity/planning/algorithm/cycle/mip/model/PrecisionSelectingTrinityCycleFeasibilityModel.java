package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.TrinityRadixCycleFeasibilityModel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Selects the ordinary model only while every exact input remains inside its conservative integer window.
 */
final class PrecisionSelectingTrinityCycleFeasibilityModel implements TrinityCycleFeasibilityModel {

    private static final int MAX_BOUNDED_ORDINARY_DOMAINS = 4;
    private static final BigInteger ORDINARY_EXACT_LIMIT = BigInteger.ONE.shiftLeft(52).subtract(BigInteger.ONE);
    private static final TrinityCycleObjectiveBounds OBJECTIVE_BOUNDS = TrinityCycleObjectiveBounds.create();

    private final TrinityCycleFeasibilityModel ordinary;
    private final TrinityCycleFeasibilityModel radix;

    PrecisionSelectingTrinityCycleFeasibilityModel() {
        TrinityIntegerResultVerifier integerVerifier = TrinityIntegerResultVerifier.create();
        TrinityExactConservationVerifier conservationVerifier = TrinityExactConservationVerifier.create();
        this.ordinary = new TrinityOrdinaryCycleFeasibilityModel(integerVerifier, conservationVerifier);
        this.radix = new TrinityRadixCycleFeasibilityModel(integerVerifier, conservationVerifier);
    }

    @Override
    public TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solve(
                                                                         TrinityCycleFeasibilityRequest request,
                                                                         TrinityPlanningMode mode,
                                                                         TrinityPlanningControl control) {
        return openSession(request).solve(request, mode, control);
    }

    @Override
    public TrinityCycleFeasibilitySession openSession(TrinityCycleFeasibilityRequest request) {
        return TrinityCycleFeasibilitySession.create(
                request,
                new PrecisionSessionSolver());
    }

    /**
     * Selects precision for every related request and initializes the ordinary template only on its first use.
     */
    private final class PrecisionSessionSolver implements TrinityCycleFeasibilitySession.Solver {

        private @Nullable TrinityCycleFeasibilitySession ordinarySession;

        @Override
        public TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solve(
                                                                             TrinityCycleFeasibilityRequest request,
                                                                             TrinityPlanningMode mode,
                                                                             TrinityPlanningControl control) {
            if (requiresRadix(request)) {
                if (mode == TrinityPlanningMode.FIRST_FEASIBLE) {
                    TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> bounded = solveBoundedOrdinary(
                            request,
                            control);
                    if (bounded.successful() ||
                            bounded.diagnostic().code() != TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT) {
                        return bounded;
                    }
                }
                return radix.solve(request, mode, control);
            }
            TrinityCycleFeasibilitySession session = this.ordinarySession;
            if (session == null) {
                control.recordSolverModel();
                session = ordinary.openSession(request);
                this.ordinarySession = session;
            }
            return session.solve(request, mode, control);
        }

        private TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solveBoundedOrdinary(
                                                                                             TrinityCycleFeasibilityRequest request,
                                                                                             TrinityPlanningControl control) {
            BigInteger firingUpper = OBJECTIVE_BOUNDS.compactFiringUpper(request);
            for (int domain = 0; domain < MAX_BOUNDED_ORDINARY_DOMAINS; domain++) {
                TrinityCycleFeasibilityRequest bounded = request.withOpenFiringUpper(firingUpper);
                if (requiresRadix(bounded)) {
                    return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                            TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                            Component.translatable(
                                    "gui.data_energistics.trinity_planning.mip.radix_model_limit"),
                            Map.of("phase", "bounded_ordinary_precision")));
                }
                TrinityCycleFeasibilitySession session = this.ordinarySession;
                if (session == null) {
                    control.recordSolverModel();
                    session = ordinary.openSession(bounded);
                    this.ordinarySession = session;
                }
                TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solved = session.solve(
                        bounded,
                        TrinityPlanningMode.FIRST_FEASIBLE,
                        control);
                if (solved.successful() ||
                        solved.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                    return solved;
                }
                firingUpper = firingUpper.multiply(firingUpper).max(BigInteger.TWO);
            }
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    Component.translatable(
                            "gui.data_energistics.trinity_planning.mip.schedule_search_limit"),
                    Map.of(
                            "phase", "bounded_ordinary_expansion",
                            "states", Integer.toString(MAX_BOUNDED_ORDINARY_DOMAINS))));
        }
    }

    private static boolean requiresRadix(TrinityCycleFeasibilityRequest request) {
        BigInteger logicalUpper = request.ordinaryLogicalUpperBound().orElse(null);
        if (logicalUpper == null || exceedsWindow(logicalUpper) ||
                exceedsWindow(request.seedLowerBound()) || exceedsWindow(request.firingLowerBound())) {
            return true;
        }
        if (request.shortageDiagnostic() &&
                exceedsWindow(OBJECTIVE_BOUNDS.shortageLogicalUpperBound(request))) {
            return true;
        }
        if (request.firingBounds().values().stream()
                .map(TrinityFiringBounds::lowerInclusive)
                .anyMatch(PrecisionSelectingTrinityCycleFeasibilityModel::exceedsWindow) ||
                request.fixedExternalTotal().filter(PrecisionSelectingTrinityCycleFeasibilityModel::exceedsWindow).isPresent()) {
            return true;
        }
        if (request.demand().finalBalanceLowerBounds().values().stream()
                .anyMatch(PrecisionSelectingTrinityCycleFeasibilityModel::exceedsWindow) ||
                request.demand().requiredNetChangeLowerBounds().values().stream()
                        .anyMatch(PrecisionSelectingTrinityCycleFeasibilityModel::exceedsWindow)) {
            return true;
        }
        LinkedHashSet<AEKey> touchedKeys = new LinkedHashSet<>();
        request.variants().forEach(variant -> touchedKeys.addAll(variant.netChange().keySet()));
        touchedKeys.addAll(request.demand().finalBalanceLowerBounds().keySet());
        touchedKeys.addAll(request.demand().requiredNetChangeLowerBounds().keySet());
        for (AEKey key : touchedKeys) {
            BigInteger rowEnvelope = request.variants().stream()
                    .map(variant -> variant.netChange()
                            .getOrDefault(key, BigInteger.ZERO)
                            .abs()
                            .multiply(logicalUpper))
                    .reduce(BigInteger.ZERO, BigInteger::add);
            if (request.internalKeys().contains(key) || externalReserveKeys(request).contains(key)) {
                rowEnvelope = rowEnvelope.add(reserveUpperBound(request, key));
            }
            if (exceedsWindow(rowEnvelope)) {
                return true;
            }
        }
        BigInteger firingObjectiveEnvelope = logicalUpper.multiply(
                BigInteger.valueOf(request.variants().size()));
        BigInteger seedObjectiveEnvelope = request.internalKeys().stream()
                .map(key -> reserveUpperBound(request, key))
                .reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger externalObjectiveEnvelope = externalReserveKeys(request).stream()
                .map(key -> reserveUpperBound(request, key))
                .reduce(BigInteger.ZERO, BigInteger::add);
        if (exceedsWindow(firingObjectiveEnvelope) || exceedsWindow(seedObjectiveEnvelope) ||
                exceedsWindow(externalObjectiveEnvelope)) {
            return true;
        }
        if (!request.shortageDiagnostic()) {
            return false;
        }
        LinkedHashSet<AEKey> reserveKeys = new LinkedHashSet<>(request.internalKeys());
        reserveKeys.addAll(externalReserveKeys(request));
        BigInteger missingEnvelope = BigInteger.ZERO;
        for (AEKey key : reserveKeys) {
            if (request.producibleInputs().contains(key)) {
                continue;
            }
            BigInteger requiredUpper = reserveUpperBound(request, key);
            BigInteger actualUpper = request.available().getOrDefault(key, BigInteger.ZERO).min(requiredUpper);
            if (exceedsWindow(requiredUpper.add(actualUpper).add(requiredUpper))) {
                return true;
            }
            missingEnvelope = missingEnvelope.add(requiredUpper);
        }
        return exceedsWindow(missingEnvelope);
    }

    private static Set<AEKey> externalReserveKeys(TrinityCycleFeasibilityRequest request) {
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
        request.variants().forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !request.internalKeys().contains(key))
                .forEach(keys::add));
        request.demand().finalBalanceLowerBounds().keySet().stream()
                .filter(key -> !request.internalKeys().contains(key))
                .forEach(keys::add);
        return Set.copyOf(keys);
    }

    private static BigInteger reserveUpperBound(TrinityCycleFeasibilityRequest request, AEKey key) {
        return OBJECTIVE_BOUNDS.reserveUpperBound(
                request,
                key,
                request.ordinaryLogicalUpperBound().orElseThrow());
    }

    private static boolean exceedsWindow(BigInteger value) {
        return value.abs().compareTo(ORDINARY_EXACT_LIMIT) > 0;
    }
}
