package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Sequential shifted-MIP implementation whose large request quantities remain model constants.
 */
final class TrinityShiftedFiringOptimizerImpl implements TrinityShiftedFiringOptimizer {

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger MINIMUM_RELAXED_BOUND_GUARD = BigInteger.valueOf(4L);

    private final TrinityIntegerResultVerifier integerVerifier;

    TrinityShiftedFiringOptimizerImpl(TrinityIntegerResultVerifier integerVerifier) {
        this.integerVerifier = integerVerifier;
    }

    @Override
    public Optional<TrinityAlgorithmResult<Map<TrinityPatternVariant, BigInteger>>> optimize(
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
        if (producibleInputs.stream().anyMatch(internalKeys::contains)) {
            return Optional.empty();
        }
        List<TrinityPatternVariant> variants = component.cycleVariants().stream().sorted().toList();
        if (!firingUpperBound.keySet().containsAll(variants)) {
            return Optional.empty();
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
                    return Optional.empty();
                }
            }
        }

        ShiftedContext unboundedContext = new ShiftedContext(
                variants,
                internalKeys,
                externalCostKeys,
                finiteExternalKeys,
                demand,
                available,
                firingUpperBound,
                firingUpperBound,
                Map.of(),
                netChange(firingUpperBound));
        int passes = 0;
        TrinityAlgorithmResult<ReductionBounds> tightened = tightenReductionBounds(
                unboundedContext,
                control,
                passes);
        if (!tightened.successful()) {
            return Optional.of(TrinityAlgorithmResult.failure(tightened.diagnostic()));
        }
        passes = Math.addExact(passes, variants.size());
        ShiftedContext context = new ShiftedContext(
                variants,
                internalKeys,
                externalCostKeys,
                finiteExternalKeys,
                demand,
                available,
                firingUpperBound,
                tightened.value().upperBounds(),
                tightened.value().guards(),
                unboundedContext.baselineNet());
        TrinityAlgorithmResult<SolvedShift> external = solve(
                context,
                ExternalPass.INSTANCE,
                control,
                ++passes);
        if (!external.successful()) {
            return Optional.of(TrinityAlgorithmResult.failure(external.diagnostic()));
        }
        BigInteger optimalExternalSaving = external.value().externalSaving();

        TrinityAlgorithmResult<SolvedShift> seed = solve(
                context,
                new SeedPass(optimalExternalSaving),
                control,
                ++passes);
        if (!seed.successful()) {
            return Optional.of(TrinityAlgorithmResult.failure(seed.diagnostic()));
        }
        BigInteger optimalSeed = seed.value().seedTotal();

        TrinityAlgorithmResult<SolvedShift> firing = solve(
                context,
                new FiringPass(optimalExternalSaving, optimalSeed),
                control,
                ++passes);
        if (!firing.successful()) {
            return Optional.of(TrinityAlgorithmResult.failure(firing.diagnostic()));
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
                return Optional.of(TrinityAlgorithmResult.failure(identity.diagnostic()));
            }
            canonical = identity.value();
            fixedReductions.put(variant, canonical.reductions().getOrDefault(variant, ZERO));
        }
        if (touchesRelaxedBoundary(canonical, context)) {
            return Optional.empty();
        }

        LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            BigInteger firingCount = firingUpperBound.get(variant)
                    .subtract(canonical.reductions().getOrDefault(variant, ZERO));
            if (firingCount.signum() < 0) {
                return Optional.of(inexact("firing_lower", variant.patternIdentity().publicationEncoding()));
            }
            if (firingCount.signum() > 0) {
                firings.put(variant, firingCount);
            }
        }
        Map<AEKey, BigInteger> net = netChange(firings);
        if (!satisfiesDemand(net, demand) ||
                !fitsAvailable(net, demand, available, internalKeys, finiteExternalKeys)) {
            return Optional.of(inexact("exact_conservation", "shifted_vector"));
        }
        return Optional.of(TrinityAlgorithmResult.success(Collections.unmodifiableMap(firings)));
    }

    private static boolean touchesRelaxedBoundary(SolvedShift solved, ShiftedContext context) {
        for (TrinityPatternVariant variant : context.variants()) {
            BigInteger tightened = context.reductionUpperBounds().get(variant);
            BigInteger structural = context.firingUpperBound().get(variant);
            if (tightened.compareTo(structural) < 0 && solved.reductions()
                    .getOrDefault(variant, ZERO)
                    .add(context.reductionBoundGuards().getOrDefault(variant, ZERO))
                    .compareTo(tightened) >= 0) {
                return true;
            }
        }
        return false;
    }

    private TrinityAlgorithmResult<ReductionBounds> tightenReductionBounds(
                                                                           ShiftedContext context,
                                                                           TrinityPlanningControl control,
                                                                           int completedPasses) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> tightened = new LinkedHashMap<>();
        LinkedHashMap<TrinityPatternVariant, BigInteger> guards = new LinkedHashMap<>();
        int passNumber = completedPasses;
        for (TrinityPatternVariant variant : context.variants()) {
            if (control.cancellationRequested()) {
                return failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        "Shifted Trinity bound tightening was cancelled",
                        Map.of("passes", Integer.toString(passNumber)));
            }
            if (control.deadlineExceeded()) {
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        "Shifted Trinity bound tightening exhausted its deadline",
                        Map.of("passes", Integer.toString(passNumber)));
            }
            passNumber = Math.incrementExact(passNumber);
            ModelData data = createModel(context, new ReductionBoundPass(variant), false);
            configureDeadline(data.model(), control);
            Optimisation.Result result = data.model().minimise();
            if (!result.getState().isOptimal()) {
                return failure(
                        result.getState().isFeasible() ?
                                TrinityPlanningDiagnosticCode.MIP_TIMEOUT :
                                TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                        "Shifted Trinity bound tightening did not prove a finite relaxation",
                        Map.of("passes", Integer.toString(passNumber), "state", result.getState().name()));
            }
            Variable reduction = data.reductions().get(variant);
            BigDecimal relaxed = result.get(data.model().indexOf(reduction));
            BigInteger guard = relaxedBoundGuard(relaxed, context);
            BigInteger guarded = relaxed
                    .max(BigDecimal.ZERO)
                    .setScale(0, RoundingMode.CEILING)
                    .toBigIntegerExact()
                    .add(guard);
            tightened.put(
                    variant,
                    guarded.min(context.firingUpperBound().get(variant)));
            guards.put(variant, guard);
        }
        return TrinityAlgorithmResult.success(new ReductionBounds(
                Collections.unmodifiableMap(tightened),
                Collections.unmodifiableMap(guards)));
    }

    private static BigInteger relaxedBoundGuard(BigDecimal relaxed, ShiftedContext context) {
        double magnitude = Math.abs(relaxed.doubleValue());
        if (!Double.isFinite(magnitude)) {
            return context.firingUpperBound().values().stream()
                    .max(BigInteger::compareTo)
                    .orElseThrow();
        }
        long dimension = Math.addExact(
                Math.addExact(context.variants().size(), context.internalKeys().size()),
                Math.addExact(
                        context.finiteExternalKeys().size(),
                        Math.addExact(
                                context.demand().finalBalanceLowerBounds().size(),
                                context.demand().requiredNetChangeLowerBounds().size())));
        double scale = Math.max(32.0D, Math.multiplyExact(dimension, dimension) * 8.0D);
        double guardedUlp = Math.ceil(Math.ulp(magnitude) * scale);
        if (!Double.isFinite(guardedUlp)) {
            return context.firingUpperBound().values().stream()
                    .max(BigInteger::compareTo)
                    .orElseThrow();
        }
        return BigDecimal.valueOf(guardedUlp)
                .toBigIntegerExact()
                .max(MINIMUM_RELAXED_BOUND_GUARD);
    }

    private TrinityAlgorithmResult<SolvedShift> solve(
                                                      ShiftedContext context,
                                                      ShiftedPass pass,
                                                      TrinityPlanningControl control,
                                                      int passNumber) {
        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    "Shifted Trinity firing optimization was cancelled",
                    Map.of("passes", Integer.toString(passNumber - 1)));
        }
        if (control.deadlineExceeded()) {
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    "Shifted Trinity firing optimization exhausted its deadline",
                    Map.of("passes", Integer.toString(passNumber - 1)));
        }
        ModelData data = createModel(context, pass, true);
        configureDeadline(data.model(), control);
        Optimisation.Result result = data.model().minimise();
        if (!result.getState().isOptimal()) {
            if (control.deadlineExceeded() || result.getState().isFeasible()) {
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        "Shifted Trinity firing optimization did not prove an optimum",
                        Map.of("passes", Integer.toString(passNumber), "state", result.getState().name()));
            }
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                    "Shifted Trinity firing optimization has no integer solution",
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
                                         ShiftedPass pass,
                                         boolean integerReductions) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        ArrayList<Variable> variables = new ArrayList<>();
        LinkedHashMap<TrinityPatternVariant, Variable> reductions = new LinkedHashMap<>();
        for (int index = 0; index < context.variants().size(); index++) {
            TrinityPatternVariant variant = context.variants().get(index);
            Variable reduction = model.addVariable("reduction_" + index)
                    .lower(ZERO)
                    .upper(context.reductionUpperBounds().get(variant));
            if (integerReductions) {
                reduction.integer();
            }
            reductions.put(variant, reduction);
            variables.add(reduction);
        }
        LinkedHashMap<AEKey, Variable> seeds = new LinkedHashMap<>();
        int seedIndex = 0;
        for (AEKey key : context.internalKeys()) {
            Variable seed = model.addVariable("seed_" + seedIndex++)
                    .lower(ZERO)
                    .upper(context.available().getOrDefault(key, ZERO));
            if (integerReductions) {
                seed.integer();
            }
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
            externalSaving.level(seedPass.externalSaving());
            seedTotal.weight(BigDecimal.ONE);
        } else if (pass instanceof FiringPass firingPass) {
            externalSaving.level(firingPass.externalSaving());
            seedTotal.level(firingPass.seedTotal());
            reductionTotal.weight(BigDecimal.ONE.negate());
        } else if (pass instanceof IdentityPass identityPass) {
            externalSaving.level(identityPass.externalSaving());
            seedTotal.level(identityPass.seedTotal());
            reductionTotal.level(identityPass.reductionTotal());
            identityPass.fixedReductions().forEach((variant, value) -> model
                    .addExpression("fixed_" + context.variants().indexOf(variant))
                    .set(reductions.get(variant), BigInteger.ONE)
                    .level(value));
            model.addExpression("identity_objective")
                    .set(reductions.get(identityPass.variant()), BigInteger.ONE)
                    .weight(BigDecimal.ONE.negate());
        } else if (pass instanceof ReductionBoundPass reductionBoundPass) {
            model.addExpression("reduction_bound")
                    .set(reductions.get(reductionBoundPass.variant()), BigInteger.ONE)
                    .weight(BigDecimal.ONE.negate());
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
                "A shifted ojAlgo result violates exact Trinity constraints",
                Map.of("constraint", constraint, "value", value));
    }

    private static <T> TrinityAlgorithmResult<T> failure(
                                                         TrinityPlanningDiagnosticCode code,
                                                         String detail,
                                                         Map<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.literal(detail),
                metadata));
    }

    private sealed interface ShiftedPass
                                         permits ExternalPass, SeedPass, FiringPass, IdentityPass, ReductionBoundPass {}

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

    private record ReductionBoundPass(TrinityPatternVariant variant) implements ShiftedPass {}

    private record ShiftedContext(
                                  List<TrinityPatternVariant> variants,
                                  Set<AEKey> internalKeys,
                                  Set<AEKey> externalCostKeys,
                                  Set<AEKey> finiteExternalKeys,
                                  TrinityCycleDemand demand,
                                  Map<AEKey, BigInteger> available,
                                  Map<TrinityPatternVariant, BigInteger> firingUpperBound,
                                  Map<TrinityPatternVariant, BigInteger> reductionUpperBounds,
                                  Map<TrinityPatternVariant, BigInteger> reductionBoundGuards,
                                  Map<AEKey, BigInteger> baselineNet) {}

    private record ReductionBounds(
                                   Map<TrinityPatternVariant, BigInteger> upperBounds,
                                   Map<TrinityPatternVariant, BigInteger> guards) {}

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
