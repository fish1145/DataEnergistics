package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedScheduler;
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

/**
 * Sequential no-big-M implementation. Exact seed is part of candidate selection, not a post-hoc acceptance detail.
 */
final class TrinityMixedIntegerCycleSolverImpl implements TrinityMixedIntegerCycleSolver {

    private final TrinityIntegerResultVerifier integerVerifier;
    private final TrinityExactConservationVerifier conservationVerifier;
    private final TrinityMinimumSeedScheduler seedScheduler;

    TrinityMixedIntegerCycleSolverImpl(TrinityIntegerResultVerifier integerVerifier,
                                       TrinityExactConservationVerifier conservationVerifier,
                                       TrinityMinimumSeedScheduler seedScheduler) {
        this.integerVerifier = integerVerifier;
        this.conservationVerifier = conservationVerifier;
        this.seedScheduler = seedScheduler;
    }

    @Override
    public TrinityAlgorithmResult<TrinityMipCyclePlan> solve(
                                                             TrinityStronglyConnectedComponent component,
                                                             AEKey target,
                                                             BigInteger requestedAmount,
                                                             CraftingQuantityMode quantityMode,
                                                             Map<AEKey, BigInteger> available,
                                                             Set<AEKey> producibleInputs,
                                                             int maxSearchStates,
                                                             TrinityPlanningControl control) {
        if (component == null || !component.cyclic() || component.cycleVariants().isEmpty() || target == null ||
                requestedAmount == null || requestedAmount.signum() <= 0 || quantityMode == null ||
                available == null || producibleInputs == null || maxSearchStates <= 0 || control == null) {
            throw new IllegalArgumentException("A Trinity multi-route cycle request is incomplete");
        }
        if (!component.keys().contains(target)) {
            throw new IllegalArgumentException("The Trinity MIP target must belong to its SCC");
        }
        List<TrinityPatternVariant> variants = component.cycleVariants().stream().sorted().toList();
        Map<AEKey, BigInteger> inventory = copyAvailable(available);
        Set<AEKey> producible = copyKeys(producibleInputs);
        Set<AEKey> internalKeys = Collections.unmodifiableSet(new LinkedHashSet<>(component.keys()));
        BigInteger requiredTargetNet = requiredTargetNet(
                target,
                requestedAmount,
                quantityMode,
                inventory);
        SolverMetrics metrics = new SolverMetrics();

        ModelRequest externalRequest = new ModelRequest(
                variants,
                internalKeys,
                target,
                requestedAmount,
                requiredTargetNet,
                quantityMode,
                inventory,
                producible,
                ExternalInputPass.INSTANCE);
        TrinityAlgorithmResult<SolvedModel> externalSolve = solveModel(externalRequest, control, metrics);
        if (!externalSolve.successful()) {
            return TrinityAlgorithmResult.failure(externalSolve.diagnostic());
        }
        BigInteger optimalExternal = sum(externalSolve.value().externalInputs());

        BigInteger seedLower = BigInteger.ZERO;
        BigInteger firingLower = BigInteger.ZERO;
        SearchBudget searchBudget = new SearchBudget(maxSearchStates);
        while (true) {
            ModelRequest seedRequest = new ModelRequest(
                    variants,
                    internalKeys,
                    target,
                    requestedAmount,
                    requiredTargetNet,
                    quantityMode,
                    inventory,
                    producible,
                    new SeedPass(optimalExternal, seedLower));
            TrinityAlgorithmResult<SolvedModel> seedSolve = solveModel(seedRequest, control, metrics);
            if (!seedSolve.successful()) {
                return TrinityAlgorithmResult.failure(seedSolve.diagnostic());
            }
            BigInteger optimalSeed = sum(seedSolve.value().modelSeed());

            ModelRequest firingRequest = new ModelRequest(
                    variants,
                    internalKeys,
                    target,
                    requestedAmount,
                    requiredTargetNet,
                    quantityMode,
                    inventory,
                    producible,
                    new FiringPass(optimalExternal, optimalSeed, firingLower));
            TrinityAlgorithmResult<SolvedModel> firingSolve = solveModel(firingRequest, control, metrics);
            if (!firingSolve.successful()) {
                if (firingSolve.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                    return TrinityAlgorithmResult.failure(firingSolve.diagnostic());
                }
                seedLower = optimalSeed.add(BigInteger.ONE);
                firingLower = BigInteger.ZERO;
                continue;
            }
            BigInteger optimalFirings = sum(firingSolve.value().firings());

            CandidateSearch candidateSearch = new CandidateSearch(
                    variants,
                    internalKeys,
                    target,
                    requestedAmount,
                    requiredTargetNet,
                    quantityMode,
                    inventory,
                    producible,
                    optimalExternal,
                    optimalSeed,
                    optimalFirings,
                    searchBudget,
                    control,
                    metrics);
            TrinityAlgorithmResult<TrinityMipCyclePlan> candidate = candidateSearch.search();
            if (candidate.successful()) {
                return candidate;
            }
            if (candidate.diagnostic().code() != TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION) {
                return candidate;
            }
            firingLower = optimalFirings.add(BigInteger.ONE);
        }
    }

