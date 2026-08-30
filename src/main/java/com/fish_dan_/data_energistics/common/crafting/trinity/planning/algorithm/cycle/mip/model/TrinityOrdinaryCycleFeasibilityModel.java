package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Ordinary exact-window ojAlgo model retaining the established sequential objective semantics.
 */
final class TrinityOrdinaryCycleFeasibilityModel implements TrinityCycleFeasibilityModel {

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

    private TrinityAlgorithmResult<SolvedPass> optimize(
                                                        TrinityCycleFeasibilityRequest request,
                                                        ModelPass pass,
                                                        OrdinaryModelTemplate modelTemplate,
                                                        TrinityPlanningControl control,
                                                        SolverMetrics metrics) {
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
        configureDeadline(data.model(), control);
        long started = System.nanoTime();
        Optimisation.Result result = data.model().minimise();
        metrics.addPass(Math.max(0L, System.nanoTime() - started));
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
        return TrinityAlgorithmResult.success(new TrinityCycleFeasibilitySolution(
                solved.firings(),
                solved.modelSeed(),
                solved.externalInputs(),
                metrics.passes,
                metrics.nanos,
                false,
                quality));
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
                this.objectiveBounds.finiteInputUpperBounds(request),
                request.demand().finalBalanceLowerBounds(),
                request.demand().requiredNetChangeLowerBounds());
        if (!exact.successful()) {
            return exact;
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
            case FeasibilityPass.INSTANCE -> exact;
            case ExternalPass.INSTANCE -> exact;
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
        return new OrdinaryModelTemplate(
                model,
                allVariables.size(),
                Collections.unmodifiableMap(firingIndexes),
                Collections.unmodifiableMap(seedIndexes),
                Collections.unmodifiableMap(externalIndexes),
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
        LinkedHashSet<AEKey> touchedKeys = new LinkedHashSet<>();
        request.variants().forEach(variant -> {
            touchedKeys.addAll(variant.inputs().keySet());
            touchedKeys.addAll(variant.outputs().keySet());
        });
        touchedKeys.addAll(request.demand().finalBalanceLowerBounds().keySet());
        touchedKeys.addAll(request.demand().requiredNetChangeLowerBounds().keySet());
        int conservationIndex = 0;
        for (AEKey key : touchedKeys) {
            Expression conservation = model.addExpression("conservation_" + conservationIndex++);
            firingVariables.forEach((variant, variable) -> setIfNonZero(
                    conservation,
                    variable,
                    variant.netChange().getOrDefault(key, BigInteger.ZERO)));
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
            firingVariables.forEach((variant, variable) -> setIfNonZero(
                    net,
                    variable,
                    variant.netChange().getOrDefault(bound.getKey(), BigInteger.ZERO)));
            net.lower(bound.getValue());
        }
        int settlementIndex = 0;
        boolean exportsInternalKey = request.internalKeys().stream()
                .anyMatch(request.demand().requiredNetChangeLowerBounds()::containsKey);
        for (AEKey key : request.internalKeys()) {
            Expression settlement = model.addExpression("settled_internal_" + settlementIndex++);
            firingVariables.forEach((variant, variable) -> setIfNonZero(
                    settlement,
                    variable,
                    variant.netChange().getOrDefault(key, BigInteger.ZERO)));
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
            fixedCounts = Collections.unmodifiableMap(new LinkedHashMap<>(fixedCounts));
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
                                         TrinityCycleObjectiveBounds objectiveBounds) {

        private OrdinaryModelTemplate {
            firingIndexes = Collections.unmodifiableMap(new LinkedHashMap<>(firingIndexes));
            seedIndexes = Collections.unmodifiableMap(new LinkedHashMap<>(seedIndexes));
            externalIndexes = Collections.unmodifiableMap(new LinkedHashMap<>(externalIndexes));
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

            BigInteger logicalUpper = request.ordinaryLogicalUpperBound().orElseThrow(() -> new IllegalArgumentException("A signed Trinity system cannot use the ordinary exact model"));
            firingVariables.forEach((variant, variable) -> {
                TrinityFiringBounds bounds = request.firingBounds().get(variant);
                variable.lower(bounds.lowerInclusive());
                variable.upper(bounds.upperInclusive().min(logicalUpper));
            });
            seedVariables.forEach((key, variable) -> {
                variable.lower(BigInteger.ZERO);
                variable.upper(reserveUpperBound(request, key, logicalUpper));
            });
            externalVariables.forEach((key, variable) -> {
                variable.lower(BigInteger.ZERO);
                variable.upper(reserveUpperBound(request, key, logicalUpper));
            });

            Expression seedTotal = model.getExpression("seed_total");
            Expression externalTotal = model.getExpression("external_total");
            Expression firingTotal = model.getExpression("firing_total");
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
            }
            return new ModelData(
                    model,
                    List.copyOf(variables),
                    Collections.unmodifiableMap(firingVariables),
                    Collections.unmodifiableMap(seedVariables),
                    Collections.unmodifiableMap(externalVariables));
        }

        private static BigInteger reserveUpperBound(
                                                    TrinityCycleFeasibilityRequest request,
                                                    AEKey key,
                                                    BigInteger logicalUpper) {
            return request.producibleInputs().contains(key) ?
                    logicalUpper : request.available().getOrDefault(key, BigInteger.ZERO).min(logicalUpper);
        }
    }

    private record ModelData(
                             ExpressionsBasedModel model,
                             List<Variable> variables,
                             Map<TrinityPatternVariant, Variable> firingVariables,
                             Map<AEKey, Variable> seedVariables,
                             Map<AEKey, Variable> externalVariables) {

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
                    positiveAmounts(externalVariables, byVariable));
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
