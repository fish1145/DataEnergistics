package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate.Coefficient;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Ordinary exact-window ojAlgo model retaining the established sequential objective semantics.
 */
final class TrinityOrdinaryCycleFeasibilityModel implements TrinityCycleFeasibilityModel {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final TrinityIntegerResultVerifier integerVerifier;
    private final TrinityExactConservationVerifier conservationVerifier;
    private final TrinityCycleObjectiveBounds objectiveBounds;

    TrinityOrdinaryCycleFeasibilityModel(TrinityIntegerResultVerifier integerVerifier,
                                         TrinityExactConservationVerifier conservationVerifier) {
        this.integerVerifier = integerVerifier;
        this.conservationVerifier = conservationVerifier;
        this.objectiveBounds = TrinityCycleObjectiveBounds.create();
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
        OrdinaryModelTemplate modelTemplate = createModelTemplate(request);
        return TrinityCycleFeasibilitySession.create(
                request,
                (current, mode, control) -> solve(current, mode, control, modelTemplate));
    }

    private TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solve(
                                                                          TrinityCycleFeasibilityRequest request,
                                                                          TrinityPlanningMode mode,
                                                                          TrinityPlanningControl control,
                                                                          OrdinaryModelTemplate modelTemplate) {
        SolverMetrics metrics = new SolverMetrics();
        if (request.shortageDiagnostic()) {
            return solveShortage(
                    request,
                    control,
                    modelTemplate,
                    metrics,
                    TrinityCycleSolveBudget.limited(request.shortageStateLimit()));
        }
        if (mode == TrinityPlanningMode.FIRST_FEASIBLE) {
            TrinityAlgorithmResult<SolvedPass> feasible = optimize(
                    request,
                    FeasibilityPass.INSTANCE,
                    modelTemplate,
                    control,
                    metrics);
            return feasible.successful() ?
                    solution(feasible.value().model(), metrics, TrinityPlanQuality.VERIFIED_FEASIBLE) :
                    TrinityAlgorithmResult.failure(feasible.diagnostic());
        }

        TrinityAlgorithmResult<SolvedPass> external = optimize(
                request,
                ExternalPass.INSTANCE,
                modelTemplate,
                control,
                metrics);
        if (!external.successful()) {
            return TrinityAlgorithmResult.failure(external.diagnostic());
        }
        if (!external.value().objectiveProved()) {
            return solution(external.value().model(), metrics, TrinityPlanQuality.VERIFIED_FEASIBLE);
        }
        SolvedModel incumbent = external.value().model();
        BigInteger optimalExternal = total(incumbent.externalInputs());
        BigInteger seedLower = request.seedLowerBound();
        BigInteger firingLower = request.firingLowerBound();
        while (true) {
            TrinityAlgorithmResult<SolvedPass> seed = optimize(
                    request,
                    new SeedPass(optimalExternal, seedLower),
                    modelTemplate,
                    control,
                    metrics);
            if (!seed.successful()) {
                return recoverIncumbent(incumbent, metrics, seed.diagnostic());
            }
            incumbent = seed.value().model();
            if (!seed.value().objectiveProved()) {
                return solution(incumbent, metrics, TrinityPlanQuality.VERIFIED_FEASIBLE);
            }
            BigInteger optimalSeed = total(incumbent.modelSeed());
            BigInteger firingObjectiveLower = firingLower.max(
                    this.objectiveBounds.conservationFiringLowerBound(request, optimalExternal, optimalSeed));
            BigInteger seedWitnessFirings = total(incumbent.firings());
            TrinityAlgorithmResult<SolvedPass> firing = seedWitnessFirings.equals(firingObjectiveLower) ?
                    TrinityAlgorithmResult.success(new SolvedPass(incumbent, true)) :
                    optimize(
                            request,
                            new FiringPass(optimalExternal, optimalSeed, firingObjectiveLower),
                            modelTemplate,
                            control,
                            metrics);
            if (!firing.successful()) {
                if (firing.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                    return recoverIncumbent(incumbent, metrics, firing.diagnostic());
                }
                seedLower = optimalSeed.add(BigInteger.ONE);
                firingLower = BigInteger.ZERO;
                continue;
            }
            incumbent = firing.value().model();
            if (!firing.value().objectiveProved()) {
                return solution(incumbent, metrics, TrinityPlanQuality.VERIFIED_FEASIBLE);
            }
            BigInteger optimalFirings = total(incumbent.firings());
            LinkedHashMap<TrinityPatternVariant, BigInteger> fixedFirings = new LinkedHashMap<>();
            SolvedModel canonical = incumbent;
            for (TrinityPatternVariant variant : request.variants()) {
                TrinityFiringBounds bounds = request.firingBounds().get(variant);
                if (bounds.lowerInclusive().equals(bounds.upperInclusive())) {
                    BigInteger fixedCount = canonical.firings().getOrDefault(variant, BigInteger.ZERO);
                    if (!fixedCount.equals(bounds.lowerInclusive())) {
                        throw new IllegalStateException("An exact Trinity firing solution violated a fixed axis");
                    }
                    fixedFirings.put(variant, fixedCount);
                    continue;
                }
                IdentityPass identityPass = new IdentityPass(
                        optimalExternal,
                        optimalSeed,
                        optimalFirings,
                        fixedFirings,
                        variant);
                BigInteger witnessCount = canonical.firings().getOrDefault(variant, BigInteger.ZERO);
                BigInteger identityUpper = this.objectiveBounds.identityObjectiveUpperBound(
                        request,
                        optimalExternal,
                        optimalSeed,
                        optimalFirings,
                        fixedFirings,
                        variant)
                        .min(bounds.upperInclusive());
                if (witnessCount.compareTo(identityUpper) > 0) {
                    throw new IllegalStateException("A Trinity identity witness exceeded its proven upper bound");
                }
                if (witnessCount.equals(identityUpper)) {
                    fixedFirings.put(variant, witnessCount);
                    continue;
                }
                TrinityAlgorithmResult<SolvedPass> identity = optimize(
                        request,
                        identityPass,
                        modelTemplate,
                        control,
                        metrics);
                if (!identity.successful()) {
                    return recoverIncumbent(canonical, metrics, identity.diagnostic());
                }
                canonical = identity.value().model();
                if (!identity.value().objectiveProved()) {
                    return solution(canonical, metrics, TrinityPlanQuality.VERIFIED_FEASIBLE);
                }
                fixedFirings.put(variant, canonical.firings().getOrDefault(variant, BigInteger.ZERO));
            }
            return solution(canonical, metrics, TrinityPlanQuality.PROVED_OPTIMAL);
        }
    }