    private TrinityAlgorithmResult<SolvedModel> solveModel(
                                                           ModelRequest request,
                                                           TrinityPlanningControl control,
                                                           SolverMetrics metrics) {
        LinkedHashSet<AEKey> enforcedUpperBounds = new LinkedHashSet<>();
        while (true) {
            if (control.cancellationRequested()) {
                return failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        "Trinity MIP solving was cancelled",
                        Map.of("passes", Integer.toString(metrics.passes)));
            }
            if (control.deadlineExceeded()) {
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        "Trinity MIP exhausted its shared deadline",
                        Map.of("passes", Integer.toString(metrics.passes)));
            }

            ModelData data = createModel(request, enforcedUpperBounds);
            long remainingNanos = control.remainingNanos();
            long remainingMillis = Math.max(
                    1L,
                    TimeUnit.NANOSECONDS.toMillis(remainingNanos) +
                            (remainingNanos % 1_000_000L == 0L ? 0L : 1L));
            data.model().options.time_abort = remainingMillis;
            data.model().options.time_suffice = remainingMillis;
            long started = System.nanoTime();
            Optimisation.Result result = data.model().minimise();
            long elapsed = System.nanoTime() - started;
            metrics.passes = Math.addExact(metrics.passes, 1);
            metrics.nanos = Math.addExact(metrics.nanos, Math.max(0L, elapsed));

