package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.complement.TrinityFiringComplementOptimizer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Sequential shifted-MIP implementation whose large request quantities remain model constants.
 */
final class TrinityShiftedFiringOptimizerImpl implements TrinityShiftedFiringOptimizer {

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final String UNSUPPORTED_PATTERN_KEY = "gui.data_energistics.trinity_planning.diagnostic.unsupported_pattern";
    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String TIMEOUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.timeout";
    private static final String NO_INTEGER_SOLUTION_KEY = "gui.data_energistics.trinity_planning.diagnostic.no_integer_solution";
    private static final String INEXACT_RESULT_KEY = "gui.data_energistics.trinity_planning.diagnostic.inexact_result";

    private final TrinityIntegerResultVerifier integerVerifier;
    private final TrinityFiringComplementOptimizer complementOptimizer;

    TrinityShiftedFiringOptimizerImpl(
                                      TrinityIntegerResultVerifier integerVerifier,
                                      TrinityFiringComplementOptimizer complementOptimizer) {
        this.integerVerifier = integerVerifier;
        this.complementOptimizer = complementOptimizer;
    }

    @Override
    public TrinityPlanningAttempt<TrinityFiringOptimization> optimize(
                                                                      TrinityStronglyConnectedComponent component,
                                                                      TrinityCycleDemand demand,
                                                                      Map<AEKey, BigInteger> available,
                                                                      Set<AEKey> producibleInputs,
                                                                      Map<TrinityPatternVariant, BigInteger> firingUpperBound,
                                                                      TrinityPlanningControl control) {
        if (component == null || demand == null || available == null || producibleInputs == null ||
                firingUpperBound == null || firingUpperBound.isEmpty() || control == null) {
            throw new IllegalArgumentException("A shifted Trinity firing request is incomplete");
        }
        Set<AEKey> internalKeys = Set.copyOf(component.keys());
        List<TrinityPatternVariant> variants = component.cycleVariants().stream().sorted().toList();
        if (!firingUpperBound.keySet().containsAll(variants) || variants.stream()
                .anyMatch(variant -> firingUpperBound.get(variant) == null ||
                        firingUpperBound.get(variant).signum() < 0)) {
            return notApplicable(UNSUPPORTED_PATTERN_KEY);
        }
        Set<AEKey> externalCostKeys = externalReserveKeys(variants, internalKeys, demand);
        LinkedHashSet<AEKey> finiteExternal = new LinkedHashSet<>();
        externalCostKeys.stream()
                .filter(key -> !producibleInputs.contains(key))
                .forEach(finiteExternal::add);
        Set<AEKey> finiteExternalKeys = Collections.unmodifiableSet(finiteExternal);
        for (TrinityPatternVariant variant : variants) {
            for (AEKey key : externalCostKeys) {
                if (variant.netChange().getOrDefault(key, ZERO).signum() > 0) {
                    return notApplicable(UNSUPPORTED_PATTERN_KEY);
                }
            }
        }

        ShiftedContext context = new ShiftedContext(
                variants,
                internalKeys,
                externalCostKeys,
                finiteExternalKeys,
                Set.copyOf(producibleInputs),
                demand,
                available,
                firingUpperBound,
                netChange(firingUpperBound));
        int passes = 0;
        TrinityAlgorithmResult<SolvedShift> external = solve(
                context,
                ExternalPass.INSTANCE,
                control,
                ++passes);
        if (!external.successful()) {
            return unsuccessfulAttempt(external);
        }
        BigInteger optimalExternalSaving = external.value().externalSaving();

        TrinityAlgorithmResult<SolvedShift> seed = solve(
                context,
                new SeedPass(optimalExternalSaving),
                control,
                ++passes);
        if (!seed.successful()) {
            return unsuccessfulAttempt(seed);
        }
        BigInteger optimalSeed = seed.value().seedTotal();

        Set<TrinityPatternVariant> externallyFixed = variants.stream()
                .filter(variant -> externalCost(variant, externalCostKeys).signum() > 0)
                .collect(Collectors.toUnmodifiableSet());
        Optional<Map<TrinityPatternVariant, BigInteger>> complemented = this.complementOptimizer.minimize(
                component,
                demand,
                available,
                producibleInputs,
                firingUpperBound,
                seed.value().reductions(),
                externallyFixed);
        if (complemented.isPresent()) {
            Map<TrinityPatternVariant, BigInteger> firings = complemented.orElseThrow();
            Map<AEKey, BigInteger> net = netChange(firings);
            if (!satisfiesDemand(net, demand) ||
                    !fitsAvailable(net, demand, available, internalKeys, finiteExternalKeys)) {
                return notApplicable(inexact("exact_conservation", "complement_vector").diagnostic());
            }
            return TrinityPlanningAttempt.provedOptimal(new TrinityFiringOptimization(
                    firings,
                    externalReserveTotal(net, demand, externalCostKeys),
                    optimalSeed));
        }

        TrinityAlgorithmResult<SolvedShift> firing = solve(
                context,
                new FiringPass(optimalExternalSaving, optimalSeed),
                control,
                ++passes);
        if (!firing.successful()) {
            return unsuccessfulAttempt(firing);
        }
        BigInteger optimalReduction = firing.value().reductionTotal();
        LinkedHashMap<TrinityPatternVariant, BigInteger> fixedReductions = new LinkedHashMap<>();
        SolvedShift canonical = firing.value();
        for (TrinityPatternVariant variant : variants) {
            TrinityAlgorithmResult<SolvedShift> identity = solve(
                    context,
                    new IdentityPass(
                            optimalExternalSaving,
                            optimalSeed,
                            optimalReduction,
                            Collections.unmodifiableMap(new LinkedHashMap<>(fixedReductions)),
                            variant),
                    control,
                    ++passes);
            if (!identity.successful()) {
                return unsuccessfulAttempt(identity);
            }
            canonical = identity.value();
            fixedReductions.put(variant, canonical.reductions().getOrDefault(variant, ZERO));
        }
        LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            BigInteger firingCount = firingUpperBound.get(variant)
                    .subtract(canonical.reductions().getOrDefault(variant, ZERO));
            if (firingCount.signum() < 0) {
                return notApplicable(inexact("firing_lower", variant.patternIdentity().publicationEncoding())
                        .diagnostic());
            }
            if (firingCount.signum() > 0) {
                firings.put(variant, firingCount);
            }
        }
        Map<AEKey, BigInteger> net = netChange(firings);
        if (!satisfiesDemand(net, demand) ||
                !fitsAvailable(net, demand, available, internalKeys, finiteExternalKeys)) {
            return notApplicable(inexact("exact_conservation", "shifted_vector").diagnostic());
        }
        return TrinityPlanningAttempt.provedOptimal(new TrinityFiringOptimization(
                Collections.unmodifiableMap(firings),
                externalReserveTotal(net, demand, externalCostKeys),
                optimalSeed));
    }

    private TrinityAlgorithmResult<SolvedShift> solve(
                                                      ShiftedContext context,
                                                      ShiftedPass pass,
                                                      TrinityPlanningControl control,
                                                      int passNumber) {
        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    CANCELLED_KEY,
                    Map.of("passes", Integer.toString(passNumber - 1)));
        }
        if (control.deadlineExceeded()) {
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    TIMEOUT_KEY,
                    Map.of("passes", Integer.toString(passNumber - 1)));
        }
        ModelData data = createModel(context, pass);
        configureDeadline(data.model(), control);
        Optimisation.Result result = data.model().minimise();
        if (!result.getState().isOptimal()) {
            if (control.deadlineExceeded() || result.getState().isFeasible()) {
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        TIMEOUT_KEY,
                        Map.of("passes", Integer.toString(passNumber), "state", result.getState().name()));
            }
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                    NO_INTEGER_SOLUTION_KEY,
                    Map.of("passes", Integer.toString(passNumber), "state", result.getState().name()));
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
        SolvedShift solved = data.decode(verified.value(), context);
        if (!verifyPass(solved, pass)) {
            return inexact("objective_level", pass.getClass().getSimpleName());
        }
        return TrinityAlgorithmResult.success(solved);
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

    private static ModelData createModel(
                                         ShiftedContext context,
                                         ShiftedPass pass) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        ArrayList<Variable> variables = new ArrayList<>();
        LinkedHashMap<TrinityPatternVariant, Variable> reductions = new LinkedHashMap<>();
        for (int index = 0; index < context.variants().size(); index++) {
            TrinityPatternVariant variant = context.variants().get(index);
            Variable reduction = model.addVariable("reduction_" + index)
                    .lower(ZERO)
                    .upper(context.firingUpperBound().get(variant))
                    .integer();
            reductions.put(variant, reduction);
            variables.add(reduction);
        }
        LinkedHashMap<AEKey, Variable> seeds = new LinkedHashMap<>();
        int seedIndex = 0;
        for (AEKey key : context.internalKeys()) {
            Variable seed = model.addVariable("seed_" + seedIndex++)
                    .lower(ZERO)
                    .upper(seedUpperBound(context, key))
                    .integer();
            seeds.put(key, seed);
            variables.add(seed);
        }

        int constraintIndex = 0;
        for (AEKey key : context.internalKeys()) {
            Expression conservation = model.addExpression("internal_" + constraintIndex++);
            setShiftedNet(conservation, reductions, key);
            conservation.set(seeds.get(key), BigInteger.ONE);
            conservation.lower(context.demand().finalBalanceLowerBounds()
                    .getOrDefault(key, ZERO)
                    .subtract(context.baselineNet().getOrDefault(key, ZERO)));
        }
        for (AEKey key : context.finiteExternalKeys()) {
            Expression finiteInput = model.addExpression("external_" + constraintIndex++);
            setShiftedNet(finiteInput, reductions, key);
            finiteInput.lower(context.demand().finalBalanceLowerBounds()
                    .getOrDefault(key, ZERO)
                    .subtract(context.available().getOrDefault(key, ZERO))
                    .subtract(context.baselineNet().getOrDefault(key, ZERO)));
        }
        for (Map.Entry<AEKey, BigInteger> bound : context.demand().requiredNetChangeLowerBounds().entrySet()) {
            Expression requiredNet = model.addExpression("required_net_" + constraintIndex++);
            setShiftedNet(requiredNet, reductions, bound.getKey());
            requiredNet.lower(bound.getValue().subtract(
                    context.baselineNet().getOrDefault(bound.getKey(), ZERO)));
        }

        Expression externalSaving = model.addExpression("external_saving");
        reductions.forEach((variant, variable) -> {
            BigInteger saving = externalCost(variant, context.externalCostKeys());
            if (saving.signum() > 0) {
                externalSaving.set(variable, saving);
            }
        });
        Expression seedTotal = expression(model, "seed_total", seeds.values());
        seedTotal.lower(minimumFirstInternalInput(context.variants(), context.internalKeys()));
        Expression reductionTotal = expression(model, "reduction_total", reductions.values());

        if (pass instanceof ExternalPass) {
            externalSaving.weight(BigDecimal.ONE.negate());
        } else if (pass instanceof SeedPass seedPass) {
            externalSaving.lower(seedPass.externalSaving());
            seedTotal.weight(BigDecimal.ONE);
        } else if (pass instanceof FiringPass firingPass) {
            externalSaving.lower(firingPass.externalSaving());
            seedTotal.upper(firingPass.seedTotal());
            reductionTotal.weight(BigDecimal.ONE.negate());
        } else if (pass instanceof IdentityPass identityPass) {
            externalSaving.lower(identityPass.externalSaving());
            seedTotal.upper(identityPass.seedTotal());
            reductionTotal.lower(identityPass.reductionTotal());
            identityPass.fixedReductions().forEach((variant, value) -> reductions.get(variant)
                    .lower(value)
                    .upper(value));
            model.addExpression("identity_objective")
                    .set(reductions.get(identityPass.variant()), BigInteger.ONE)
                    .weight(BigDecimal.ONE);
        } else {
            throw new IllegalStateException("Unknown shifted Trinity optimization pass");
        }
        return new ModelData(
                model,
                List.copyOf(variables),
                Collections.unmodifiableMap(reductions),
                Collections.unmodifiableMap(seeds));
    }

    private static void setShiftedNet(
                                      Expression expression,
                                      Map<TrinityPatternVariant, Variable> reductions,
                                      AEKey key) {
        reductions.forEach((variant, variable) -> {
            BigInteger coefficient = variant.netChange().getOrDefault(key, ZERO).negate();
            if (coefficient.signum() != 0) {
                expression.set(variable, coefficient);
            }
        });
    }

    /**
     * Uses the finite verified incumbent as the bound for predecessor-produced cycle seeds. Supplying every bounded
     * internal input up front is sufficient for any non-negative reduction of that incumbent.
     */
    private static BigInteger seedUpperBound(ShiftedContext context, AEKey key) {
        BigInteger stored = context.available().getOrDefault(key, ZERO);
        if (!context.producibleInputs().contains(key)) {
            return stored;
        }
        BigInteger upstream = context.firingUpperBound().entrySet().stream()
                .map(entry -> entry.getKey().inputs()
                        .getOrDefault(key, ZERO)
                        .multiply(entry.getValue()))
                .reduce(ZERO, BigInteger::add);
        return stored.add(upstream);
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

    private static boolean verifyPass(SolvedShift solved, ShiftedPass pass) {
        if (pass instanceof SeedPass seedPass) {
            return solved.externalSaving().equals(seedPass.externalSaving());
        }
        if (pass instanceof FiringPass firingPass) {
            return solved.externalSaving().equals(firingPass.externalSaving()) &&
                    solved.seedTotal().equals(firingPass.seedTotal());
        }
        if (pass instanceof IdentityPass identityPass) {
            return solved.externalSaving().equals(identityPass.externalSaving()) &&
                    solved.seedTotal().equals(identityPass.seedTotal()) &&
                    solved.reductionTotal().equals(identityPass.reductionTotal()) &&
                    identityPass.fixedReductions().entrySet().stream().allMatch(entry -> solved.reductions()
                            .getOrDefault(entry.getKey(), ZERO)
                            .equals(entry.getValue()));
        }
        return true;
    }

    private static Set<AEKey> externalReserveKeys(
                                                  List<TrinityPatternVariant> variants,
                                                  Set<AEKey> internalKeys,
                                                  TrinityCycleDemand demand) {
        LinkedHashSet<AEKey> external = new LinkedHashSet<>();
        variants.forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(external::add));
        demand.finalBalanceLowerBounds().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(external::add);
        return Collections.unmodifiableSet(external);
    }

    private static BigInteger externalCost(TrinityPatternVariant variant, Set<AEKey> externalReserveKeys) {
        return externalReserveKeys.stream()
                .map(key -> variant.netChange().getOrDefault(key, ZERO).negate())
                .reduce(ZERO, BigInteger::add);
    }

    private static BigInteger externalReserveTotal(
                                                   Map<AEKey, BigInteger> net,
                                                   TrinityCycleDemand demand,
                                                   Set<AEKey> externalReserveKeys) {
        return externalReserveKeys.stream()
                .map(key -> demand.finalBalanceLowerBounds()
                        .getOrDefault(key, ZERO)
                        .subtract(net.getOrDefault(key, ZERO))
                        .max(ZERO))
                .reduce(ZERO, BigInteger::add);
    }

    private static BigInteger minimumFirstInternalInput(
                                                        List<TrinityPatternVariant> variants,
                                                        Set<AEKey> internalKeys) {
        return variants.stream()
                .map(variant -> variant.inputs().entrySet().stream()
                        .filter(entry -> internalKeys.contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .reduce(ZERO, BigInteger::add))
                .filter(amount -> amount.signum() > 0)
                .min(BigInteger::compareTo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "A shifted Trinity component must consume an internal key"));
    }

    private static Map<AEKey, BigInteger> netChange(Map<TrinityPatternVariant, BigInteger> firings) {
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(net);
    }

    private static boolean satisfiesDemand(Map<AEKey, BigInteger> net, TrinityCycleDemand demand) {
        return demand.requiredNetChangeLowerBounds().entrySet().stream().allMatch(entry -> net
                .getOrDefault(entry.getKey(), ZERO)
                .compareTo(entry.getValue()) >= 0);
    }

    private static boolean fitsAvailable(
                                         Map<AEKey, BigInteger> net,
                                         TrinityCycleDemand demand,
                                         Map<AEKey, BigInteger> available,
                                         Set<AEKey> internalKeys,
                                         Set<AEKey> externalReserveKeys) {
        LinkedHashSet<AEKey> bounded = new LinkedHashSet<>(internalKeys);
        bounded.addAll(externalReserveKeys);
        return bounded.stream().allMatch(key -> available.getOrDefault(key, ZERO)
                .add(net.getOrDefault(key, ZERO))
                .compareTo(demand.finalBalanceLowerBounds().getOrDefault(key, ZERO)) >= 0);
    }

    private static <T> TrinityAlgorithmResult<T> inexact(String constraint, String value) {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                INEXACT_RESULT_KEY,
                Map.of("constraint", constraint, "value", value));
    }

    private static <T> TrinityAlgorithmResult<T> failure(
                                                         TrinityPlanningDiagnosticCode code,
                                                         String translationKey,
                                                         Map<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.translatable(translationKey),
                metadata));
    }

    private static <T> TrinityPlanningAttempt<T> notApplicable(String detail) {
        return notApplicable(TrinityPlanningDiagnostic.ofTranslationKey(
                TrinityPlanningDiagnosticCode.UNSUPPORTED_PATTERN,
                detail));
    }

    private static <T> TrinityPlanningAttempt<T> notApplicable(TrinityPlanningDiagnostic diagnostic) {
        return TrinityPlanningAttempt.notApplicable(diagnostic);
    }

    private static <T> TrinityPlanningAttempt<T> unsuccessfulAttempt(TrinityAlgorithmResult<?> result) {
        TrinityPlanningDiagnostic diagnostic = result.diagnostic();
        if (diagnostic.code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED ||
                diagnostic.code() == TrinityPlanningDiagnosticCode.MIP_TIMEOUT) {
            return TrinityPlanningAttempt.terminal(diagnostic);
        }
        return TrinityPlanningAttempt.notApplicable(diagnostic);
    }

    private sealed interface ShiftedPass
                                         permits ExternalPass, SeedPass, FiringPass, IdentityPass {}

    private enum ExternalPass implements ShiftedPass {
        INSTANCE
    }

    private record SeedPass(BigInteger externalSaving) implements ShiftedPass {}

    private record FiringPass(BigInteger externalSaving, BigInteger seedTotal) implements ShiftedPass {}

    private record IdentityPass(
                                BigInteger externalSaving,
                                BigInteger seedTotal,
                                BigInteger reductionTotal,
                                Map<TrinityPatternVariant, BigInteger> fixedReductions,
                                TrinityPatternVariant variant)
            implements ShiftedPass {}

    private record ShiftedContext(
                                  List<TrinityPatternVariant> variants,
                                  Set<AEKey> internalKeys,
                                  Set<AEKey> externalCostKeys,
                                  Set<AEKey> finiteExternalKeys,
                                  Set<AEKey> producibleInputs,
                                  TrinityCycleDemand demand,
                                  Map<AEKey, BigInteger> available,
                                  Map<TrinityPatternVariant, BigInteger> firingUpperBound,
                                  Map<AEKey, BigInteger> baselineNet) {}

    private record ModelData(
                             ExpressionsBasedModel model,
                             List<Variable> variables,
                             Map<TrinityPatternVariant, Variable> reductions,
                             Map<AEKey, Variable> seeds) {

        private SolvedShift decode(List<BigInteger> values, ShiftedContext context) {
            LinkedHashMap<Variable, BigInteger> byVariable = new LinkedHashMap<>();
            for (int index = 0; index < this.variables.size(); index++) {
                byVariable.put(this.variables.get(index), values.get(index));
            }
            LinkedHashMap<TrinityPatternVariant, BigInteger> decodedReductions = new LinkedHashMap<>();
            this.reductions.forEach((variant, variable) -> {
                BigInteger value = byVariable.get(variable);
                if (value.signum() > 0) {
                    decodedReductions.put(variant, value);
                }
            });
            LinkedHashMap<AEKey, BigInteger> decodedSeeds = new LinkedHashMap<>();
            this.seeds.forEach((key, variable) -> {
                BigInteger value = byVariable.get(variable);
                if (value.signum() > 0) {
                    decodedSeeds.put(key, value);
                }
            });
            BigInteger externalSaving = decodedReductions.entrySet().stream()
                    .map(entry -> externalCost(entry.getKey(), context.externalCostKeys())
                            .multiply(entry.getValue()))
                    .reduce(ZERO, BigInteger::add);
            BigInteger reductionTotal = decodedReductions.values().stream().reduce(ZERO, BigInteger::add);
            BigInteger seedTotal = decodedSeeds.values().stream().reduce(ZERO, BigInteger::add);
            return new SolvedShift(
                    Collections.unmodifiableMap(decodedReductions),
                    Collections.unmodifiableMap(decodedSeeds),
                    externalSaving,
                    seedTotal,
                    reductionTotal);
        }
    }

    private record SolvedShift(
                               Map<TrinityPatternVariant, BigInteger> reductions,
                               Map<AEKey, BigInteger> seeds,
                               BigInteger externalSaving,
                               BigInteger seedTotal,
                               BigInteger reductionTotal) {}
}