    /**
     * Proves the minimum virtual reserve required to repair a root-infeasible finite-inventory domain. Unlike
     * executable anytime optimisation, every objective must be proved before diagnostic evidence can be published.
     */
    private TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solveShortage(
                                                                                  TrinityCycleFeasibilityRequest request,
                                                                                  TrinityPlanningControl control,
                                                                                  OrdinaryModelTemplate modelTemplate,
                                                                                  SolverMetrics metrics,
                                                                                  TrinityCycleSolveBudget stateBudget) {
        TrinityAlgorithmResult<SolvedPass> missing = optimize(
                request,
                ShortageMissingPass.INSTANCE,
                modelTemplate,
                control,
                metrics,
                stateBudget);
        if (!missing.successful()) {
            return shortageFailure(missing.diagnostic(), stateBudget);
        }
        if (!missing.value().objectiveProved()) {
            return unprovedShortage(metrics, stateBudget, "missing");
        }
        SolvedModel incumbent = missing.value().model();
        BigInteger optimalMissing = total(incumbent.missingInputs());
        if (optimalMissing.signum() == 0) {
            return solution(
                    incumbent,
                    metrics,
                    TrinityPlanQuality.PROVED_OPTIMAL,
                    stateBudget.used());
        }

        TrinityAlgorithmResult<SolvedPass> external = optimize(
                request,
                new ShortageExternalPass(optimalMissing),
                modelTemplate,
                control,
                metrics,
                stateBudget);
        if (!external.successful()) {
            return shortageFailure(external.diagnostic(), stateBudget);
        }
        if (!external.value().objectiveProved()) {
            return unprovedShortage(metrics, stateBudget, "external");
        }
        incumbent = external.value().model();
        BigInteger optimalExternal = total(incumbent.externalInputs());
        BigInteger seedLower = request.seedLowerBound();
        BigInteger firingLower = request.firingLowerBound();
        while (true) {
            TrinityAlgorithmResult<SolvedPass> seed = optimize(
                    request,
                    new ShortageSeedPass(optimalMissing, optimalExternal, seedLower),
                    modelTemplate,
                    control,
                    metrics,
                    stateBudget);
            if (!seed.successful()) {
                return shortageFailure(seed.diagnostic(), stateBudget);
            }
            if (!seed.value().objectiveProved()) {
                return unprovedShortage(metrics, stateBudget, "seed");
            }
            incumbent = seed.value().model();
            BigInteger optimalSeed = total(incumbent.modelSeed());
            BigInteger firingObjectiveLower = firingLower.max(
                    this.objectiveBounds.conservationFiringLowerBound(request, optimalExternal, optimalSeed));
            BigInteger seedWitnessFirings = total(incumbent.firings());
            TrinityAlgorithmResult<SolvedPass> firing = seedWitnessFirings.equals(firingObjectiveLower) ?
                    TrinityAlgorithmResult.success(new SolvedPass(incumbent, true)) :
                    optimize(
                            request,
                            new ShortageFiringPass(
                                    optimalMissing,
                                    optimalExternal,
                                    optimalSeed,
                                    firingObjectiveLower),
                            modelTemplate,
                            control,
                            metrics,
                            stateBudget);
            if (!firing.successful()) {
                if (firing.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                    return shortageFailure(firing.diagnostic(), stateBudget);
                }
                seedLower = optimalSeed.add(BigInteger.ONE);
                firingLower = BigInteger.ZERO;
                continue;
            }
            if (!firing.value().objectiveProved()) {
                return unprovedShortage(metrics, stateBudget, "firing");
            }
            incumbent = firing.value().model();
            BigInteger optimalFirings = total(incumbent.firings());
            LinkedHashMap<TrinityPatternVariant, BigInteger> fixedFirings = new LinkedHashMap<>();
            SolvedModel canonical = incumbent;
            for (TrinityPatternVariant variant : request.variants()) {
                TrinityFiringBounds bounds = request.firingBounds().get(variant);
                if (bounds.lowerInclusive().equals(bounds.upperInclusive())) {
                    BigInteger fixedCount = canonical.firings().getOrDefault(variant, BigInteger.ZERO);
                    if (!fixedCount.equals(bounds.lowerInclusive())) {
                        throw new IllegalStateException("An exact Trinity shortage solution violated a fixed axis");
                    }
                    fixedFirings.put(variant, fixedCount);
                    continue;
                }
                BigInteger witnessCount = canonical.firings().getOrDefault(variant, BigInteger.ZERO);
                BigInteger identityUpper = this.objectiveBounds.identityObjectiveUpperBound(
                        request,
                        optimalExternal,
                        optimalSeed,
                        optimalFirings,
                        fixedFirings,
                        variant)
                        .min(bounds.upperInclusive());
                if (witnessCount.compareTo(identityUpper) > 0) {
                    throw new IllegalStateException("A Trinity shortage identity witness exceeded its proven upper bound");
                }
                if (witnessCount.equals(identityUpper)) {
                    fixedFirings.put(variant, witnessCount);
                    continue;
                }
                TrinityAlgorithmResult<SolvedPass> identity = optimize(
                        request,
                        new ShortageIdentityPass(
                                optimalMissing,
                                optimalExternal,
                                optimalSeed,
                                optimalFirings,
                                fixedFirings,
                                variant),
                        modelTemplate,
                        control,
                        metrics,
                        stateBudget);
                if (!identity.successful()) {
                    return shortageFailure(identity.diagnostic(), stateBudget);
                }
                if (!identity.value().objectiveProved()) {
                    return unprovedShortage(metrics, stateBudget, "identity");
                }
                canonical = identity.value().model();
                fixedFirings.put(variant, canonical.firings().getOrDefault(variant, BigInteger.ZERO));
            }
            LinkedHashMap<AEKey, BigInteger> fixedReserves = new LinkedHashMap<>();
            LinkedHashSet<AEKey> reserveKeys = diagnosticReserveKeys(request);
            for (AEKey key : reserveKeys) {
                BigInteger witness = requiredInputs(canonical).getOrDefault(key, BigInteger.ZERO);
                if (witness.signum() == 0) {
                    fixedReserves.put(key, BigInteger.ZERO);
                    continue;
                }
                TrinityAlgorithmResult<SolvedPass> reserve = optimize(
                        request,
                        new ShortageReservePass(
                                optimalMissing,
                                optimalExternal,
                                optimalSeed,
                                optimalFirings,
                                fixedFirings,
                                fixedReserves,
                                key),
                        modelTemplate,
                        control,
                        metrics,
                        stateBudget);
                if (!reserve.successful()) {
                    return shortageFailure(reserve.diagnostic(), stateBudget);
                }
                if (!reserve.value().objectiveProved()) {
                    return unprovedShortage(metrics, stateBudget, "reserve");
                }
                canonical = reserve.value().model();
                fixedReserves.put(key, requiredInputs(canonical).getOrDefault(key, BigInteger.ZERO));
            }
            return solution(
                    canonical,
                    metrics,
                    TrinityPlanQuality.PROVED_OPTIMAL,
                    stateBudget.used());
        }
    }