            if (control.cancellationRequested()) {
                return failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        "Trinity MIP solving was cancelled",
                        Map.of("passes", Integer.toString(metrics.passes)));
            }
            if (!result.getState().isOptimal()) {
                if (control.deadlineExceeded() || result.getState().isFeasible()) {
                    return failure(
                            TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                            "Trinity MIP did not prove an optimum before its deadline",
                            Map.of("state", result.getState().name()));
                }
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                        "Trinity MIP has no integer solution for the requested bounds",
                        Map.of("state", result.getState().name()));
            }

            ArrayList<BigDecimal> rawValues = new ArrayList<>(data.variables().size());
            for (Variable variable : data.variables()) {
                rawValues.add(result.get(data.model().indexOf(variable)));
            }
            TrinityAlgorithmResult<List<BigInteger>> verified = this.integerVerifier.verify(rawValues);
            if (!verified.successful()) {
                return TrinityAlgorithmResult.failure(verified.diagnostic());
            }
            if (verified.value().stream().anyMatch(value -> value.signum() < 0)) {
                return inexactResult("variable_lower", "negative");
            }
            SolvedModel solved = data.decode(verified.value());
            Set<AEKey> violatedUpperBounds = violatedUpperBounds(request, solved);
            if (!violatedUpperBounds.isEmpty()) {
                if (!enforcedUpperBounds.addAll(violatedUpperBounds)) {
                    return inexactResult("variable_upper", "enforced_bound_was_violated");
                }
                continue;
            }
            TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verifyExactModelResult(request, solved);
            if (!exact.successful()) {
                return TrinityAlgorithmResult.failure(exact.diagnostic());
            }
            return TrinityAlgorithmResult.success(solved);
        }
    }

    private static Set<AEKey> violatedUpperBounds(ModelRequest request, SolvedModel solved) {
        LinkedHashSet<AEKey> violated = new LinkedHashSet<>();
        collectViolatedUpperBounds(request, solved.modelSeed(), violated);
        collectViolatedUpperBounds(request, solved.externalInputs(), violated);
        return Collections.unmodifiableSet(violated);
    }

    private static void collectViolatedUpperBounds(ModelRequest request,
                                                   Map<AEKey, BigInteger> amounts,
                                                   Set<AEKey> violated) {
        amounts.forEach((key, amount) -> {
            if (!request.producibleInputs().contains(key) &&
                    amount.compareTo(request.available().getOrDefault(key, BigInteger.ZERO)) > 0) {
                violated.add(key);
            }
        });
    }

    private TrinityAlgorithmResult<Map<AEKey, BigInteger>> verifyExactModelResult(
                                                                                  ModelRequest request,
                                                                                  SolvedModel solved) {
        LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>(solved.externalInputs());
        solved.modelSeed().forEach((key, amount) -> initialInputs.merge(key, amount, BigInteger::add));
        Map<AEKey, BigInteger> finiteUpperBounds = finiteInputUpperBounds(
                request.variants(),
                request.internalKeys(),
                request.available(),
                request.producibleInputs());
        Map<AEKey, BigInteger> finalLowerBounds = request.quantityMode() == CraftingQuantityMode.FINAL_TOTAL ?
                Map.of(request.target(), request.requestedAmount()) :
                Map.of();
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = this.conservationVerifier.verify(
                request.variants(),
                solved.firings(),
                initialInputs,
                finiteUpperBounds,
                finalLowerBounds,
                request.target(),
                request.requiredTargetNet());
        if (!exact.successful()) {
            return exact;
        }

        BigInteger externalTotal = sum(solved.externalInputs());
        BigInteger minimumExternal = minimumFirstExternalInput(request.variants(), request.internalKeys());
        if (externalTotal.compareTo(minimumExternal) < 0) {
            return inexactResult("external_lower", externalTotal + "<" + minimumExternal);
        }

        BigInteger seedTotal = sum(solved.modelSeed());
        BigInteger minimumSeed = minimumFirstInternalInput(request.variants(), request.internalKeys());
        if (seedTotal.compareTo(minimumSeed) < 0) {
            return inexactResult("seed_lower", seedTotal + "<" + minimumSeed);
        }

        BigInteger firingTotal = sum(solved.firings());
        if (request.pass() instanceof SeedPass pass) {
            if (externalTotal.compareTo(pass.fixedExternal()) != 0) {
                return inexactResult("external_level", externalTotal + "!=" + pass.fixedExternal());
            }
            if (seedTotal.compareTo(pass.seedLowerBound()) < 0) {
                return inexactResult("seed_lower", seedTotal + "<" + pass.seedLowerBound());
            }
        } else if (request.pass() instanceof FiringPass pass) {
            if (externalTotal.compareTo(pass.fixedExternal()) != 0) {
                return inexactResult("external_level", externalTotal + "!=" + pass.fixedExternal());
            }
            if (seedTotal.compareTo(pass.fixedSeed()) != 0) {
                return inexactResult("seed_level", seedTotal + "!=" + pass.fixedSeed());
            }
            if (firingTotal.compareTo(pass.firingLowerBound()) < 0) {
                return inexactResult("firing_lower", firingTotal + "<" + pass.firingLowerBound());
            }
        }
        return exact;
    }

    private static ModelData createModel(ModelRequest request, Set<AEKey> enforcedUpperBounds) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        ArrayList<Variable> allVariables = new ArrayList<>();
        LinkedHashMap<TrinityPatternVariant, Variable> firingVariables = new LinkedHashMap<>();
        for (int index = 0; index < request.variants().size(); index++) {
            Variable variable = model.addVariable("firing_" + index)
                    .integer()
                    .lower(BigInteger.ZERO);
            firingVariables.put(request.variants().get(index), variable);
            allVariables.add(variable);
        }

        LinkedHashMap<AEKey, Variable> seedVariables = new LinkedHashMap<>();
        int seedIndex = 0;
        for (AEKey key : request.internalKeys()) {
            Variable variable = model.addVariable("seed_" + seedIndex++)
                    .integer()
                    .lower(BigInteger.ZERO);
            if (enforcedUpperBounds.contains(key)) {
                variable.upper(request.available().getOrDefault(key, BigInteger.ZERO));
            }
            seedVariables.put(key, variable);
            allVariables.add(variable);
        }
        Set<AEKey> externalKeys = externalInputKeys(request.variants(), request.internalKeys());
        LinkedHashMap<AEKey, Variable> externalVariables = new LinkedHashMap<>();
        int externalIndex = 0;
        for (AEKey key : externalKeys) {
            Variable variable = model.addVariable("external_" + externalIndex++)
                    .integer()
                    .lower(BigInteger.ZERO);
            if (enforcedUpperBounds.contains(key)) {
                variable.upper(request.available().getOrDefault(key, BigInteger.ZERO));
            }
            externalVariables.put(key, variable);
            allVariables.add(variable);
        }

        LinkedHashSet<AEKey> touchedKeys = new LinkedHashSet<>();
        request.variants().forEach(variant -> {
            touchedKeys.addAll(variant.inputs().keySet());
            touchedKeys.addAll(variant.outputs().keySet());
        });
        int conservationIndex = 0;
        for (AEKey key : touchedKeys) {
            Expression conservation = model.addExpression("conservation_" + conservationIndex++);
            firingVariables.forEach((variant, variable) -> {
                BigInteger coefficient = variant.netChange().getOrDefault(key, BigInteger.ZERO);
                if (coefficient.signum() != 0) {
                    conservation.set(variable, coefficient);
                }
            });
            Variable reserve = request.internalKeys().contains(key) ?
                    seedVariables.get(key) :
                    externalVariables.get(key);
            if (reserve != null) {
                conservation.set(reserve, BigInteger.ONE);
            }
            BigInteger finalLower = request.quantityMode() == CraftingQuantityMode.FINAL_TOTAL &&
                    key.equals(request.target()) ?
                            request.requestedAmount() :
                            BigInteger.ZERO;
            conservation.lower(finalLower);
        }
        Expression targetNet = model.addExpression("target_net");
        firingVariables.forEach((variant, variable) -> {
            BigInteger coefficient = variant.netChange().getOrDefault(request.target(), BigInteger.ZERO);
            if (coefficient.signum() != 0) {
                targetNet.set(variable, coefficient);
            }
        });
        targetNet.lower(request.requiredTargetNet());

        Expression seedTotal = expression(model, "seed_total", seedVariables.values());
        seedTotal.lower(minimumFirstInternalInput(request.variants(), request.internalKeys()));
        Expression externalTotal = expression(model, "external_total", externalVariables.values());
        externalTotal.lower(minimumFirstExternalInput(request.variants(), request.internalKeys()));
        Expression firingTotal = expression(model, "firing_total", firingVariables.values());
        if (request.pass() instanceof ExternalInputPass) {
            externalTotal.weight(BigDecimal.ONE);
        } else if (request.pass() instanceof SeedPass pass) {
            externalTotal.level(pass.fixedExternal());
            seedTotal.lower(minimumFirstInternalInput(request.variants(), request.internalKeys())
                    .max(pass.seedLowerBound()));
            seedTotal.weight(BigDecimal.ONE);
        } else if (request.pass() instanceof FiringPass pass) {
            externalTotal.level(pass.fixedExternal());
            seedTotal.level(pass.fixedSeed());
            firingTotal.lower(pass.firingLowerBound());
            firingTotal.weight(BigDecimal.ONE);
        } else {
            throw new IllegalStateException("Unknown Trinity MIP pass");
        }
        return new ModelData(
                model,
                List.copyOf(allVariables),
                firingVariables,
                seedVariables,
                externalVariables);
    }

    private static Expression expression(ExpressionsBasedModel model,
                                         String name,
                                         Iterable<Variable> variables) {
        Expression expression = model.addExpression(name);
        for (Variable variable : variables) {
            expression.set(variable, BigInteger.ONE);
        }
        return expression;
    }

    private static BigInteger minimumFirstInternalInput(
                                                        List<TrinityPatternVariant> variants,
                                                        Set<AEKey> internalKeys) {
        return variants.stream()
                .map(variant -> variant.inputs().entrySet().stream()
                        .filter(entry -> internalKeys.contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .reduce(BigInteger.ZERO, BigInteger::add))
                .filter(amount -> amount.signum() > 0)
                .min(BigInteger::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("A Trinity cycle variant must consume at least one internal key"));
    }

    private static BigInteger minimumFirstExternalInput(
                                                        List<TrinityPatternVariant> variants,
                                                        Set<AEKey> internalKeys) {
        return variants.stream()
                .map(variant -> variant.inputs().entrySet().stream()
                        .filter(entry -> !internalKeys.contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .reduce(BigInteger.ZERO, BigInteger::add))
                .min(BigInteger::compareTo)
                .orElse(BigInteger.ZERO);
    }

    private static Set<AEKey> externalInputKeys(
                                                List<TrinityPatternVariant> variants,
                                                Set<AEKey> internalKeys) {
        LinkedHashSet<AEKey> externalKeys = new LinkedHashSet<>();
        for (TrinityPatternVariant variant : variants) {
            for (AEKey key : variant.inputs().keySet()) {
                if (!internalKeys.contains(key)) {
                    externalKeys.add(key);
                }
            }
        }
        return Collections.unmodifiableSet(externalKeys);
    }

    private static BigInteger requiredTargetNet(AEKey target,
                                                BigInteger requestedAmount,
                                                CraftingQuantityMode quantityMode,
                                                Map<AEKey, BigInteger> available) {
        if (quantityMode == CraftingQuantityMode.NET_NEW) {
            return requestedAmount;
        }
        return requestedAmount
                .subtract(available.getOrDefault(target, BigInteger.ZERO))
                .max(BigInteger.ZERO)
                .max(BigInteger.ONE);
    }

    private static Map<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity MIP inventory cannot be negative or null");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Set<AEKey> copyKeys(Set<AEKey> source) {
        LinkedHashSet<AEKey> copied = new LinkedHashSet<>();
        for (AEKey key : source) {
            if (key == null) {
                throw new IllegalArgumentException("A Trinity producible input key cannot be null");
            }
            copied.add(key);
        }
        return Collections.unmodifiableSet(copied);
    }

    private static BigInteger sum(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
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

    private static <T> TrinityAlgorithmResult<T> inexactResult(String constraint, String value) {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                "An integral ojAlgo result violates exact Trinity constraints",
                Map.of("constraint", constraint, "value", value));
    }

    private sealed interface ModelPass permits ExternalInputPass, SeedPass, FiringPass {}

    private enum ExternalInputPass implements ModelPass {
        INSTANCE
    }

    private record SeedPass(BigInteger fixedExternal, BigInteger seedLowerBound) implements ModelPass {

        private SeedPass {
            if (fixedExternal == null || seedLowerBound == null) {
                throw new IllegalArgumentException("A Trinity seed pass requires explicit bounds");
            }
        }
    }

    private record FiringPass(
                              BigInteger fixedExternal,
                              BigInteger fixedSeed,
                              BigInteger firingLowerBound)
            implements ModelPass {

        private FiringPass {
            if (fixedExternal == null || fixedSeed == null || firingLowerBound == null) {
                throw new IllegalArgumentException("A Trinity firing pass requires explicit bounds");
            }
        }
    }

    private record ModelRequest(
                                List<TrinityPatternVariant> variants,
                                Set<AEKey> internalKeys,
                                AEKey target,
                                BigInteger requestedAmount,
                                BigInteger requiredTargetNet,
                                CraftingQuantityMode quantityMode,
                                Map<AEKey, BigInteger> available,
                                Set<AEKey> producibleInputs,
                                ModelPass pass) {

        private ModelRequest {
            if (pass == null) {
                throw new IllegalArgumentException("A Trinity MIP request requires an explicit pass");
            }
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
            for (int index = 0; index < this.variables.size(); index++) {
                byVariable.put(this.variables.get(index), values.get(index));
            }
            LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
            this.firingVariables.forEach((variant, variable) -> {
                BigInteger count = byVariable.get(variable);
                if (count.signum() > 0) {
                    firings.put(variant, count);
                }
            });
            Map<AEKey, BigInteger> seed = positiveValues(this.seedVariables, byVariable);
            Map<AEKey, BigInteger> external = positiveValues(this.externalVariables, byVariable);
            return new SolvedModel(firings, seed, external);
        }

        private static Map<AEKey, BigInteger> positiveValues(
                                                             Map<AEKey, Variable> variables,
                                                             Map<Variable, BigInteger> values) {
            LinkedHashMap<AEKey, BigInteger> positive = new LinkedHashMap<>();
            variables.forEach((key, variable) -> {
                BigInteger value = values.get(variable);
                if (value.signum() > 0) {
                    positive.put(key, value);
                }
            });
            return Collections.unmodifiableMap(positive);
        }
    }

    private record SolvedModel(
                               Map<TrinityPatternVariant, BigInteger> firings,
                               Map<AEKey, BigInteger> modelSeed,
                               Map<AEKey, BigInteger> externalInputs) {}

    private final class CandidateSearch {

        private final List<TrinityPatternVariant> variants;
        private final Set<AEKey> internalKeys;
        private final AEKey target;
        private final BigInteger requestedAmount;
        private final BigInteger requiredTargetNet;
        private final CraftingQuantityMode quantityMode;
        private final Map<AEKey, BigInteger> available;
        private final Set<AEKey> producibleInputs;
        private final Set<AEKey> externalKeys;
        private final BigInteger optimalExternal;
        private final BigInteger optimalSeed;
        private final BigInteger optimalFirings;
        private final SearchBudget budget;
        private final TrinityPlanningControl control;
        private final SolverMetrics metrics;
        private Optional<TrinityMipCyclePlan> best = Optional.empty();
        private Optional<TrinityAlgorithmResult<TrinityMipCyclePlan>> terminal = Optional.empty();

        private CandidateSearch(
                                List<TrinityPatternVariant> variants,
                                Set<AEKey> internalKeys,
                                AEKey target,
                                BigInteger requestedAmount,
                                BigInteger requiredTargetNet,
                                CraftingQuantityMode quantityMode,
                                Map<AEKey, BigInteger> available,
                                Set<AEKey> producibleInputs,
                                BigInteger optimalExternal,
                                BigInteger optimalSeed,
                                BigInteger optimalFirings,
                                SearchBudget budget,
                                TrinityPlanningControl control,
                                SolverMetrics metrics) {
            this.variants = variants;
            this.internalKeys = internalKeys;
            this.target = target;
            this.requestedAmount = requestedAmount;
            this.requiredTargetNet = requiredTargetNet;
            this.quantityMode = quantityMode;
            this.available = available;
            this.producibleInputs = producibleInputs;
            this.externalKeys = externalInputKeys(variants, internalKeys);
            this.optimalExternal = optimalExternal;
            this.optimalSeed = optimalSeed;
            this.optimalFirings = optimalFirings;
            this.budget = budget;
            this.control = control;
            this.metrics = metrics;
        }

        private TrinityAlgorithmResult<TrinityMipCyclePlan> search() {
            BigInteger[] counts = new BigInteger[this.variants.size()];
            enumerate(0, this.optimalFirings, counts);
            if (this.terminal.isPresent()) {
                return this.terminal.orElseThrow();
            }
            if (this.best.isPresent()) {
                return TrinityAlgorithmResult.success(this.best.orElseThrow());
            }
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                    "No schedule reaches the exact seed lower bound at this firing count",
                    Map.of(
                            "firings", this.optimalFirings.toString(),
                            "seed", this.optimalSeed.toString()));
        }

        private void enumerate(int index, BigInteger remaining, BigInteger[] counts) {
            if (this.terminal.isPresent() || hasProvenMinimumSeed()) {
                return;
            }
            if (this.control.cancellationRequested()) {
                this.terminal = Optional.of(failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        "Trinity MIP candidate search was cancelled",
                        Map.of("states", Integer.toString(this.budget.used))));
                return;
            }
            if (this.control.deadlineExceeded()) {
                this.terminal = Optional.of(failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        "Trinity MIP candidate search exhausted its deadline",
                        Map.of("states", Integer.toString(this.budget.used))));
                return;
            }
            if (index == counts.length - 1) {
                counts[index] = remaining;
                evaluate(counts);
                return;
            }
            BigInteger count = remaining;
            while (count.signum() >= 0 && this.terminal.isEmpty() && !hasProvenMinimumSeed()) {
                counts[index] = count;
                enumerate(index + 1, remaining.subtract(count), counts);
                count = count.subtract(BigInteger.ONE);
            }
        }

        private void evaluate(BigInteger[] counts) {
            if (!this.budget.consume(1)) {
                this.terminal = Optional.of(searchLimit(this.budget));
                return;
            }
            LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
            for (int index = 0; index < counts.length; index++) {
                if (counts[index].signum() > 0) {
                    firings.put(this.variants.get(index), counts[index]);
                }
            }
            Optional<CandidateAccounting> candidateAccounting = accountCandidate(
                    firings,
                    this.internalKeys,
                    this.target,
                    this.requestedAmount,
                    this.requiredTargetNet,
                    this.quantityMode);
            if (candidateAccounting.isEmpty()) {
                return;
            }
            CandidateAccounting accounting = candidateAccounting.orElseThrow();
            if (sum(accounting.externalInputs()).compareTo(this.optimalExternal) > 0 ||
                    sum(accounting.requiredModelSeed()).compareTo(this.optimalSeed) > 0 ||
                    exceedsAvailable(accounting.externalInputs(), this.available, this.producibleInputs) ||
                    exceedsAvailable(accounting.requiredModelSeed(), this.available, this.producibleInputs)) {
                return;
            }

            Map<AEKey, BigInteger> maximumInputs = candidateInputBounds(
                    firings,
                    this.externalKeys,
                    this.internalKeys,
                    accounting,
                    this.available,
                    this.producibleInputs,
                    this.optimalExternal,
                    this.optimalSeed);
            int remainingStates = this.budget.remaining();
            if (remainingStates <= 0) {
                this.terminal = Optional.of(searchLimit(this.budget));
                return;
            }
            LinkedHashMap<AEKey, BigInteger> minimumInputs = new LinkedHashMap<>(
                    accounting.externalInputs());
            accounting.requiredModelSeed().forEach(
                    (key, amount) -> minimumInputs.merge(key, amount, BigInteger::max));
            TrinityAlgorithmResult<TrinityMinimumSeedSchedule> seeded = seedScheduler.find(
                    firings,
                    this.externalKeys,
                    this.internalKeys,
                    minimumInputs,
                    maximumInputs,
                    remainingStates,
                    this.control);
            if (!seeded.successful()) {
                if (seeded.diagnostic().code() == TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER) {
                    this.budget.consume(diagnosticStates(seeded.diagnostic()));
                    return;
                }
                this.terminal = Optional.of(TrinityAlgorithmResult.failure(seeded.diagnostic()));
                return;
            }
            if (!this.budget.consume(seeded.value().schedule().statesVisited())) {
                this.terminal = Optional.of(searchLimit(this.budget));
                return;
            }

            LinkedHashMap<AEKey, BigInteger> externalInitial = maximumAmounts(
                    accounting.externalInputs(),
                    seeded.value().externalInputs());
            BigInteger externalSlack = this.optimalExternal.subtract(sum(externalInitial));
            if (externalSlack.signum() < 0 ||
                    hasUnfilledSlack(externalInitial, externalSlack, this.externalKeys, maximumInputs)) {
                return;
            }
            LinkedHashMap<AEKey, BigInteger> internalInitial = maximumAmounts(
                    accounting.requiredModelSeed(),
                    seeded.value().minimumSeed());
            BigInteger requiredSlack = this.optimalSeed.subtract(sum(internalInitial));
            if (requiredSlack.signum() > 0 &&
                    hasUnfilledSlack(internalInitial, requiredSlack, this.internalKeys, maximumInputs)) {
                return;
            }
            LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>(externalInitial);
            internalInitial.forEach((key, amount) -> initialInputs.merge(key, amount, BigInteger::add));
            Map<AEKey, BigInteger> finiteUpperBounds = finiteInputUpperBounds(
                    this.variants,
                    this.internalKeys,
                    this.available,
                    this.producibleInputs);
            Map<AEKey, BigInteger> finalLowerBounds = this.quantityMode == CraftingQuantityMode.FINAL_TOTAL ?
                    Map.of(this.target, this.requestedAmount) :
                    Map.of();
            TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = conservationVerifier.verify(
                    this.variants,
                    firings,
                    initialInputs,
                    finiteUpperBounds,
                    finalLowerBounds,
                    this.target,
                    this.requiredTargetNet);
            if (!exact.successful()) {
                this.terminal = Optional.of(TrinityAlgorithmResult.failure(exact.diagnostic()));
                return;
            }
            Map<AEKey, BigInteger> finalBalances = addSigned(initialInputs, accounting.netChange());
            TrinityCompressedSchedule adjustedSchedule = new TrinityCompressedSchedule(
                    seeded.value().schedule().batches(),
                    finalBalances,
                    seeded.value().schedule().statesVisited());
            TrinityMipCyclePlan candidate = new TrinityMipCyclePlan(
                    firings,
                    externalInitial,
                    seeded.value().minimumSeed(),
                    initialInputs,
                    accounting.netChange(),
                    adjustedSchedule,
                    this.metrics.passes,
                    this.metrics.nanos);
            if (this.best.isEmpty() || seedTotal(candidate).compareTo(seedTotal(this.best.orElseThrow())) < 0) {
                this.best = Optional.of(candidate);
            }
        }

        private boolean hasProvenMinimumSeed() {
            return this.best.isPresent() && seedTotal(this.best.orElseThrow()).equals(this.optimalSeed);
        }

        private static BigInteger seedTotal(TrinityMipCyclePlan plan) {
            return sum(plan.minimumSeed());
        }
    }

    private static Optional<CandidateAccounting> accountCandidate(
                                                                  Map<TrinityPatternVariant, BigInteger> firings,
                                                                  Set<AEKey> internalKeys,
                                                                  AEKey target,
                                                                  BigInteger requestedAmount,
                                                                  BigInteger requiredTargetNet,
                                                                  CraftingQuantityMode quantityMode) {
        if (firings.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (net.getOrDefault(target, BigInteger.ZERO).compareTo(requiredTargetNet) < 0) {
            return Optional.empty();
        }
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>(net.keySet());
        keys.addAll(internalKeys);
        LinkedHashMap<AEKey, BigInteger> external = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> modelSeed = new LinkedHashMap<>();
        for (AEKey key : keys) {
            BigInteger finalLower = quantityMode == CraftingQuantityMode.FINAL_TOTAL && key.equals(target) ?
                    requestedAmount :
                    BigInteger.ZERO;
            BigInteger required = finalLower.subtract(net.getOrDefault(key, BigInteger.ZERO)).max(BigInteger.ZERO);
            if (required.signum() > 0) {
                if (internalKeys.contains(key)) {
                    modelSeed.put(key, required);
                } else {
                    external.put(key, required);
                }
            }
        }
        return Optional.of(new CandidateAccounting(
                Collections.unmodifiableMap(external),
                Collections.unmodifiableMap(modelSeed),
                Collections.unmodifiableMap(net)));
    }

    private static LinkedHashMap<AEKey, BigInteger> maximumAmounts(
                                                                   Map<AEKey, BigInteger> first,
                                                                   Map<AEKey, BigInteger> second) {
        LinkedHashMap<AEKey, BigInteger> maximum = new LinkedHashMap<>(first);
        second.forEach((key, amount) -> maximum.merge(key, amount, BigInteger::max));
        return maximum;
    }

    private static Map<AEKey, BigInteger> finiteInputUpperBounds(
                                                                 List<TrinityPatternVariant> variants,
                                                                 Set<AEKey> internalKeys,
                                                                 Map<AEKey, BigInteger> available,
                                                                 Set<AEKey> producibleInputs) {
        LinkedHashSet<AEKey> inputKeys = new LinkedHashSet<>(internalKeys);
        variants.forEach(variant -> inputKeys.addAll(variant.inputs().keySet()));
        LinkedHashMap<AEKey, BigInteger> bounds = new LinkedHashMap<>();
        for (AEKey key : inputKeys) {
            if (!producibleInputs.contains(key)) {
                bounds.put(key, available.getOrDefault(key, BigInteger.ZERO));
            }
        }
        return Collections.unmodifiableMap(bounds);
    }

    private static Map<AEKey, BigInteger> candidateInputBounds(
                                                               Map<TrinityPatternVariant, BigInteger> firings,
                                                               Set<AEKey> externalKeys,
                                                               Set<AEKey> internalKeys,
                                                               CandidateAccounting accounting,
                                                               Map<AEKey, BigInteger> available,
                                                               Set<AEKey> producibleInputs,
                                                               BigInteger optimalExternal,
                                                               BigInteger optimalSeed) {
        LinkedHashSet<AEKey> injectableKeys = new LinkedHashSet<>(externalKeys);
        firings.keySet().forEach(variant -> variant.inputs().keySet().stream()
                .filter(internalKeys::contains)
                .forEach(injectableKeys::add));
        injectableKeys.addAll(accounting.requiredModelSeed().keySet());

        LinkedHashMap<AEKey, BigInteger> totalConsumption = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.inputs().forEach(
                (key, amount) -> totalConsumption.merge(key, amount.multiply(count), BigInteger::add)));
        LinkedHashMap<AEKey, BigInteger> bounds = new LinkedHashMap<>();
        for (AEKey key : injectableKeys) {
            BigInteger bound = available.getOrDefault(key, BigInteger.ZERO);
            if (producibleInputs.contains(key)) {
                bound = bound.max(totalConsumption.getOrDefault(key, BigInteger.ZERO));
                bound = bound.max(accounting.externalInputs().getOrDefault(key, BigInteger.ZERO));
                bound = bound.max(accounting.requiredModelSeed().getOrDefault(key, BigInteger.ZERO));
                bound = bound.max(externalKeys.contains(key) ? optimalExternal : optimalSeed);
            }
            bounds.put(key, bound);
        }
        return Collections.unmodifiableMap(bounds);
    }

    private static boolean hasUnfilledSlack(
                                            Map<AEKey, BigInteger> amounts,
                                            BigInteger slack,
                                            Set<AEKey> eligibleKeys,
                                            Map<AEKey, BigInteger> maximumInputs) {
        BigInteger remaining = slack;
        for (AEKey key : eligibleKeys) {
            BigInteger current = amounts.getOrDefault(key, BigInteger.ZERO);
            BigInteger capacity = maximumInputs.getOrDefault(key, BigInteger.ZERO)
                    .subtract(current)
                    .max(BigInteger.ZERO);
            BigInteger added = remaining.min(capacity);
            if (added.signum() > 0) {
                amounts.put(key, current.add(added));
                remaining = remaining.subtract(added);
            }
            if (remaining.signum() == 0) {
                return false;
            }
        }
        return remaining.signum() != 0;
    }

    private static boolean exceedsAvailable(Map<AEKey, BigInteger> required,
                                            Map<AEKey, BigInteger> available,
                                            Set<AEKey> producibleInputs) {
        return required.entrySet().stream().anyMatch(entry -> !producibleInputs.contains(entry.getKey()) &&
                available.getOrDefault(entry.getKey(), BigInteger.ZERO).compareTo(entry.getValue()) < 0);
    }

    private static Map<AEKey, BigInteger> addSigned(Map<AEKey, BigInteger> initial,
                                                    Map<AEKey, BigInteger> change) {
        LinkedHashMap<AEKey, BigInteger> result = new LinkedHashMap<>(initial);
        change.forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
        if (result.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalStateException("An exact Trinity MIP candidate has a negative final balance");
        }
        result.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(result);
    }

    static int diagnosticStates(TrinityPlanningDiagnostic diagnostic) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("A Trinity schedule diagnostic is required");
        }
        String encodedStates = diagnostic.metadata().get("states");
        if (encodedStates == null) {
            throw new IllegalStateException("A Trinity schedule diagnostic must report visited states");
        }
        try {
            int states = Integer.parseInt(encodedStates);
            if (states < 0) {
                throw new IllegalStateException("Trinity schedule diagnostic states cannot be negative");
            }
            return states;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Trinity schedule diagnostic states must be an integer", exception);
        }
    }

    private static TrinityAlgorithmResult<TrinityMipCyclePlan> searchLimit(SearchBudget budget) {
        return failure(
                TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                "Trinity exact-seed candidate search exceeded its shared state limit",
                Map.of(
                        "limit", Integer.toString(budget.limit),
                        "states", Integer.toString(budget.used)));
    }

    private record CandidateAccounting(
                                       Map<AEKey, BigInteger> externalInputs,
                                       Map<AEKey, BigInteger> requiredModelSeed,
                                       Map<AEKey, BigInteger> netChange) {}

    private static final class SearchBudget {

        private final int limit;
        private int used;

        private SearchBudget(int limit) {
            this.limit = limit;
        }

        private boolean consume(int states) {
            if (states < 0) {
                throw new IllegalArgumentException("Trinity search states cannot be negative");
            }
            if (states > this.limit - this.used) {
                this.used = this.limit;
                return false;
            }
            this.used += states;
            return true;
        }

        private int remaining() {
            return this.limit - this.used;
        }
    }

    private static final class SolverMetrics {

        private int passes;
        private long nanos;
    }
}
