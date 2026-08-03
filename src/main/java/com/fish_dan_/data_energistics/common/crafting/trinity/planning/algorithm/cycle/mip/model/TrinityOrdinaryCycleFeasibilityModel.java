package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

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
                                                                         TrinityPlanningControl control) {
        SolverMetrics metrics = new SolverMetrics();
        TrinityAlgorithmResult<SolvedModel> external = optimize(
                request,
                ExternalPass.INSTANCE,
                control,
                metrics);
        if (!external.successful()) {
            return TrinityAlgorithmResult.failure(external.diagnostic());
        }
        BigInteger optimalExternal = total(external.value().externalInputs());
        BigInteger seedLower = request.seedLowerBound();
        BigInteger firingLower = request.firingLowerBound();
        while (true) {
            TrinityAlgorithmResult<SolvedModel> seed = optimize(
                    request,
                    new SeedPass(optimalExternal, seedLower),
                    control,
                    metrics);
            if (!seed.successful()) {
                return TrinityAlgorithmResult.failure(seed.diagnostic());
            }
            BigInteger optimalSeed = total(seed.value().modelSeed());
            BigInteger firingObjectiveLower = firingLower.max(
                    this.objectiveBounds.conservationFiringLowerBound(request, optimalExternal, optimalSeed));
            BigInteger seedWitnessFirings = total(seed.value().firings());
            TrinityAlgorithmResult<SolvedModel> firing = seedWitnessFirings.equals(firingObjectiveLower) ?
                    TrinityAlgorithmResult.success(seed.value()) :
                    optimize(
                            request,
                            new FiringPass(optimalExternal, optimalSeed, firingObjectiveLower),
                            control,
                            metrics);
            if (!firing.successful()) {
                if (firing.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                    return TrinityAlgorithmResult.failure(firing.diagnostic());
                }
                seedLower = optimalSeed.add(BigInteger.ONE);
                firingLower = BigInteger.ZERO;
                continue;
            }
            BigInteger optimalFirings = total(firing.value().firings());
            LinkedHashMap<TrinityPatternVariant, BigInteger> fixedFirings = new LinkedHashMap<>();
            SolvedModel canonical = firing.value();
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
                TrinityAlgorithmResult<SolvedModel> identity = optimize(
                        request,
                        identityPass,
                        control,
                        metrics);
                if (!identity.successful()) {
                    return TrinityAlgorithmResult.failure(identity.diagnostic());
                }
                canonical = identity.value();
                fixedFirings.put(variant, canonical.firings().getOrDefault(variant, BigInteger.ZERO));
            }
            return TrinityAlgorithmResult.success(new TrinityCycleFeasibilitySolution(
                    canonical.firings(),
                    canonical.modelSeed(),
                    canonical.externalInputs(),
                    metrics.passes,
                    metrics.nanos,
                    false));
        }
    }

    private TrinityAlgorithmResult<SolvedModel> optimize(
                                                         TrinityCycleFeasibilityRequest request,
                                                         ModelPass pass,
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
        ModelData data = createModel(request, pass);
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
        if (!result.getState().isOptimal()) {
            if (control.deadlineExceeded() || result.getState().isFeasible()) {
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
        return exact.successful() ? TrinityAlgorithmResult.success(solved) :
                TrinityAlgorithmResult.failure(exact.diagnostic());
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
                seedTotal.compareTo(this.objectiveBounds.minimumFirstInternalInput(request)) < 0) {
            return inexact("objective_lower", externalTotal + "/" + seedTotal);
        }
        if (pass instanceof SeedPass seed &&
                (!externalTotal.equals(seed.fixedExternal()) || seedTotal.compareTo(seed.seedLowerBound()) < 0)) {
            return inexact("seed_level", externalTotal + "/" + seedTotal);
        }
        if (pass instanceof FiringPass firing &&
                (!externalTotal.equals(firing.fixedExternal()) || !seedTotal.equals(firing.fixedSeed()) ||
                        firingTotal.compareTo(firing.firingLowerBound()) < 0)) {
            return inexact("firing_level", externalTotal + "/" + seedTotal + "/" + firingTotal);
        }
        if (pass instanceof IdentityPass identity &&
                (!externalTotal.equals(identity.fixedExternal()) || !seedTotal.equals(identity.fixedSeed()) ||
                        !firingTotal.equals(identity.fixedFirings()) || identity.fixedCounts().entrySet().stream()
                                .anyMatch(entry -> !solved.firings()
                                        .getOrDefault(entry.getKey(), BigInteger.ZERO)
                                        .equals(entry.getValue())))) {
            return inexact("identity_level", identity.variant().patternIdentity().publicationEncoding());
        }
        return exact;
    }

    private ModelData createModel(TrinityCycleFeasibilityRequest request, ModelPass pass) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        BigInteger logicalUpper = request.ordinaryLogicalUpperBound().orElseThrow(() -> new IllegalArgumentException("A signed Trinity system cannot use the ordinary exact model"));
        ArrayList<Variable> allVariables = new ArrayList<>();
        LinkedHashMap<TrinityPatternVariant, Variable> firingVariables = new LinkedHashMap<>();
        for (int index = 0; index < request.variants().size(); index++) {
            TrinityPatternVariant variant = request.variants().get(index);
            TrinityFiringBounds bounds = request.firingBounds().get(variant);
            Variable variable = model.addVariable("firing_" + index)
                    .integer()
                    .lower(bounds.lowerInclusive())
                    .upper(bounds.upperInclusive().min(logicalUpper));
            firingVariables.put(variant, variable);
            allVariables.add(variable);
        }
        LinkedHashMap<AEKey, Variable> seedVariables = reserveVariables(
                model,
                allVariables,
                request.internalKeys(),
                request,
                "seed_");
        LinkedHashMap<AEKey, Variable> externalVariables = reserveVariables(
                model,
                allVariables,
                this.objectiveBounds.externalReserveKeys(request),
                request,
                "external_");
        addConservation(model, request, firingVariables, seedVariables, externalVariables);
        Expression seedTotal = expression(model, "seed_total", seedVariables.values());
        seedTotal.lower(this.objectiveBounds.minimumFirstInternalInput(request));
        Expression externalTotal = expression(model, "external_total", externalVariables.values());
        externalTotal.lower(this.objectiveBounds.minimumFirstExternalInput(request));
        request.fixedExternalTotal().ifPresent(externalTotal::level);
        Expression firingTotal = expression(model, "firing_total", firingVariables.values());
        if (pass instanceof ExternalPass) {
            externalTotal.weight(BigDecimal.ONE);
        } else if (pass instanceof SeedPass seed) {
            externalTotal.level(seed.fixedExternal());
            seedTotal.lower(this.objectiveBounds.minimumFirstInternalInput(request).max(seed.seedLowerBound()));
            seedTotal.weight(BigDecimal.ONE);
        } else if (pass instanceof FiringPass firing) {
            externalTotal.level(firing.fixedExternal());
            seedTotal.level(firing.fixedSeed());
            firingTotal.lower(firing.firingLowerBound().max(
                    this.objectiveBounds.conservationFiringLowerBound(
                            request,
                            firing.fixedExternal(),
                            firing.fixedSeed())));
            firingTotal.weight(BigDecimal.ONE);
        } else if (pass instanceof IdentityPass identity) {
            externalTotal.level(identity.fixedExternal());
            seedTotal.level(identity.fixedSeed());
            firingTotal.level(identity.fixedFirings());
            identity.fixedCounts().forEach((variant, count) -> model
                    .addExpression("fixed_firing_" + request.variants().indexOf(variant))
                    .set(firingVariables.get(variant), BigInteger.ONE)
                    .level(count));
            BigInteger identityUpper = this.objectiveBounds.identityObjectiveUpperBound(
                    request,
                    identity.fixedExternal(),
                    identity.fixedSeed(),
                    identity.fixedFirings(),
                    identity.fixedCounts(),
                    identity.variant())
                    .min(request.firingBounds().get(identity.variant()).upperInclusive());
            model.addExpression("identity_objective")
                    .set(firingVariables.get(identity.variant()), BigInteger.ONE)
                    .upper(identityUpper)
                    .weight(BigDecimal.ONE.negate());
        } else {
            throw new IllegalStateException("Unknown Trinity ordinary MIP pass");
        }
        return new ModelData(
                model,
                List.copyOf(allVariables),
                firingVariables,
                seedVariables,
                externalVariables);
    }

    private static LinkedHashMap<AEKey, Variable> reserveVariables(
                                                                   ExpressionsBasedModel model,
                                                                   List<Variable> allVariables,
                                                                   Set<AEKey> keys,
                                                                   TrinityCycleFeasibilityRequest request,
                                                                   String prefix) {
        LinkedHashMap<AEKey, Variable> variables = new LinkedHashMap<>();
        int index = 0;
        for (AEKey key : keys) {
            Variable variable = model.addVariable(prefix + index++).integer().lower(BigInteger.ZERO);
            BigInteger logicalUpper = request.ordinaryLogicalUpperBound().orElseThrow();
            variable.upper(request.producibleInputs().contains(key) ?
                    logicalUpper : request.available().getOrDefault(key, BigInteger.ZERO).min(logicalUpper));
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
    }

    private static void setIfNonZero(Expression expression, Variable variable, BigInteger coefficient) {
        if (coefficient.signum() != 0) {
            expression.set(variable, coefficient);
        }
    }

    private static Expression expression(
                                         ExpressionsBasedModel model,
                                         String name,
                                         Iterable<Variable> variables) {
        Expression expression = model.addExpression(name);
        for (Variable variable : variables) {
            expression.set(variable, BigInteger.ONE);
        }
        return expression;
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

    private sealed interface ModelPass permits ExternalPass, SeedPass, FiringPass, IdentityPass {}

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

    private static final class SolverMetrics {

        private int passes;
        private long nanos;

        private void addPass(long elapsedNanos) {
            this.passes = Math.addExact(this.passes, 1);
            this.nanos = Math.addExact(this.nanos, elapsedNanos);
        }
    }
}