    private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> unprovedShortage(
                                                                                            SolverMetrics metrics,
                                                                                            TrinityCycleSolveBudget stateBudget,
                                                                                            String objective) {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                "gui.data_energistics.trinity_planning.mip.timeout",
                Map.of(
                        "objective", objective,
                        "passes", Integer.toString(metrics.passes),
                        "states", Integer.toString(stateBudget.used())));
    }

    private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> shortageFailure(
                                                                                           TrinityPlanningDiagnostic diagnostic,
                                                                                           TrinityCycleSolveBudget stateBudget) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>(diagnostic.metadata());
        metadata.put("limit", Integer.toString(stateBudget.limit()));
        metadata.put("states", Integer.toString(stateBudget.used()));
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                diagnostic.code(),
                diagnostic.message(),
                metadata,
                diagnostic.detail()));
    }

    private LinkedHashSet<AEKey> diagnosticReserveKeys(TrinityCycleFeasibilityRequest request) {
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>(request.internalKeys());
        keys.addAll(this.objectiveBounds.externalReserveKeys(request));
        keys.removeAll(request.producibleInputs());
        return keys;
    }

    private static Map<AEKey, BigInteger> requiredInputs(SolvedModel solved) {
        LinkedHashMap<AEKey, BigInteger> required = new LinkedHashMap<>(solved.externalInputs());
        solved.modelSeed().forEach((key, amount) -> required.merge(key, amount, BigInteger::add));
        return required;
    }

    private TrinityAlgorithmResult<SolvedPass> optimize(
                                                        TrinityCycleFeasibilityRequest request,
                                                        ModelPass pass,
                                                        OrdinaryModelTemplate modelTemplate,
                                                        TrinityPlanningControl control,
                                                        SolverMetrics metrics) {
        return optimize(
                request,
                pass,
                modelTemplate,
                control,
                metrics,
                TrinityCycleSolveBudget.unbounded());
    }

    private TrinityAlgorithmResult<SolvedPass> optimize(
                                                        TrinityCycleFeasibilityRequest request,
                                                        ModelPass pass,
                                                        OrdinaryModelTemplate modelTemplate,
                                                        TrinityPlanningControl control,
                                                        SolverMetrics metrics,
                                                        TrinityCycleSolveBudget stateBudget) {
        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "gui.data_energistics.trinity_planning.diagnostic.cancelled",
                    Map.of("passes", Integer.toString(metrics.passes)));
        }
        if (control.deadlineExceeded()) {
            return timeout(metrics, "before_model");
        }
        if (!stateBudget.tryConsume()) {
            return failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    "gui.data_energistics.trinity_planning.mip.schedule_search_limit",
                    Map.of(
                            "limit", Integer.toString(stateBudget.limit()),
                            "states", Integer.toString(stateBudget.used())));
        }
        ModelData data = modelTemplate.forPass(request, pass);
        configureDeadline(data.model(), control);
        long started = System.nanoTime();
        Optimisation.Result result = data.model().minimise();
        long elapsedNanos = Math.max(0L, System.nanoTime() - started);
        metrics.addPass(elapsedNanos);
        control.recordSolverPass(elapsedNanos);
        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "gui.data_energistics.trinity_planning.diagnostic.cancelled",
                    Map.of("passes", Integer.toString(metrics.passes)));
        }
        boolean objectiveProved = result.getState().isOptimal();
        if (!objectiveProved && !result.getState().isFeasible()) {
            if (control.deadlineExceeded()) {
                return timeout(metrics, result.getState().name());
            }
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                    "gui.data_energistics.trinity_planning.diagnostic.no_integer_solution",
                    Map.of("state", result.getState().name()));
        }
        ArrayList<BigDecimal> rawValues = new ArrayList<>(data.variables().size());
        for (Variable variable : data.variables()) {
            rawValues.add(result.get(data.model().indexOf(variable)));
        }
        TrinityAlgorithmResult<List<BigInteger>> verified = this.integerVerifier.verify(
                rawValues,
                data.model().options.integer().getIntegralityTolerance());
        if (!verified.successful()) {
            return TrinityAlgorithmResult.failure(verified.diagnostic());
        }
        if (verified.value().stream().anyMatch(value -> value.signum() < 0)) {
            return inexact("variable_lower", "negative");
        }
        SolvedModel solved = data.decode(verified.value());
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verifyExact(request, pass, solved);
        return exact.successful() ? TrinityAlgorithmResult.success(new SolvedPass(solved, objectiveProved)) :
                TrinityAlgorithmResult.failure(exact.diagnostic());
    }

    private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solution(
                                                                                    SolvedModel solved,
                                                                                    SolverMetrics metrics,
                                                                                    TrinityPlanQuality quality) {
        return solution(solved, metrics, quality, 0);
    }

    private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solution(
                                                                                    SolvedModel solved,
                                                                                    SolverMetrics metrics,
                                                                                    TrinityPlanQuality quality,
                                                                                    int diagnosticStates) {
        return TrinityAlgorithmResult.success(new TrinityCycleFeasibilitySolution(
                solved.firings(),
                solved.modelSeed(),
                solved.externalInputs(),
                metrics.passes,
                metrics.nanos,
                false,
                quality,
                solved.actualInputs(),
                solved.missingInputs(),
                diagnosticStates));
    }

    private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> recoverIncumbent(
                                                                                            SolvedModel incumbent,
                                                                                            SolverMetrics metrics,
                                                                                            TrinityPlanningDiagnostic diagnostic) {
        TrinityPlanningDiagnosticCode code = diagnostic.code();
        return code == TrinityPlanningDiagnosticCode.MIP_TIMEOUT ||
                code == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT ?
                        solution(incumbent, metrics, TrinityPlanQuality.VERIFIED_FEASIBLE) :
                        TrinityAlgorithmResult.failure(diagnostic);
    }

    private static void configureDeadline(ExpressionsBasedModel model, TrinityPlanningControl control) {
        if (!control.deadlineConfigured()) {
            return;
        }
        long remainingNanos = control.remainingNanos();
        long remainingMillis = Math.max(
                1L,
                TimeUnit.NANOSECONDS.toMillis(remainingNanos) +
                        (remainingNanos % 1_000_000L == 0L ? 0L : 1L));
        model.options.time_abort = remainingMillis;
        model.options.time_suffice = remainingMillis;
    }

    private TrinityAlgorithmResult<Map<AEKey, BigInteger>> verifyExact(
                                                                       TrinityCycleFeasibilityRequest request,
                                                                       ModelPass pass,
                                                                       SolvedModel solved) {
        LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>(solved.externalInputs());
        solved.modelSeed().forEach((key, amount) -> initialInputs.merge(key, amount, BigInteger::add));
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = this.conservationVerifier.verify(
                request.variants(),
                solved.firings(),
                initialInputs,
                request.shortageDiagnostic() ? Map.of() : this.objectiveBounds.finiteInputUpperBounds(request),
                request.demand().finalBalanceLowerBounds(),
                request.demand().requiredNetChangeLowerBounds());
        if (!exact.successful()) {
            return exact;
        }
        if (request.shortageDiagnostic()) {
            TrinityAlgorithmResult<Map<AEKey, BigInteger>> shortage = verifyShortageInputs(
                    request,
                    solved,
                    initialInputs);
            if (!shortage.successful()) {
                return shortage;
            }
        } else if (!solved.actualInputs().isEmpty() || !solved.missingInputs().isEmpty()) {
            return inexact("diagnostic_input", "executable_model");
        }
        BigInteger externalTotal = total(solved.externalInputs());
        BigInteger seedTotal = total(solved.modelSeed());
        BigInteger firingTotal = total(solved.firings());
        BigInteger missingTotal = total(solved.missingInputs());
        if (request.fixedExternalTotal().filter(fixed -> !fixed.equals(externalTotal)).isPresent()) {
            return inexact("fixed_external", externalTotal.toString());
        }
        for (TrinityPatternVariant variant : request.variants()) {
            BigInteger count = solved.firings().getOrDefault(variant, BigInteger.ZERO);
            if (!request.firingBounds().get(variant).contains(count)) {
                return inexact("firing_domain", variant.patternIdentity().publicationEncoding());
            }
        }
        if (externalTotal.compareTo(this.objectiveBounds.minimumFirstExternalInput(request)) < 0 ||
                seedTotal.compareTo(this.objectiveBounds.minimumFirstInternalInput(request)
                        .max(request.seedLowerBound())) < 0 ||
                firingTotal.compareTo(request.firingLowerBound()) < 0) {
            return inexact("objective_lower", externalTotal + "/" + seedTotal + "/" + firingTotal);
        }
        return switch (pass) {
            case FeasibilityPass.INSTANCE, ExternalPass.INSTANCE, ShortageMissingPass.INSTANCE -> exact;
            case SeedPass(var fixedExternal, var seedLowerBound) -> !externalTotal.equals(fixedExternal) || seedTotal.compareTo(seedLowerBound) < 0 ?
                    inexact("seed_level", externalTotal + "/" + seedTotal) : exact;
            case FiringPass(var fixedExternal, var fixedSeed, var firingLowerBound) -> !externalTotal.equals(fixedExternal) || !seedTotal.equals(fixedSeed) ||
                    firingTotal.compareTo(firingLowerBound) < 0 ?
                            inexact("firing_level", externalTotal + "/" + seedTotal + "/" + firingTotal) : exact;
            case IdentityPass(var fixedExternal, var fixedSeed, var fixedFirings, var fixedCounts, var variant) -> !externalTotal.equals(fixedExternal) || !seedTotal.equals(fixedSeed) ||
                    !firingTotal.equals(fixedFirings) || fixedCounts.entrySet().stream()
                            .anyMatch(entry -> !solved.firings()
                                    .getOrDefault(entry.getKey(), BigInteger.ZERO)
                                    .equals(entry.getValue())) ?
                                            inexact("identity_level", variant.patternIdentity().publicationEncoding()) : exact;
            case ShortageExternalPass(var fixedMissing) -> !missingTotal.equals(fixedMissing) ?
                    inexact("shortage_external_level", missingTotal.toString()) : exact;
            case ShortageSeedPass(var fixedMissing, var fixedExternal, var seedLowerBound) -> !missingTotal.equals(fixedMissing) || !externalTotal.equals(fixedExternal) ||
                    seedTotal.compareTo(seedLowerBound) < 0 ?
                            inexact(
                                    "shortage_seed_level",
                                    missingTotal + "/" + externalTotal + "/" + seedTotal) :
                            exact;
            case ShortageFiringPass(var fixedMissing, var fixedExternal, var fixedSeed, var firingLowerBound) -> !missingTotal.equals(fixedMissing) || !externalTotal.equals(fixedExternal) ||
                    !seedTotal.equals(fixedSeed) || firingTotal.compareTo(firingLowerBound) < 0 ?
                            inexact(
                                    "shortage_firing_level",
                                    missingTotal + "/" + externalTotal + "/" + seedTotal + "/" + firingTotal) :
                            exact;
            case ShortageIdentityPass(var fixedMissing, var fixedExternal, var fixedSeed, var fixedFirings, var fixedCounts, var variant) -> !missingTotal.equals(fixedMissing) ||
                    !externalTotal.equals(fixedExternal) || !seedTotal.equals(fixedSeed) ||
                    !firingTotal.equals(fixedFirings) || fixedCounts.entrySet().stream()
                            .anyMatch(entry -> !solved.firings()
                                    .getOrDefault(entry.getKey(), BigInteger.ZERO)
                                    .equals(entry.getValue())) ?
                                            inexact(
                                                    "shortage_identity_level",
                                                    variant.patternIdentity().publicationEncoding()) :
                                            exact;
            case ShortageReservePass(var fixedMissing, var fixedExternal, var fixedSeed, var fixedFirings, var fixedCounts, var fixedReserves, var key) -> !missingTotal.equals(fixedMissing) ||
                    !externalTotal.equals(fixedExternal) || !seedTotal.equals(fixedSeed) ||
                    !firingTotal.equals(fixedFirings) || fixedCounts.entrySet().stream()
                            .anyMatch(entry -> !solved.firings()
                                    .getOrDefault(entry.getKey(), BigInteger.ZERO)
                                    .equals(entry.getValue())) ||
                    fixedReserves.entrySet().stream()
                            .anyMatch(entry -> !initialInputs
                                    .getOrDefault(entry.getKey(), BigInteger.ZERO)
                                    .equals(entry.getValue())) ?
                                            inexact("shortage_reserve_level", key.toString()) : exact;
        };
    }

    private TrinityAlgorithmResult<Map<AEKey, BigInteger>> verifyShortageInputs(
                                                                                TrinityCycleFeasibilityRequest request,
                                                                                SolvedModel solved,
                                                                                Map<AEKey, BigInteger> requiredInputs) {
        LinkedHashSet<AEKey> finiteKeys = new LinkedHashSet<>(request.internalKeys());
        finiteKeys.addAll(this.objectiveBounds.externalReserveKeys(request));
        finiteKeys.removeAll(request.producibleInputs());
        LinkedHashMap<AEKey, BigInteger> expectedActual = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> expectedMissing = new LinkedHashMap<>();
        for (AEKey key : finiteKeys) {
            BigInteger required = requiredInputs.getOrDefault(key, BigInteger.ZERO);
            if (required.compareTo(LONG_MAX) > 0) {
                return inexact("shortage_required_long", key.toString());
            }
            BigInteger available = request.available().getOrDefault(key, BigInteger.ZERO);
            BigInteger actual = required.min(available);
            BigInteger missing = required.subtract(actual);
            if (actual.signum() > 0) {
                expectedActual.put(key, actual);
            }
            if (missing.signum() > 0) {
                expectedMissing.put(key, missing);
            }
        }
        if (!expectedActual.equals(solved.actualInputs())) {
            return inexact("shortage_actual", solved.actualInputs().toString());
        }
        if (!expectedMissing.equals(solved.missingInputs())) {
            return inexact("shortage_missing", solved.missingInputs().toString());
        }
        return TrinityAlgorithmResult.success(Map.copyOf(requiredInputs));
    }

    private OrdinaryModelTemplate createModelTemplate(TrinityCycleFeasibilityRequest request) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        ArrayList<Variable> allVariables = new ArrayList<>();
        LinkedHashMap<TrinityPatternVariant, Variable> firingVariables = new LinkedHashMap<>();
        for (int index = 0; index < request.variants().size(); index++) {
            TrinityPatternVariant variant = request.variants().get(index);
            Variable variable = model.addVariable("firing_" + index)
                    .integer();
            firingVariables.put(variant, variable);
            allVariables.add(variable);
        }
        LinkedHashMap<AEKey, Variable> seedVariables = reserveVariables(
                model,
                allVariables,
                request.internalKeys(),
                "seed_");
        LinkedHashMap<AEKey, Variable> externalVariables = reserveVariables(
                model,
                allVariables,
                this.objectiveBounds.externalReserveKeys(request),
                "external_");
        LinkedHashMap<AEKey, Variable> actualVariables = new LinkedHashMap<>();
        LinkedHashMap<AEKey, Variable> missingVariables = new LinkedHashMap<>();
        if (request.shortageDiagnostic()) {
            LinkedHashSet<AEKey> finiteKeys = new LinkedHashSet<>(request.internalKeys());
            finiteKeys.addAll(this.objectiveBounds.externalReserveKeys(request));
            finiteKeys.removeAll(request.producibleInputs());
            actualVariables.putAll(reserveVariables(model, allVariables, finiteKeys, "actual_"));
            missingVariables.putAll(reserveVariables(model, allVariables, finiteKeys, "missing_"));
            int splitIndex = 0;
            for (AEKey key : finiteKeys) {
                Variable required = request.internalKeys().contains(key) ?
                        seedVariables.get(key) : externalVariables.get(key);
                model.addExpression("shortage_split_" + splitIndex++)
                        .set(required, BigInteger.ONE)
                        .set(actualVariables.get(key), BigInteger.ONE.negate())
                        .set(missingVariables.get(key), BigInteger.ONE.negate())
                        .level(BigInteger.ZERO);
            }
        }
        expression(model, "missing_total", missingVariables.values());
        addConservation(model, request, firingVariables, seedVariables, externalVariables);
        expression(model, "seed_total", seedVariables.values());
        expression(model, "external_total", externalVariables.values());
        expression(model, "firing_total", firingVariables.values());
        LinkedHashMap<TrinityPatternVariant, Integer> firingIndexes = new LinkedHashMap<>();
        firingVariables.forEach((variant, variable) -> firingIndexes.put(variant, model.indexOf(variable)));
        LinkedHashMap<AEKey, Integer> seedIndexes = new LinkedHashMap<>();
        seedVariables.forEach((key, variable) -> seedIndexes.put(key, model.indexOf(variable)));
        LinkedHashMap<AEKey, Integer> externalIndexes = new LinkedHashMap<>();
        externalVariables.forEach((key, variable) -> externalIndexes.put(key, model.indexOf(variable)));
        LinkedHashMap<AEKey, Integer> actualIndexes = new LinkedHashMap<>();
        actualVariables.forEach((key, variable) -> actualIndexes.put(key, model.indexOf(variable)));
        LinkedHashMap<AEKey, Integer> missingIndexes = new LinkedHashMap<>();
        missingVariables.forEach((key, variable) -> missingIndexes.put(key, model.indexOf(variable)));
        return new OrdinaryModelTemplate(
                model,
                allVariables.size(),
                Collections.unmodifiableMap(firingIndexes),
                Collections.unmodifiableMap(seedIndexes),
                Collections.unmodifiableMap(externalIndexes),
                Collections.unmodifiableMap(actualIndexes),
                Collections.unmodifiableMap(missingIndexes),
                this.objectiveBounds);
    }

    private static LinkedHashMap<AEKey, Variable> reserveVariables(
                                                                   ExpressionsBasedModel model,
                                                                   List<Variable> allVariables,
                                                                   Set<AEKey> keys,
                                                                   String prefix) {
        LinkedHashMap<AEKey, Variable> variables = new LinkedHashMap<>();
        int index = 0;
        for (AEKey key : keys) {
            Variable variable = model.addVariable(prefix + index++).integer();
            variables.put(key, variable);
            allVariables.add(variable);
        }
        return variables;
    }

    private static void addConservation(
                                        ExpressionsBasedModel model,
                                        TrinityCycleFeasibilityRequest request,
                                        Map<TrinityPatternVariant, Variable> firingVariables,
                                        Map<AEKey, Variable> seedVariables,
                                        Map<AEKey, Variable> externalVariables) {
        ObjectArrayList<AEKey> touchedKeys = new ObjectArrayList<>(request.coefficientTemplate().touchedKeys());
        ObjectOpenHashSet<AEKey> seenKeys = new ObjectOpenHashSet<>(touchedKeys);
        request.demand().finalBalanceLowerBounds().keySet().forEach(key -> addStableKey(key, seenKeys, touchedKeys));
        request.demand().requiredNetChangeLowerBounds().keySet().forEach(
                key -> addStableKey(key, seenKeys, touchedKeys));
        int conservationIndex = 0;
        for (AEKey key : touchedKeys) {
            Expression conservation = model.addExpression("conservation_" + conservationIndex++);
            setNetCoefficients(conservation, request, firingVariables, key);
            Variable reserve = request.internalKeys().contains(key) ?
                    seedVariables.get(key) : externalVariables.get(key);
            if (reserve != null) {
                conservation.set(reserve, BigInteger.ONE);
            }
            conservation.lower(request.demand().finalBalanceLowerBounds().getOrDefault(key, BigInteger.ZERO));
        }
        int netIndex = 0;
        for (Map.Entry<AEKey, BigInteger> bound : request.demand().requiredNetChangeLowerBounds().entrySet()) {
            Expression net = model.addExpression("required_net_" + netIndex++);
            setNetCoefficients(net, request, firingVariables, bound.getKey());
            net.lower(bound.getValue());
        }
        int settlementIndex = 0;
        boolean exportsInternalKey = request.internalKeys().stream()
                .anyMatch(request.demand().requiredNetChangeLowerBounds()::containsKey);
        for (AEKey key : request.internalKeys()) {
            Expression settlement = model.addExpression("settled_internal_" + settlementIndex++);
            setNetCoefficients(settlement, request, firingVariables, key);
            BigInteger requestedOutput = request.demand().requiredNetChangeLowerBounds().get(key);
            if (requestedOutput != null) {
                settlement.lower(requestedOutput);
            } else if (exportsInternalKey) {
                settlement.level(BigInteger.ZERO);
            } else {
                settlement.lower(BigInteger.ZERO);
            }
        }
    }

    private static void setNetCoefficients(
                                           Expression expression,
                                           TrinityCycleFeasibilityRequest request,
                                           Map<TrinityPatternVariant, Variable> firingVariables,
                                           AEKey key) {
        for (Coefficient coefficient : request.coefficientTemplate().coefficients(key)) {
            expression.set(
                    firingVariables.get(request.variants().get(coefficient.variantIndex())),
                    coefficient.value());
        }
    }

    private static void addStableKey(AEKey key, Set<AEKey> seen, List<AEKey> destination) {
        if (seen.add(key)) {
            destination.add(key);
        }
    }

    private static void setIfNonZero(Expression expression, Variable variable, BigInteger coefficient) {
        if (coefficient.signum() != 0) {
            expression.set(variable, coefficient);
        }
    }

    private static void expression(
                                   ExpressionsBasedModel model,
                                   String name,
                                   Iterable<Variable> variables) {
        Expression expression = model.addExpression(name);
        for (Variable variable : variables) {
            expression.set(variable, BigInteger.ONE);
        }
    }

    private static BigInteger total(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static <T> TrinityAlgorithmResult<T> timeout(SolverMetrics metrics, String state) {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                "gui.data_energistics.trinity_planning.mip.timeout",
                Map.of("passes", Integer.toString(metrics.passes), "state", state));
    }

    private static <T> TrinityAlgorithmResult<T> inexact(String constraint, String value) {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                "gui.data_energistics.trinity_planning.diagnostic.inexact_result",
                Map.of("constraint", constraint, "value", value));
    }

    private static <T> TrinityAlgorithmResult<T> failure(
                                                         TrinityPlanningDiagnosticCode code,
                                                         String detail,
                                                         Map<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(code, Component.translatable(detail), metadata));
    }

    private sealed interface ModelPass permits FeasibilityPass, ExternalPass, SeedPass, FiringPass, IdentityPass,
                                       ShortageMissingPass, ShortageExternalPass, ShortageSeedPass, ShortageFiringPass, ShortageIdentityPass,
                                       ShortageReservePass {}

    private enum FeasibilityPass implements ModelPass {
        INSTANCE
    }

    private enum ExternalPass implements ModelPass {
        INSTANCE
    }

    private record SeedPass(BigInteger fixedExternal, BigInteger seedLowerBound) implements ModelPass {}

    private record FiringPass(
                              BigInteger fixedExternal,
                              BigInteger fixedSeed,
                              BigInteger firingLowerBound)
            implements ModelPass {}

    private record IdentityPass(
                                BigInteger fixedExternal,
                                BigInteger fixedSeed,
                                BigInteger fixedFirings,
                                Map<TrinityPatternVariant, BigInteger> fixedCounts,
                                TrinityPatternVariant variant)
            implements ModelPass {

        private IdentityPass {
            fixedCounts = Collections.unmodifiableMap(new LinkedHashMap<>(fixedCounts));
        }
    }

    private enum ShortageMissingPass implements ModelPass {
        INSTANCE
    }

    private record ShortageExternalPass(BigInteger fixedMissing) implements ModelPass {}

    private record ShortageSeedPass(
                                    BigInteger fixedMissing,
                                    BigInteger fixedExternal,
                                    BigInteger seedLowerBound)
            implements ModelPass {}

    private record ShortageFiringPass(
                                      BigInteger fixedMissing,
                                      BigInteger fixedExternal,
                                      BigInteger fixedSeed,
                                      BigInteger firingLowerBound)
            implements ModelPass {}

    private record ShortageIdentityPass(
                                        BigInteger fixedMissing,
                                        BigInteger fixedExternal,
                                        BigInteger fixedSeed,
                                        BigInteger fixedFirings,
                                        Map<TrinityPatternVariant, BigInteger> fixedCounts,
                                        TrinityPatternVariant variant)
            implements ModelPass {

        private ShortageIdentityPass {
            fixedCounts = Collections.unmodifiableMap(new LinkedHashMap<>(fixedCounts));
        }
    }

    private record ShortageReservePass(
                                       BigInteger fixedMissing,
                                       BigInteger fixedExternal,
                                       BigInteger fixedSeed,
                                       BigInteger fixedFirings,
                                       Map<TrinityPatternVariant, BigInteger> fixedCounts,
                                       Map<AEKey, BigInteger> fixedReserves,
                                       AEKey key)
            implements ModelPass {

        private ShortageReservePass {
            fixedCounts = Collections.unmodifiableMap(new LinkedHashMap<>(fixedCounts));
            fixedReserves = Collections.unmodifiableMap(new LinkedHashMap<>(fixedReserves));
        }
    }

    /**
     * Request-private structural template copied for each child objective pass. The base retains only stable variables
     * and sparse conservation coefficients; every mutable bound and objective is reapplied to the copy.
     */
    private record OrdinaryModelTemplate(
                                         ExpressionsBasedModel baseModel,
                                         int variableCount,
                                         Map<TrinityPatternVariant, Integer> firingIndexes,
                                         Map<AEKey, Integer> seedIndexes,
                                         Map<AEKey, Integer> externalIndexes,
                                         Map<AEKey, Integer> actualIndexes,
                                         Map<AEKey, Integer> missingIndexes,
                                         TrinityCycleObjectiveBounds objectiveBounds) {

        private OrdinaryModelTemplate {
            firingIndexes = Collections.unmodifiableMap(new LinkedHashMap<>(firingIndexes));
            seedIndexes = Collections.unmodifiableMap(new LinkedHashMap<>(seedIndexes));
            externalIndexes = Collections.unmodifiableMap(new LinkedHashMap<>(externalIndexes));
            actualIndexes = Collections.unmodifiableMap(new LinkedHashMap<>(actualIndexes));
            missingIndexes = Collections.unmodifiableMap(new LinkedHashMap<>(missingIndexes));
        }

        private ModelData forPass(TrinityCycleFeasibilityRequest request, ModelPass pass) {
            ExpressionsBasedModel model = this.baseModel.copy();
            ArrayList<Variable> variables = new ArrayList<>(this.variableCount);
            for (int index = 0; index < this.variableCount; index++) {
                variables.add(model.getVariable(index));
            }
            LinkedHashMap<TrinityPatternVariant, Variable> firingVariables = new LinkedHashMap<>();
            this.firingIndexes.forEach((variant, index) -> firingVariables.put(variant, model.getVariable(index)));
            LinkedHashMap<AEKey, Variable> seedVariables = new LinkedHashMap<>();
            this.seedIndexes.forEach((key, index) -> seedVariables.put(key, model.getVariable(index)));
            LinkedHashMap<AEKey, Variable> externalVariables = new LinkedHashMap<>();
            this.externalIndexes.forEach((key, index) -> externalVariables.put(key, model.getVariable(index)));
            LinkedHashMap<AEKey, Variable> actualVariables = new LinkedHashMap<>();
            this.actualIndexes.forEach((key, index) -> actualVariables.put(key, model.getVariable(index)));
            LinkedHashMap<AEKey, Variable> missingVariables = new LinkedHashMap<>();
            this.missingIndexes.forEach((key, index) -> missingVariables.put(key, model.getVariable(index)));

            BigInteger logicalUpper = request.ordinaryLogicalUpperBound().orElseThrow(() -> new IllegalArgumentException("A signed Trinity system cannot use the ordinary exact model"));
            firingVariables.forEach((variant, variable) -> {
                TrinityFiringBounds bounds = request.firingBounds().get(variant);
                variable.lower(bounds.lowerInclusive());
                variable.upper(bounds.upperInclusive().min(logicalUpper));
            });
            seedVariables.forEach((key, variable) -> {
                variable.lower(BigInteger.ZERO);
                variable.upper(this.objectiveBounds.reserveUpperBound(request, key, logicalUpper));
            });
            externalVariables.forEach((key, variable) -> {
                variable.lower(BigInteger.ZERO);
                variable.upper(this.objectiveBounds.reserveUpperBound(request, key, logicalUpper));
            });
            actualVariables.forEach((key, variable) -> {
                BigInteger requiredUpper = this.objectiveBounds.reserveUpperBound(request, key, logicalUpper);
                variable.lower(BigInteger.ZERO);
                variable.upper(request.available().getOrDefault(key, BigInteger.ZERO).min(requiredUpper));
            });
            missingVariables.forEach((key, variable) -> {
                variable.lower(BigInteger.ZERO);
                variable.upper(this.objectiveBounds.reserveUpperBound(request, key, logicalUpper));
            });

            Expression seedTotal = model.getExpression("seed_total");
            Expression externalTotal = model.getExpression("external_total");
            Expression firingTotal = model.getExpression("firing_total");
            Expression missingTotal = Objects.requireNonNull(
                    model.getExpression("missing_total"),
                    "A Trinity ordinary model requires its missing-input total expression");
            seedTotal.lower(this.objectiveBounds.minimumFirstInternalInput(request)
                    .max(request.seedLowerBound()));
            externalTotal.lower(this.objectiveBounds.minimumFirstExternalInput(request));
            firingTotal.lower(request.firingLowerBound());
            request.fixedExternalTotal().ifPresent(externalTotal::level);
            switch (pass) {
                case FeasibilityPass.INSTANCE -> {
                    // Zero objective: obtain any integer witness and verify it exactly after the solve.
                }
                case ExternalPass.INSTANCE -> externalTotal.weight(BigDecimal.ONE);
                case SeedPass(var fixedExternal, var seedLowerBound) -> {
                    externalTotal.level(fixedExternal);
                    seedTotal.lower(this.objectiveBounds.minimumFirstInternalInput(request)
                            .max(request.seedLowerBound())
                            .max(seedLowerBound));
                    seedTotal.weight(BigDecimal.ONE);
                }
                case FiringPass(var fixedExternal, var fixedSeed, var firingLowerBound) -> {
                    externalTotal.level(fixedExternal);
                    seedTotal.level(fixedSeed);
                    firingTotal.lower(firingLowerBound.max(
                            request.firingLowerBound()).max(
                                    this.objectiveBounds.conservationFiringLowerBound(
                                            request,
                                            fixedExternal,
                                            fixedSeed)));
                    firingTotal.weight(BigDecimal.ONE);
                }
                case IdentityPass(var fixedExternal, var fixedSeed, var fixedFirings, var fixedCounts, var variant) -> {
                    externalTotal.level(fixedExternal);
                    seedTotal.level(fixedSeed);
                    firingTotal.level(fixedFirings);
                    fixedCounts.forEach((fixedVariant, count) -> model
                            .addExpression("fixed_firing_" + request.variants().indexOf(fixedVariant))
                            .set(firingVariables.get(fixedVariant), BigInteger.ONE)
                            .level(count));
                    BigInteger identityUpper = this.objectiveBounds.identityObjectiveUpperBound(
                            request,
                            fixedExternal,
                            fixedSeed,
                            fixedFirings,
                            fixedCounts,
                            variant)
                            .min(request.firingBounds().get(variant).upperInclusive());
                    model.addExpression("identity_objective")
                            .set(firingVariables.get(variant), BigInteger.ONE)
                            .upper(identityUpper)
                            .weight(BigDecimal.ONE.negate());
                }
                case ShortageMissingPass.INSTANCE -> missingTotal.weight(BigDecimal.ONE);
                case ShortageExternalPass(var fixedMissing) -> {
                    missingTotal.level(fixedMissing);
                    externalTotal.weight(BigDecimal.ONE);
                }
                case ShortageSeedPass(var fixedMissing, var fixedExternal, var seedLowerBound) -> {
                    missingTotal.level(fixedMissing);
                    externalTotal.level(fixedExternal);
                    seedTotal.lower(this.objectiveBounds.minimumFirstInternalInput(request)
                            .max(request.seedLowerBound())
                            .max(seedLowerBound));
                    seedTotal.weight(BigDecimal.ONE);
                }
                case ShortageFiringPass(var fixedMissing, var fixedExternal, var fixedSeed, var firingLowerBound) -> {
                    missingTotal.level(fixedMissing);
                    externalTotal.level(fixedExternal);
                    seedTotal.level(fixedSeed);
                    firingTotal.lower(firingLowerBound.max(request.firingLowerBound()).max(
                            this.objectiveBounds.conservationFiringLowerBound(
                                    request,
                                    fixedExternal,
                                    fixedSeed)));
                    firingTotal.weight(BigDecimal.ONE);
                }
                case ShortageIdentityPass(var fixedMissing, var fixedExternal, var fixedSeed, var fixedFirings, var fixedCounts, var variant) -> {
                    missingTotal.level(fixedMissing);
                    externalTotal.level(fixedExternal);
                    seedTotal.level(fixedSeed);
                    firingTotal.level(fixedFirings);
                    fixedCounts.forEach((fixedVariant, count) -> model
                            .addExpression("fixed_shortage_firing_" + request.variants().indexOf(fixedVariant))
                            .set(firingVariables.get(fixedVariant), BigInteger.ONE)
                            .level(count));
                    BigInteger identityUpper = this.objectiveBounds.identityObjectiveUpperBound(
                            request,
                            fixedExternal,
                            fixedSeed,
                            fixedFirings,
                            fixedCounts,
                            variant)
                            .min(request.firingBounds().get(variant).upperInclusive());
                    model.addExpression("shortage_identity_objective")
                            .set(firingVariables.get(variant), BigInteger.ONE)
                            .upper(identityUpper)
                            .weight(BigDecimal.ONE.negate());
                }
                case ShortageReservePass(var fixedMissing, var fixedExternal, var fixedSeed, var fixedFirings, var fixedCounts, var fixedReserves, var key) -> {
                    missingTotal.level(fixedMissing);
                    externalTotal.level(fixedExternal);
                    seedTotal.level(fixedSeed);
                    firingTotal.level(fixedFirings);
                    fixedCounts.forEach((fixedVariant, count) -> model
                            .addExpression("fixed_reserve_firing_" + request.variants().indexOf(fixedVariant))
                            .set(firingVariables.get(fixedVariant), BigInteger.ONE)
                            .level(count));
                    fixedReserves.forEach((fixedKey, amount) -> requiredVariable(
                            fixedKey,
                            seedVariables,
                            externalVariables).level(amount));
                    requiredVariable(key, seedVariables, externalVariables).weight(BigDecimal.ONE);
                }
            }
            return new ModelData(
                    model,
                    List.copyOf(variables),
                    Collections.unmodifiableMap(firingVariables),
                    Collections.unmodifiableMap(seedVariables),
                    Collections.unmodifiableMap(externalVariables),
                    Collections.unmodifiableMap(actualVariables),
                    Collections.unmodifiableMap(missingVariables));
        }

        private static Variable requiredVariable(
                                                 AEKey key,
                                                 Map<AEKey, Variable> seedVariables,
                                                 Map<AEKey, Variable> externalVariables) {
            Variable variable = seedVariables.get(key);
            if (variable == null) {
                variable = externalVariables.get(key);
            }
            if (variable == null) {
                throw new IllegalArgumentException("A Trinity shortage reserve key has no model axis");
            }
            return variable;
        }
    }

    private record ModelData(
                             ExpressionsBasedModel model,
                             List<Variable> variables,
                             Map<TrinityPatternVariant, Variable> firingVariables,
                             Map<AEKey, Variable> seedVariables,
                             Map<AEKey, Variable> externalVariables,
                             Map<AEKey, Variable> actualVariables,
                             Map<AEKey, Variable> missingVariables) {

        private SolvedModel decode(List<BigInteger> values) {
            LinkedHashMap<Variable, BigInteger> byVariable = new LinkedHashMap<>();
            for (int index = 0; index < variables.size(); index++) {
                byVariable.put(variables.get(index), values.get(index));
            }
            LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
            firingVariables.forEach((variant, variable) -> putPositive(firings, variant, byVariable.get(variable)));
            return new SolvedModel(
                    Collections.unmodifiableMap(firings),
                    positiveAmounts(seedVariables, byVariable),
                    positiveAmounts(externalVariables, byVariable),
                    positiveAmounts(actualVariables, byVariable),
                    positiveAmounts(missingVariables, byVariable));
        }

        private static Map<AEKey, BigInteger> positiveAmounts(
                                                              Map<AEKey, Variable> variables,
                                                              Map<Variable, BigInteger> values) {
            LinkedHashMap<AEKey, BigInteger> positive = new LinkedHashMap<>();
            variables.forEach((key, variable) -> putPositive(positive, key, values.get(variable)));
            return Collections.unmodifiableMap(positive);
        }

        private static <K> void putPositive(Map<K, BigInteger> target, K key, BigInteger value) {
            if (value.signum() > 0) {
                target.put(key, value);
            }
        }
    }

    private record SolvedModel(
                               Map<TrinityPatternVariant, BigInteger> firings,
                               Map<AEKey, BigInteger> modelSeed,
                               Map<AEKey, BigInteger> externalInputs,
                               Map<AEKey, BigInteger> actualInputs,
                               Map<AEKey, BigInteger> missingInputs) {}

    private record SolvedPass(SolvedModel model, boolean objectiveProved) {}

    private static final class SolverMetrics {

        private int passes;
        private long nanos;

        private void addPass(long elapsedNanos) {
            this.passes = Math.addExact(this.passes, 1);
            this.nanos = Math.addExact(this.nanos, elapsedNanos);
        }
    }
}
