package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.TrinityRadixCycleFeasibilityModel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;

import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Selects the ordinary model only while every exact input remains inside its conservative integer window.
 */
final class PrecisionSelectingTrinityCycleFeasibilityModel implements TrinityCycleFeasibilityModel {

    private static final BigInteger ORDINARY_EXACT_LIMIT = BigInteger.ONE.shiftLeft(52).subtract(BigInteger.ONE);

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
        if (request == null || mode == null || control == null) {
            throw new IllegalArgumentException("A Trinity feasibility solve requires a request and control");
        }
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
    }

    private static boolean requiresRadix(TrinityCycleFeasibilityRequest request) {
        BigInteger logicalUpper = request.ordinaryLogicalUpperBound().orElse(null);
        if (logicalUpper == null || exceedsWindow(logicalUpper) ||
                exceedsWindow(request.seedLowerBound()) || exceedsWindow(request.firingLowerBound())) {
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
        return exceedsWindow(firingObjectiveEnvelope) || exceedsWindow(seedObjectiveEnvelope) ||
                exceedsWindow(externalObjectiveEnvelope);
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
        BigInteger proven = request.ordinaryLogicalUpperBound().orElseThrow();
        return request.producibleInputs().contains(key) ?
                proven : request.available().getOrDefault(key, BigInteger.ZERO).min(proven);
    }

    private static boolean exceedsWindow(BigInteger value) {
        return value.abs().compareTo(ORDINARY_EXACT_LIMIT) > 0;
    }
}
