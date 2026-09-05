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

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ordinary exact-window ojAlgo model retaining the established sequential objective semantics.
 */
final class TrinityOrdinaryCycleFeasibilityModel implements TrinityCycleFeasibilityModel {

    // ojAlgo NodeKey stores integer domains in int[] and reserves MAX_VALUE as an unbounded sentinel.
    private static final BigDecimal INTEGER_BRANCH_LIMIT = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final long CORRECTION_CALL_MILLIS = 100L;

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
            Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> fixedFirings = new Object2ObjectLinkedOpenHashMap<>();
            SolvedModel canonical = incumbent;
            for (TrinityPatternVariant variant : request.variants()) {
                TrinityFiringBounds bounds = request.firingBounds().get(variant);
                if (bounds.fixed()) {
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
                BigInteger identityUpper = bounds.upperOr(this.objectiveBounds.identityObjectiveUpperBound(
                        request,
                        optimalExternal,
                        optimalSeed,
                        optimalFirings,
                        fixedFirings,
                        variant));
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
     * Finds an exactly verified conservation candidate with virtual reserve after executable search stops.
     * This is not an optimality or global infeasibility proof; the caller must verify its schedule and actual inputs.
     */
    private TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solveShortage(
                                                                                  TrinityCycleFeasibilityRequest request, TrinityPlanningControl control,
                                                                                  OrdinaryModelTemplate modelTemplate, SolverMetrics metrics, TrinityCycleSolveBudget stateBudget) {
        TrinityAlgorithmResult<SolvedPass> feasible = optimize(
                request, FeasibilityPass.INSTANCE, modelTemplate, control, metrics, stateBudget);
        if (!feasible.successful()) return shortageFailure(feasible.diagnostic(), stateBudget, metrics);
        SolvedModel solved = feasible.value().model();
        TrinityShortageInputAllocation allocation = TrinityShortageInputAllocation.from(
                request, solved.modelSeed(), solved.externalInputs());
        return TrinityAlgorithmResult.success(new TrinityCycleFeasibilitySolution(
                solved.firings(), solved.modelSeed(), solved.externalInputs(), metrics.passes, metrics.nanos,
                false, TrinityPlanQuality.VERIFIED_FEASIBLE,
                allocation.actualInputs(), allocation.missingInputs(), stateBudget.used()));
    }

    private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> shortageFailure(
                                                                                           TrinityPlanningDiagnostic diagnostic,
                                                                                           TrinityCycleSolveBudget stateBudget,
                                                                                           SolverMetrics metrics) {
        Object2ObjectLinkedOpenHashMap<String, String> metadata = new Object2ObjectLinkedOpenHashMap<>(diagnostic.metadata());
        metadata.put("limit", Integer.toString(stateBudget.limit()));
        metadata.put("states", Integer.toString(stateBudget.used()));
        metadata.put("passes", Integer.toString(metrics.passes));
        metadata.put("nanos", Long.toString(metrics.nanos));
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                diagnostic.code(),
                diagnostic.message(),
                metadata,
                diagnostic.detail()));
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
        ModelData data = modelTemplate.forPass(request, pass);
        boolean relaxed = data.model().getVariables().stream()
                .anyMatch(variable -> variable.getUpperLimit().compareTo(INTEGER_BRANCH_LIMIT) >= 0);
        if (relaxed) {
            data.model().relax(true);
        }
        if (!stateBudget.tryConsume()) {
            return failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    "gui.data_energistics.trinity_planning.mip.schedule_search_limit",
                    Map.of(
                            "limit", Integer.toString(stateBudget.limit()),
                            "states", Integer.toString(stateBudget.used())));
        }
        TrinityOjAlgoSolvePolicy.configure(data.model(), control, pass == FeasibilityPass.INSTANCE);
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
        boolean objectiveProved = !relaxed && result.getState().isOptimal();
        if (!objectiveProved && !result.getState().isFeasible()) {
            if (relaxed && !control.deadlineExceeded()) {
                return integerDomainLimit(result.getState().name());
            }
            if (control.deadlineExceeded() || result.getState() != Optimisation.State.INFEASIBLE) {
                return timeout(metrics, result.getState().name());
            }
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                    "gui.data_energistics.trinity_planning.diagnostic.no_integer_solution",
                    Map.of("state", result.getState().name()));
        }
        TrinityAlgorithmResult<List<BigInteger>> verified = integerCandidate(data, result, relaxed);
        if (!verified.successful()) {
            if (relaxed && pass == FeasibilityPass.INSTANCE) {
                return correctCandidate(request, pass, modelTemplate, result, control, metrics, stateBudget);
            }
            return TrinityAlgorithmResult.failure(verified.diagnostic());
        }
        if (verified.value().stream().anyMatch(value -> value.signum() < 0)) {
            return inexact("variable_lower", "negative");
        }
        SolvedModel solved = data.decode(verified.value());
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verifyExact(request, pass, solved);
        if (!exact.successful()) {
            if (relaxed && pass == FeasibilityPass.INSTANCE) {
                return correctCandidate(request, pass, modelTemplate, result, control, metrics, stateBudget);
            }
            return relaxed ? integerDomainLimit("candidate_verification") :
                    TrinityAlgorithmResult.failure(exact.diagnostic());
        }
        return TrinityAlgorithmResult.success(new SolvedPass(solved, objectiveProved));
    }

    private TrinityAlgorithmResult<SolvedPass> correctCandidate(
                                                                TrinityCycleFeasibilityRequest request, ModelPass pass,
                                                                OrdinaryModelTemplate modelTemplate, Optimisation.Result witness,
                                                                TrinityPlanningControl control, SolverMetrics metrics,
                                                                TrinityCycleSolveBudget stateBudget) {
        if (control.cancellationRequested()) {
            return failure(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "gui.data_energistics.trinity_planning.diagnostic.cancelled", Map.of());
        }
        if (control.deadlineExceeded()) return timeout(metrics, "before_correction");
        // Reuse the untouched template: the linear solver may have presolved its own model in place.
        ModelData data = modelTemplate.forPass(request, pass);
        TrinityIntegerCorrection correction = TrinityIntegerCorrection.translate(data.model(), witness);
        if (correction == null) return integerDomainLimit("correction_domain");
        if (!stateBudget.tryConsume()) {
            return failure(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    "gui.data_energistics.trinity_planning.mip.schedule_search_limit",
                    Map.of("limit", Integer.toString(stateBudget.limit()), "states", Integer.toString(stateBudget.used())));
        }
        TrinityOjAlgoSolvePolicy.configure(data.model(), control, true);
        data.model().options.time_abort = Math.min(data.model().options.time_abort, CORRECTION_CALL_MILLIS);
        long started = System.nanoTime();
        Optimisation.Result result = data.model().minimise();
        long elapsed = Math.max(0L, System.nanoTime() - started);
        metrics.addPass(elapsed);
        control.recordSolverPass(elapsed);
        if (control.cancellationRequested()) {
            return failure(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "gui.data_energistics.trinity_planning.diagnostic.cancelled", Map.of());
        }
        if (control.deadlineExceeded()) return timeout(metrics, "correction");
        if (!result.getState().isFeasible()) return integerDomainLimit("correction_" + result.getState().name());
        TrinityAlgorithmResult<List<BigInteger>> delta = integerCandidate(data, result, false);
        if (!delta.successful()) return integerDomainLimit("correction_integer");
        List<BigInteger> restored = correction.restore(delta.value());
        if (restored == null) return integerDomainLimit("correction_domain");
        SolvedModel solved = data.decode(restored);
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verifyExact(request, pass, solved);
        return exact.successful() ? TrinityAlgorithmResult.success(new SolvedPass(solved, false)) :
                integerDomainLimit("correction_verification");
    }

    private TrinityAlgorithmResult<List<BigInteger>> integerCandidate(ModelData data, Optimisation.Result result, boolean relaxed) {
        int count = data.template().variableCount();
        if (relaxed) {
            ObjectArrayList<BigInteger> candidate = new ObjectArrayList<>(count);
            for (int index = 0; index < count; index++) {
                // Rounding proposes a candidate only. Exact domains, balances and settlement below decide acceptance.
                BigDecimal value = result.get(index).setScale(0, RoundingMode.HALF_EVEN);
                Variable variable = data.model().getVariable(index);
                if (value.compareTo(variable.getLowerLimit()) < 0 || value.compareTo(variable.getUpperLimit()) > 0) {
                    return integerDomainLimit("variable_domain");
                }
                candidate.add(value.toBigIntegerExact());
            }
            return TrinityAlgorithmResult.success(candidate);
        }
        ObjectArrayList<BigDecimal> rawValues = new ObjectArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rawValues.add(result.get(index));
        }
        return this.integerVerifier.verify(rawValues, data.model().options.integer().getIntegralityTolerance());
    }

    private static <T> TrinityAlgorithmResult<T> integerDomainLimit(String reason) {
        return failure(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                "gui.data_energistics.trinity_planning.mip.radix_model_limit",
                Map.of("phase", "ordinary_integer_domain", "reason", reason));
    }

    private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solution(
                                                                                    SolvedModel solved, SolverMetrics metrics, TrinityPlanQuality quality) {
        return TrinityAlgorithmResult.success(new TrinityCycleFeasibilitySolution(
                solved.firings(), solved.modelSeed(), solved.externalInputs(),
                metrics.passes, metrics.nanos, false, quality, Map.of(), Map.of(), 0));
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

    private TrinityAlgorithmResult<Map<AEKey, BigInteger>> verifyExact(
                                                                       TrinityCycleFeasibilityRequest request,
                                                                       ModelPass pass,
                                                                       SolvedModel solved) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> initialInputs = new Object2ObjectLinkedOpenHashMap<>(solved.externalInputs());
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
        boolean exportsInternalKey = request.internalKeys().stream()
                .anyMatch(request.demand().requiredNetChangeLowerBounds()::containsKey);
        for (AEKey key : request.internalKeys()) {
            if (!request.demand().requiredNetChangeLowerBounds().containsKey(key)) {
                int sign = exact.value().getOrDefault(key, BigInteger.ZERO).signum();
                if (exportsInternalKey ? sign != 0 : sign < 0) {
                    return inexact("settled_internal", key.toString());
                }
            }
        }
        BigInteger externalTotal = total(solved.externalInputs());
        BigInteger seedTotal = total(solved.modelSeed());
        BigInteger firingTotal = total(solved.firings());
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
            case FeasibilityPass.INSTANCE, ExternalPass.INSTANCE -> exact;
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
        };
    }

    private OrdinaryModelTemplate createModelTemplate(TrinityCycleFeasibilityRequest request) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        ObjectArrayList<Variable> allVariables = new ObjectArrayList<>();
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, Variable> firingVariables = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < request.variants().size(); index++) {
            TrinityPatternVariant variant = request.variants().get(index);
            Variable variable = model.addVariable("firing_" + index)
                    .integer();
            firingVariables.put(variant, variable);
            allVariables.add(variable);
        }
        Object2ObjectLinkedOpenHashMap<AEKey, Variable> seedVariables = reserveVariables(
                model,
                allVariables,
                request.internalKeys(),
                "seed_");
        Object2ObjectLinkedOpenHashMap<AEKey, Variable> externalVariables = reserveVariables(
                model,
                allVariables,
                this.objectiveBounds.externalReserveKeys(request),
                "external_");
        addConservation(model, request, firingVariables, seedVariables, externalVariables);
        expression(model, "seed_total", seedVariables.values());
        expression(model, "external_total", externalVariables.values());
        expression(model, "firing_total", firingVariables.values());
        Object2IntMap<TrinityPatternVariant> firingIndexes = new Object2IntLinkedOpenHashMap<>();
        firingVariables.forEach((variant, variable) -> firingIndexes.put(variant, model.indexOf(variable)));
        Object2IntMap<AEKey> seedIndexes = new Object2IntLinkedOpenHashMap<>();
        seedVariables.forEach((key, variable) -> seedIndexes.put(key, model.indexOf(variable)));
        Object2IntMap<AEKey> externalIndexes = new Object2IntLinkedOpenHashMap<>();
        externalVariables.forEach((key, variable) -> externalIndexes.put(key, model.indexOf(variable)));
        return new OrdinaryModelTemplate(
                model,
                allVariables.size(),
                Object2IntMaps.unmodifiable(firingIndexes),
                Object2IntMaps.unmodifiable(seedIndexes),
                Object2IntMaps.unmodifiable(externalIndexes),
                this.objectiveBounds);
    }

    private static Object2ObjectLinkedOpenHashMap<AEKey, Variable> reserveVariables(
                                                                                    ExpressionsBasedModel model,
                                                                                    List<Variable> allVariables,
                                                                                    Set<AEKey> keys,
                                                                                    String prefix) {
        Object2ObjectLinkedOpenHashMap<AEKey, Variable> variables = new Object2ObjectLinkedOpenHashMap<>();
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

    private sealed interface ModelPass permits FeasibilityPass, ExternalPass, SeedPass, FiringPass, IdentityPass {}

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
            fixedCounts = Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(fixedCounts));
        }
    }

    private record OrdinaryModelTemplate(
                                         ExpressionsBasedModel baseModel,
                                         int variableCount,
                                         Object2IntMap<TrinityPatternVariant> firingIndexes,
                                         Object2IntMap<AEKey> seedIndexes,
                                         Object2IntMap<AEKey> externalIndexes, TrinityCycleObjectiveBounds objectiveBounds) {

        private ModelData forPass(TrinityCycleFeasibilityRequest request, ModelPass pass) {
            ExpressionsBasedModel model = this.baseModel.copy();
            BigInteger logicalUpper = request.ordinaryLogicalUpperBound().orElseThrow(() -> new IllegalArgumentException("A signed Trinity system cannot use the ordinary exact model"));
            Object2IntMaps.fastForEach(this.firingIndexes, entry -> {
                Variable variable = model.getVariable(entry.getIntValue());
                TrinityFiringBounds bounds = request.firingBounds().get(entry.getKey());
                variable.lower(bounds.lowerInclusive());
                variable.upper(bounds.upperOr(logicalUpper));
            });
            Object2IntMaps.fastForEach(this.seedIndexes, entry -> {
                Variable variable = model.getVariable(entry.getIntValue());
                variable.lower(BigInteger.ZERO);
                variable.upper(this.objectiveBounds.reserveUpperBound(request, entry.getKey(), logicalUpper));
            });
            Object2IntMaps.fastForEach(this.externalIndexes, entry -> {
                Variable variable = model.getVariable(entry.getIntValue());
                variable.lower(BigInteger.ZERO);
                variable.upper(this.objectiveBounds.reserveUpperBound(request, entry.getKey(), logicalUpper));
            });
            Expression seedTotal = model.getExpression("seed_total");
            Expression externalTotal = model.getExpression("external_total");
            Expression firingTotal = model.getExpression("firing_total");
            seedTotal.lower(this.objectiveBounds.minimumFirstInternalInput(request)
                    .max(request.seedLowerBound()));
            externalTotal.lower(this.objectiveBounds.minimumFirstExternalInput(request));
            firingTotal.lower(request.shortageDiagnostic() ? request.firingLowerBound().max(BigInteger.ONE) : request.firingLowerBound());
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
                            .set(model.getVariable(this.firingIndexes.getInt(fixedVariant)), BigInteger.ONE)
                            .level(count));
                    BigInteger identityUpper = request.firingBounds().get(variant).upperOr(
                            this.objectiveBounds.identityObjectiveUpperBound(
                                    request,
                                    fixedExternal,
                                    fixedSeed,
                                    fixedFirings,
                                    fixedCounts,
                                    variant));
                    model.addExpression("identity_objective")
                            .set(model.getVariable(this.firingIndexes.getInt(variant)), BigInteger.ONE)
                            .upper(identityUpper)
                            .weight(BigDecimal.ONE.negate());
                }
            }
            return new ModelData(model, this);
        }
    }

    private record ModelData(ExpressionsBasedModel model, OrdinaryModelTemplate template) {

        private SolvedModel decode(List<BigInteger> values) {
            Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> firings = new Object2ObjectLinkedOpenHashMap<>();
            Object2IntMaps.fastForEach(this.template.firingIndexes(), entry -> putPositive(firings, entry.getKey(), values.get(entry.getIntValue())));
            return new SolvedModel(Collections.unmodifiableMap(firings),
                    positiveAmounts(this.template.seedIndexes(), values),
                    positiveAmounts(this.template.externalIndexes(), values));
        }

        private static Map<AEKey, BigInteger> positiveAmounts(Object2IntMap<AEKey> indexes, List<BigInteger> values) {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> positive = new Object2ObjectLinkedOpenHashMap<>();
            Object2IntMaps.fastForEach(indexes, entry -> putPositive(positive, entry.getKey(), values.get(entry.getIntValue())));
            return Collections.unmodifiableMap(positive);
        }

        private static <K> void putPositive(Map<K, BigInteger> target, K key, BigInteger value) {
            if (value.signum() > 0) target.put(key, value);
        }
    }

    private record SolvedModel(
                               Map<TrinityPatternVariant, BigInteger> firings,
                               Map<AEKey, BigInteger> modelSeed,
                               Map<AEKey, BigInteger> externalInputs) {}

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
