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
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
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
    private final TrinityRadixCycleFeasibilityModel radix;

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
            if (request.shortageDiagnostic()) return solveShortage(request, control);
            if (control.cancellationRequested()) {
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        Component.translatable("gui.data_energistics.trinity_planning.diagnostic.cancelled"),
                        Map.of()));
            }
            if (control.deadlineExceeded()) {
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        Component.translatable("gui.data_energistics.trinity_planning.mip.timeout"),
                        Map.of("phase", "settled_seed")));
            }
            TrinityPlanningDiagnostic settledSeedFailure = settledSeedFailure(request);
            if (settledSeedFailure != null) {
                return TrinityAlgorithmResult.failure(settledSeedFailure);
            }
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
            TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solved = session.solve(request, mode, control);
            return !solved.successful() && solved.diagnostic().code() == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT ?
                    radix.solve(request, mode, control) : solved;
        }

        private TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solveShortage(
                                                                                      TrinityCycleFeasibilityRequest request, TrinityPlanningControl control) {
            ShortageAccounting accounting = new ShortageAccounting(request.shortageStateLimit());
            BigInteger firingUpper = OBJECTIVE_BOUNDS.compactFiringUpper(request);
            for (int domain = 0; domain < MAX_BOUNDED_ORDINARY_DOMAINS; domain++) {
                if (accounting.remaining() == 0) return accounting.stopped();
                TrinityCycleFeasibilityRequest bounded = request.withOpenFiringUpper(firingUpper)
                        .forShortageDiagnosis(accounting.remaining());
                if (requiresRadix(bounded)) break;
                TrinityCycleFeasibilitySession session = this.ordinarySession;
                if (session == null) {
                    control.recordSolverModel();
                    session = ordinary.openSession(bounded);
                    this.ordinarySession = session;
                }
                TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solved = session.solve(
                        bounded, TrinityPlanningMode.FIRST_FEASIBLE, control);
                accounting.include(solved);
                if (solved.successful() || (solved.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION &&
                        solved.diagnostic().code() != TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT)) {
                    return accounting.finish(solved);
                }
                firingUpper = firingUpper.shiftLeft(1);
            }
            if (accounting.remaining() == 0) return accounting.stopped();
            TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solved = radix.solveShortage(
                    request.forShortageDiagnosis(accounting.remaining()), control, firingUpper);
            accounting.include(solved);
            return accounting.finish(solved);
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
                        (solved.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION &&
                                solved.diagnostic().code() != TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT)) {
                    return solved;
                }
                firingUpper = firingUpper.shiftLeft(1);
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

    /**
     * A non-exported internal key must have zero net change when another internal key is exported. Its final
     * reserve therefore cannot exceed real stock unless a predecessor may supply it. This contradiction holds
     * for every firing domain; virtual diagnostic reserves deliberately bypass this executable-only check.
     */
    private static @Nullable TrinityPlanningDiagnostic settledSeedFailure(TrinityCycleFeasibilityRequest request) {
        Set<AEKey> exportedKeys = request.demand().requiredNetChangeLowerBounds().keySet();
        if (request.internalKeys().stream().noneMatch(exportedKeys::contains)) {
            return null;
        }
        for (Map.Entry<AEKey, BigInteger> bound : request.demand().finalBalanceLowerBounds().entrySet()) {
            AEKey key = bound.getKey();
            if (!request.internalKeys().contains(key) || exportedKeys.contains(key) ||
                    request.producibleInputs().contains(key)) {
                continue;
            }
            BigInteger available = request.available().getOrDefault(key, BigInteger.ZERO);
            if (bound.getValue().compareTo(available) > 0) {
                return new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                        Component.translatable("gui.data_energistics.trinity_planning.diagnostic.no_integer_solution"),
                        Map.of("constraint", "settled_seed", "key", key.toString(),
                                "required", bound.getValue().toString(), "available", available.toString()));
            }
        }
        return null;
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
        Set<AEKey> externalKeys = OBJECTIVE_BOUNDS.externalReserveKeys(request);
        ObjectLinkedOpenHashSet<AEKey> touchedKeys = new ObjectLinkedOpenHashSet<>();
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
            if (request.internalKeys().contains(key) || externalKeys.contains(key)) {
                rowEnvelope = rowEnvelope.add(OBJECTIVE_BOUNDS.reserveUpperBound(request, key, logicalUpper));
            }
            if (exceedsWindow(rowEnvelope)) {
                return true;
            }
        }
        BigInteger firingObjectiveEnvelope = logicalUpper.multiply(
                BigInteger.valueOf(request.variants().size()));
        BigInteger seedObjectiveEnvelope = request.internalKeys().stream()
                .map(key -> OBJECTIVE_BOUNDS.reserveUpperBound(request, key, logicalUpper))
                .reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger externalObjectiveEnvelope = externalKeys.stream()
                .map(key -> OBJECTIVE_BOUNDS.reserveUpperBound(request, key, logicalUpper))
                .reduce(BigInteger.ZERO, BigInteger::add);
        return exceedsWindow(firingObjectiveEnvelope) || exceedsWindow(seedObjectiveEnvelope) ||
                exceedsWindow(externalObjectiveEnvelope);
    }

    /**
     * Aggregates actual solver work across precision changes without refilling the diagnostic budget.
     */
    private static final class ShortageAccounting {

        private final int limit;
        private int states;
        private int invocations;
        private int passes;
        private long nanos;

        private ShortageAccounting(int limit) {
            this.limit = limit;
        }

        private int remaining() {
            return this.limit - this.states;
        }

        private void include(TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> result) {
            this.invocations++;
            int consumed;
            int completed;
            long elapsed;
            if (result.successful()) {
                consumed = result.value().diagnosticStates();
                completed = result.value().solverPasses();
                elapsed = result.value().solverNanos();
            } else {
                Map<String, String> metadata = result.diagnostic().metadata();
                consumed = Integer.parseInt(metadata.get("states"));
                completed = Integer.parseInt(metadata.get("passes"));
                elapsed = Long.parseLong(metadata.get("nanos"));
            }
            if (consumed < 0 || consumed > remaining()) {
                throw new IllegalStateException("A Trinity shortage backend exceeded its remaining budget");
            }
            this.states += consumed;
            this.passes = Math.addExact(this.passes, completed);
            this.nanos = Math.addExact(this.nanos, elapsed);
        }

        private TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> finish(
                                                                               TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> result) {
            if (result.successful()) {
                TrinityCycleFeasibilitySolution solved = result.value();
                if (this.invocations == 1) return result;
                return TrinityAlgorithmResult.success(new TrinityCycleFeasibilitySolution(
                        solved.firings(), solved.modelSeed(), solved.externalInputs(), this.passes, this.nanos,
                        solved.radix(), TrinityPlanQuality.VERIFIED_FEASIBLE,
                        solved.actualInputs(), solved.missingInputs(), this.states));
            }
            TrinityPlanningDiagnostic diagnostic = result.diagnostic();
            Object2ObjectLinkedOpenHashMap<String, String> metadata = new Object2ObjectLinkedOpenHashMap<>(diagnostic.metadata());
            metadata.put("states", Integer.toString(this.states));
            metadata.put("limit", Integer.toString(this.limit));
            metadata.put("passes", Integer.toString(this.passes));
            metadata.put("nanos", Long.toString(this.nanos));
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    diagnostic.code(), diagnostic.message(), metadata, diagnostic.detail()));
        }

        private TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> stopped() {
            return finish(TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    Component.translatable("gui.data_energistics.trinity_planning.mip.schedule_search_limit"),
                    Map.of("phase", "shortage_state_limit"))));
        }
    }

    private static boolean exceedsWindow(BigInteger value) {
        return value.abs().compareTo(ORDINARY_EXACT_LIMIT) > 0;
    }
}
