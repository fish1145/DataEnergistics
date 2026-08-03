package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Sequential lexicographic model: inventory units, firings, then stable variant identity.
 */
final class TrinityAcyclicRouteOptimizerImpl implements TrinityAcyclicRouteOptimizer {

    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String TIMEOUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.timeout";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.diagnostic.search_limit";
    private static final String INSUFFICIENT_INPUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.insufficient_input";
    private static final String INEXACT_RESULT_KEY = "gui.data_energistics.trinity_planning.diagnostic.inexact_result";
    private static final String NO_EXECUTABLE_ORDER_KEY = "gui.data_energistics.trinity_planning.diagnostic.no_executable_order";

    private final TrinityIntegerResultVerifier integerVerifier;
    private final TrinityExactConservationVerifier conservationVerifier;
    private final TrinityAcyclicRoutePruner routePruner;

    TrinityAcyclicRouteOptimizerImpl(TrinityIntegerResultVerifier integerVerifier,
                                     TrinityExactConservationVerifier conservationVerifier,
                                     TrinityAcyclicRoutePruner routePruner) {
        this.integerVerifier = integerVerifier;
        this.conservationVerifier = conservationVerifier;
        this.routePruner = routePruner;
    }

    @Override
    public TrinityAlgorithmResult<TrinityAcyclicPlan> optimize(
                                                               TrinityCraftingTopology topology,
                                                               List<TrinityPatternVariant> variants,
                                                               AEKey target,
                                                               BigInteger requestedAmount,
                                                               CraftingQuantityMode quantityMode,
                                                               Map<AEKey, BigInteger> available,
                                                               int maxSearchStates,
                                                               TrinityPlanningControl control) {
        if (topology == null || variants == null || target == null || requestedAmount == null ||
                requestedAmount.signum() <= 0 || quantityMode == null || available == null ||
                maxSearchStates <= 0 || control == null) {
            throw new IllegalArgumentException("A Trinity acyclic route optimization requires complete inputs");
        }
        List<TrinityPatternVariant> reachable = this.routePruner.retainExecutableTargetRoutes(
                variants,
                target,
                available);
        if (reachable.isEmpty()) {
            return insufficient(target, requestedAmount);
        }
        Map<AEKey, BigInteger> inventory = copyAvailable(available);
        BigInteger requiredTargetNet = requiredTargetNet(target, requestedAmount, quantityMode, inventory);
        SearchBudget budget = new SearchBudget(maxSearchStates);
        Optional<UniformBindingFamily> uniformBindings = UniformBindingFamily.tryCreate(reachable, target);
        if (uniformBindings.isPresent()) {
            return optimizeUniformBindings(
                    topology,
                    uniformBindings.get(),
                    target,
                    requestedAmount,
                    requiredTargetNet,
                    quantityMode,
                    inventory,
                    budget,
                    control);
        }

        TrinityAlgorithmResult<SolvedModel> externalResult = solve(
                new ModelRequest(
                        reachable,
                        target,
                        requestedAmount,
                        requiredTargetNet,
                        quantityMode,
                        inventory,
                        ExternalPass.INSTANCE),
                budget,
                control);
        if (!externalResult.successful()) {
            return TrinityAlgorithmResult.failure(externalResult.diagnostic());
        }
        BigInteger optimalExternal = sum(externalResult.value().reserves());

        TrinityAlgorithmResult<SolvedModel> firingResult = solve(
                new ModelRequest(
                        reachable,
                        target,
                        requestedAmount,
                        requiredTargetNet,
                        quantityMode,
                        inventory,
                        new FiringPass(optimalExternal)),
                budget,
                control);
        if (!firingResult.successful()) {
            return TrinityAlgorithmResult.failure(firingResult.diagnostic());
        }
        BigInteger optimalFirings = sum(firingResult.value().firings());
        SolvedModel selected = firingResult.value();
        LinkedHashMap<TrinityPatternVariant, BigInteger> fixedPrefix = new LinkedHashMap<>();
        Map<AEKey, BigInteger> sourceCapacity = sourceCapacity(reachable, inventory);
        LinkedHashMap<AEKey, BigInteger> fixedSourceConsumption = new LinkedHashMap<>();
        BigInteger remainingFirings = optimalFirings;
        for (int index = 0; index < reachable.size() - 1; index++) {
            TrinityPatternVariant preferred = reachable.get(index);
            if (remainingFirings.signum() == 0) {
                break;
            }
            BigInteger preferredCount = selected.firings().getOrDefault(preferred, BigInteger.ZERO);
            BigInteger provenUpper = sourceUpperBound(
                    preferred,
                    remainingFirings,
                    sourceCapacity,
                    fixedSourceConsumption);
            if (!preferredCount.equals(provenUpper)) {
                TrinityAlgorithmResult<SolvedModel> identityResult = solve(
                        new ModelRequest(
                                reachable,
                                target,
                                requestedAmount,
                                requiredTargetNet,
                                quantityMode,
                                inventory,
                                new IdentityPass(
                                        optimalExternal,
                                        optimalFirings,
                                        Collections.unmodifiableMap(new LinkedHashMap<>(fixedPrefix)),
                                        preferred)),
                        budget,
                        control);
                if (!identityResult.successful()) {
                    return TrinityAlgorithmResult.failure(identityResult.diagnostic());
                }
                selected = identityResult.value();
                preferredCount = selected.firings().getOrDefault(preferred, BigInteger.ZERO);
            }
            fixedPrefix.put(preferred, preferredCount);
            remainingFirings = remainingFirings.subtract(preferredCount);
            mergeSourceConsumption(fixedSourceConsumption, preferred, preferredCount, sourceCapacity.keySet());
        }
        return buildPlan(topology, selected, budget.used());
    }

    /**
     * Solves the common AE2 substitution shape without starting one MIP per stable binding.
     *
     * <p>
     * Every accepted variant is the same physical pattern, produces the same target amount and consumes the same
     * quantity of exactly one interchangeable input. External-input and firing objectives are therefore constant;
     * allocating available inputs in stable variant order is the exact lexicographic optimum.
     * </p>
     */
    private TrinityAlgorithmResult<TrinityAcyclicPlan> optimizeUniformBindings(
                                                                               TrinityCraftingTopology topology,
                                                                               UniformBindingFamily family,
                                                                               AEKey target,
                                                                               BigInteger requestedAmount,
                                                                               BigInteger requiredTargetNet,
                                                                               CraftingQuantityMode quantityMode,
                                                                               Map<AEKey, BigInteger> available,
                                                                               SearchBudget budget,
                                                                               TrinityPlanningControl control) {
        BigInteger requiredFirings = ceilDivide(requiredTargetNet, family.outputPerFiring());
        BigInteger remainingFirings = requiredFirings;
        LinkedHashMap<AEKey, BigInteger> remainingInventory = new LinkedHashMap<>(available);
        LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> reserves = new LinkedHashMap<>();
        BigInteger targetReserve = targetReserve(target, requestedAmount, quantityMode, available);
        if (targetReserve.signum() > 0) {
            reserves.put(target, targetReserve);
        }

        for (TrinityPatternVariant variant : family.variants()) {
            if (control.cancellationRequested()) {
                return failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        Map.of("states", Integer.toString(budget.used())));
            }
            if (control.deadlineExceeded()) {
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        TIMEOUT_KEY,
                        Map.of("states", Integer.toString(budget.used())));
            }
            if (!budget.consume()) {
                return failure(
                        TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                        SEARCH_LIMIT_KEY,
                        Map.of("states", Integer.toString(budget.used())));
            }

            Map.Entry<AEKey, BigInteger> input = variant.inputs().entrySet().iterator().next();
            BigInteger availableInput = remainingInventory.getOrDefault(input.getKey(), BigInteger.ZERO);
            BigInteger selectedFirings = remainingFirings.min(availableInput.divide(family.inputPerFiring()));
            if (selectedFirings.signum() > 0) {
                BigInteger consumed = family.inputPerFiring().multiply(selectedFirings);
                firings.put(variant, selectedFirings);
                reserves.merge(input.getKey(), consumed, BigInteger::add);
                remainingInventory.put(input.getKey(), availableInput.subtract(consumed));
                remainingFirings = remainingFirings.subtract(selectedFirings);
            }
            if (remainingFirings.signum() == 0) {
                break;
            }
        }
        if (remainingFirings.signum() > 0) {
            return failure(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    INSUFFICIENT_INPUT_KEY,
                    Map.of(
                            "requiredFirings", requiredFirings.toString(),
                            "availableFirings", requiredFirings.subtract(remainingFirings).toString()));
        }

        ModelRequest verificationRequest = new ModelRequest(
                family.variants(),
                target,
                requestedAmount,
                requiredTargetNet,
                quantityMode,
                available,
                ExternalPass.INSTANCE);
        SolvedModel selected = new SolvedModel(
                Collections.unmodifiableMap(new LinkedHashMap<>(firings)),
                Collections.unmodifiableMap(new LinkedHashMap<>(reserves)),
                Map.of());
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verify(verificationRequest, selected);
        if (!exact.successful()) {
            return TrinityAlgorithmResult.failure(exact.diagnostic());
        }
        return buildPlan(
                topology,
                new SolvedModel(selected.firings(), selected.reserves(), exact.value()),
                budget.used());
    }

    private TrinityAlgorithmResult<SolvedModel> solve(
                                                      ModelRequest request,
                                                      SearchBudget budget,
                                                      TrinityPlanningControl control) {
        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    CANCELLED_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }
        if (control.deadlineExceeded()) {
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    TIMEOUT_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }
        if (!budget.consume()) {
            return failure(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    SEARCH_LIMIT_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }

        ModelData data = createModel(request);
        configureDeadline(data.model(), control);
        Optimisation.Result result = request.pass() instanceof IdentityPass ?
                data.model().maximise() :
                data.model().minimise();

        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    CANCELLED_KEY,
                    Map.of("states", Integer.toString(budget.used())));
        }
        if (!result.getState().isOptimal()) {
            if (control.deadlineExceeded() || result.getState().isFeasible()) {
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        TIMEOUT_KEY,
                        Map.of("state", result.getState().name()));
            }
            if (result.getState() == Optimisation.State.INFEASIBLE) {
                return failure(
                        TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                        INSUFFICIENT_INPUT_KEY,
                        Map.of("state", result.getState().name()));
            }
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                    INEXACT_RESULT_KEY,
                    Map.of("state", result.getState().name()));
        }

        ArrayList<BigDecimal> values = new ArrayList<>(data.variables().size());
        for (Variable variable : data.variables()) {
            values.add(result.get(data.model().indexOf(variable)));
        }
        TrinityAlgorithmResult<List<BigInteger>> integers = this.integerVerifier.verify(
                values,
                data.model().options.integer().getIntegralityTolerance());
        if (!integers.successful()) {
            return TrinityAlgorithmResult.failure(integers.diagnostic());
        }
        if (integers.value().stream().anyMatch(value -> value.signum() < 0)) {
            return inexact("variable_lower", "negative");
        }
        SolvedModel solved = data.decode(integers.value());
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = verify(request, solved);
        if (!exact.successful()) {
            return TrinityAlgorithmResult.failure(exact.diagnostic());
        }
        return TrinityAlgorithmResult.success(new SolvedModel(
                solved.firings(),
                solved.reserves(),
                exact.value()));
    }

    private TrinityAlgorithmResult<Map<AEKey, BigInteger>> verify(
                                                                  ModelRequest request,
                                                                  SolvedModel solved) {
        BigInteger expectedTargetReserve = targetReserve(
                request.target(),
                request.requestedAmount(),
                request.quantityMode(),
                request.available());
        BigInteger actualTargetReserve = solved.reserves().getOrDefault(request.target(), BigInteger.ZERO);
        if (actualTargetReserve.compareTo(expectedTargetReserve) != 0) {
            return inexact("target_reserve", actualTargetReserve + "!=" + expectedTargetReserve);
        }
        LinkedHashMap<AEKey, BigInteger> upperBounds = new LinkedHashMap<>();
        touchedKeys(request.variants(), request.target()).forEach(key -> upperBounds.put(
                key,
                request.quantityMode() == CraftingQuantityMode.NET_NEW && key.equals(request.target()) ?
                        BigInteger.ZERO :
                        request.available().getOrDefault(key, BigInteger.ZERO)));
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = this.conservationVerifier.verify(
                request.variants(),
                solved.firings(),
                solved.reserves(),
                upperBounds,
                Map.of(request.target(), request.requestedAmount()),
                request.target(),
                request.requiredTargetNet());
        if (!exact.successful()) {
            return exact;
        }

        BigInteger external = sum(solved.reserves());
        BigInteger firings = sum(solved.firings());
        if (request.pass() instanceof FiringPass pass &&
                external.compareTo(pass.fixedExternal()) != 0) {
            return inexact("external_level", external + "!=" + pass.fixedExternal());
        }
        if (request.pass() instanceof IdentityPass pass) {
            if (external.compareTo(pass.fixedExternal()) != 0) {
                return inexact("external_level", external + "!=" + pass.fixedExternal());
            }
            if (firings.compareTo(pass.fixedFirings()) != 0) {
                return inexact("firing_level", firings + "!=" + pass.fixedFirings());
            }
            for (Map.Entry<TrinityPatternVariant, BigInteger> fixed : pass.fixedPrefix().entrySet()) {
                BigInteger actual = solved.firings().getOrDefault(fixed.getKey(), BigInteger.ZERO);
                if (actual.compareTo(fixed.getValue()) != 0) {
                    return inexact("identity_level", actual + "!=" + fixed.getValue());
                }
            }
        }
        return exact;
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

    private static ModelData createModel(ModelRequest request) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        ArrayList<Variable> variables = new ArrayList<>();
        LinkedHashMap<TrinityPatternVariant, Variable> firingVariables = new LinkedHashMap<>();
        for (int index = 0; index < request.variants().size(); index++) {
            Variable variable = model.addVariable("firing_" + index)
                    .integer()
                    .lower(BigInteger.ZERO);
            firingVariables.put(request.variants().get(index), variable);
            variables.add(variable);
        }

        LinkedHashMap<AEKey, Variable> reserveVariables = new LinkedHashMap<>();
        int reserveIndex = 0;
        for (AEKey key : touchedKeys(request.variants(), request.target())) {
            BigInteger upper = request.available().getOrDefault(key, BigInteger.ZERO);
            Variable variable = model.addVariable("reserve_" + reserveIndex++)
                    .integer()
                    .lower(BigInteger.ZERO);
            if (key.equals(request.target())) {
                BigInteger targetReserve = targetReserve(
                        request.target(),
                        request.requestedAmount(),
                        request.quantityMode(),
                        request.available());
                variable.level(targetReserve);
            } else {
                variable.upper(upper);
            }
            reserveVariables.put(key, variable);
            variables.add(variable);
        }

        int conservationIndex = 0;
        for (Map.Entry<AEKey, Variable> reserve : reserveVariables.entrySet()) {
            AEKey key = reserve.getKey();
            Expression conservation = model.addExpression("conservation_" + conservationIndex++);
            firingVariables.forEach((variant, variable) -> {
                BigInteger coefficient = variant.netChange().getOrDefault(key, BigInteger.ZERO);
                if (coefficient.signum() != 0) {
                    conservation.set(variable, coefficient);
                }
            });
            conservation.set(reserve.getValue(), BigInteger.ONE);
            conservation.lower(key.equals(request.target()) ? request.requestedAmount() : BigInteger.ZERO);
        }

        Expression targetNet = model.addExpression("target_net");
        firingVariables.forEach((variant, variable) -> {
            BigInteger coefficient = variant.netChange().getOrDefault(request.target(), BigInteger.ZERO);
            if (coefficient.signum() != 0) {
                targetNet.set(variable, coefficient);
            }
        });
        targetNet.lower(request.requiredTargetNet());

        Expression externalTotal = expression(model, "external_total", reserveVariables.values());
        Expression firingTotal = expression(model, "firing_total", firingVariables.values());
        if (request.pass() instanceof ExternalPass) {
            externalTotal.weight(BigDecimal.ONE);
        } else if (request.pass() instanceof FiringPass pass) {
            externalTotal.level(pass.fixedExternal());
            firingTotal.weight(BigDecimal.ONE);
        } else if (request.pass() instanceof IdentityPass pass) {
            externalTotal.level(pass.fixedExternal());
            firingTotal.level(pass.fixedFirings());
            pass.fixedPrefix().forEach((variant, value) -> firingVariables.get(variant).level(value));
            firingVariables.get(pass.preferred()).weight(BigDecimal.ONE);
        } else {
            throw new IllegalStateException("Unknown Trinity acyclic optimization pass");
        }
        return new ModelData(model, List.copyOf(variables), firingVariables, reserveVariables);
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

    private static TrinityAlgorithmResult<TrinityAcyclicPlan> buildPlan(
                                                                        TrinityCraftingTopology topology,
                                                                        SolvedModel solved,
                                                                        int states) {
        Map<Integer, Integer> positions = topologicalPositions(topology);
        ArrayList<TrinityVariantFiring> executionOrder = new ArrayList<>();
        solved.firings().entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<TrinityPatternVariant, BigInteger> entry) -> producerPosition(
                                topology,
                                positions,
                                entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> executionOrder.add(new TrinityVariantFiring(entry.getKey(), entry.getValue())));
        LinkedHashMap<TrinityPatternVariant, BigInteger> orderedFirings = new LinkedHashMap<>();
        executionOrder.forEach(firing -> orderedFirings.put(firing.variant(), firing.count()));
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> executable = verifyExecutionPrefix(
                executionOrder,
                solved.reserves());
        if (!executable.successful()) {
            return TrinityAlgorithmResult.failure(executable.diagnostic());
        }
        return TrinityAlgorithmResult.success(new TrinityAcyclicPlan(
                executionOrder,
                orderedFirings,
                solved.reserves(),
                solved.netChange(),
                states));
    }

    private static Set<AEKey> touchedKeys(List<TrinityPatternVariant> variants, AEKey target) {
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
        keys.add(target);
        variants.forEach(variant -> {
            keys.addAll(variant.inputs().keySet());
            keys.addAll(variant.outputs().keySet());
        });
        return Collections.unmodifiableSet(keys);
    }

    private static Map<AEKey, BigInteger> sourceCapacity(
                                                         List<TrinityPatternVariant> variants,
                                                         Map<AEKey, BigInteger> available) {
        LinkedHashSet<AEKey> produced = new LinkedHashSet<>();
        variants.forEach(variant -> produced.addAll(variant.outputs().keySet()));
        LinkedHashMap<AEKey, BigInteger> capacity = new LinkedHashMap<>();
        variants.forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !produced.contains(key))
                .forEach(key -> capacity.putIfAbsent(key, available.getOrDefault(key, BigInteger.ZERO))));
        return Collections.unmodifiableMap(capacity);
    }

    private static BigInteger sourceUpperBound(
                                               TrinityPatternVariant variant,
                                               BigInteger remainingFirings,
                                               Map<AEKey, BigInteger> sourceCapacity,
                                               Map<AEKey, BigInteger> fixedSourceConsumption) {
        BigInteger upper = remainingFirings;
        for (Map.Entry<AEKey, BigInteger> input : variant.inputs().entrySet()) {
            BigInteger capacity = sourceCapacity.get(input.getKey());
            if (capacity == null) {
                continue;
            }
            BigInteger remaining = capacity.subtract(
                    fixedSourceConsumption.getOrDefault(input.getKey(), BigInteger.ZERO));
            upper = upper.min(remaining.max(BigInteger.ZERO).divide(input.getValue()));
        }
        return upper;
    }

    private static void mergeSourceConsumption(
                                               Map<AEKey, BigInteger> consumption,
                                               TrinityPatternVariant variant,
                                               BigInteger count,
                                               Set<AEKey> sourceKeys) {
        if (count.signum() == 0) {
            return;
        }
        variant.inputs().forEach((key, amount) -> {
            if (sourceKeys.contains(key)) {
                consumption.merge(key, amount.multiply(count), BigInteger::add);
            }
        });
    }

    private static Map<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity acyclic inventory cannot be negative or null");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(copied);
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

    private static BigInteger targetReserve(AEKey target,
                                            BigInteger requestedAmount,
                                            CraftingQuantityMode quantityMode,
                                            Map<AEKey, BigInteger> available) {
        return quantityMode == CraftingQuantityMode.NET_NEW ?
                BigInteger.ZERO :
                requestedAmount.min(available.getOrDefault(target, BigInteger.ZERO));
    }

    private static TrinityAlgorithmResult<Map<AEKey, BigInteger>> verifyExecutionPrefix(
                                                                                        List<TrinityVariantFiring> executionOrder,
                                                                                        Map<AEKey, BigInteger> reserves) {
        LinkedHashMap<AEKey, BigInteger> balance = new LinkedHashMap<>(reserves);
        for (TrinityVariantFiring firing : executionOrder) {
            for (Map.Entry<AEKey, BigInteger> input : firing.variant().inputs().entrySet()) {
                BigInteger required = input.getValue().multiply(firing.count());
                BigInteger present = balance.getOrDefault(input.getKey(), BigInteger.ZERO);
                if (present.compareTo(required) < 0) {
                    return failure(
                            TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                            NO_EXECUTABLE_ORDER_KEY,
                            Map.of(
                                    "key", input.getKey().toString(),
                                    "required", required.toString(),
                                    "available", present.toString()));
                }
                balance.put(input.getKey(), present.subtract(required));
            }
            firing.variant().outputs().forEach((key, amount) -> balance.merge(
                    key,
                    amount.multiply(firing.count()),
                    BigInteger::add));
        }
        balance.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return TrinityAlgorithmResult.success(Collections.unmodifiableMap(balance));
    }

    private static int producerPosition(TrinityCraftingTopology topology,
                                        Map<Integer, Integer> positions,
                                        TrinityPatternVariant variant) {
        int earliestOutput = Integer.MAX_VALUE;
        for (AEKey output : variant.outputs().keySet()) {
            Integer component = topology.componentByKey().get(output);
            if (component != null) {
                earliestOutput = Math.min(earliestOutput, positions.get(component));
            }
        }
        if (earliestOutput == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A Trinity acyclic firing output is absent from topology");
        }
        return earliestOutput;
    }

    private static Map<Integer, Integer> topologicalPositions(TrinityCraftingTopology topology) {
        HashMap<Integer, Integer> positions = new HashMap<>();
        for (int index = 0; index < topology.topologicalOrder().size(); index++) {
            positions.put(topology.topologicalOrder().get(index), index);
        }
        return positions;
    }

    private static BigInteger sum(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    private static <T> TrinityAlgorithmResult<T> insufficient(
                                                              AEKey key,
                                                              BigInteger required) {
        return failure(
                TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                INSUFFICIENT_INPUT_KEY,
                Map.of(
                        "key", key.toString(),
                        "required", required.toString(),
                        "available", "0"));
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

    private sealed interface ModelPass permits ExternalPass, FiringPass, IdentityPass {}

    private enum ExternalPass implements ModelPass {
        INSTANCE
    }

    private record FiringPass(BigInteger fixedExternal) implements ModelPass {}

    private record IdentityPass(
                                BigInteger fixedExternal,
                                BigInteger fixedFirings,
                                Map<TrinityPatternVariant, BigInteger> fixedPrefix,
                                TrinityPatternVariant preferred)
            implements ModelPass {}

    private record ModelRequest(
                                List<TrinityPatternVariant> variants,
                                AEKey target,
                                BigInteger requestedAmount,
                                BigInteger requiredTargetNet,
                                CraftingQuantityMode quantityMode,
                                Map<AEKey, BigInteger> available,
                                ModelPass pass) {}

    private record ModelData(
                             ExpressionsBasedModel model,
                             List<Variable> variables,
                             Map<TrinityPatternVariant, Variable> firingVariables,
                             Map<AEKey, Variable> reserveVariables) {

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
            LinkedHashMap<AEKey, BigInteger> reserves = new LinkedHashMap<>();
            this.reserveVariables.forEach((key, variable) -> {
                BigInteger amount = byVariable.get(variable);
                if (amount.signum() > 0) {
                    reserves.put(key, amount);
                }
            });
            return new SolvedModel(
                    Collections.unmodifiableMap(firings),
                    Collections.unmodifiableMap(reserves),
                    Map.of());
        }
    }

    private record SolvedModel(
                               Map<TrinityPatternVariant, BigInteger> firings,
                               Map<AEKey, BigInteger> reserves,
                               Map<AEKey, BigInteger> netChange) {}

    private record UniformBindingFamily(
                                        List<TrinityPatternVariant> variants,
                                        BigInteger inputPerFiring,
                                        BigInteger outputPerFiring) {

        private static Optional<UniformBindingFamily> tryCreate(
                                                                List<TrinityPatternVariant> variants,
                                                                AEKey target) {
            TrinityPatternVariant first = variants.getFirst();
            if (first.inputs().size() != 1 || first.outputs().size() != 1 ||
                    !first.outputs().containsKey(target) || first.inputs().containsKey(target)) {
                return Optional.empty();
            }
            BigInteger inputPerFiring = first.inputs().values().iterator().next();
            BigInteger outputPerFiring = first.outputs().get(target);
            for (TrinityPatternVariant variant : variants) {
                if (!variant.patternIdentity().equals(first.patternIdentity()) ||
                        variant.inputs().size() != 1 || variant.outputs().size() != 1 ||
                        variant.inputs().containsKey(target) ||
                        !variant.inputs().values().iterator().next().equals(inputPerFiring) ||
                        !outputPerFiring.equals(variant.outputs().get(target))) {
                    return Optional.empty();
                }
            }
            return Optional.of(new UniformBindingFamily(
                    List.copyOf(variants),
                    inputPerFiring,
                    outputPerFiring));
        }
    }

    private static final class SearchBudget {

        private final int limit;
        private int used;

        private SearchBudget(int limit) {
            this.limit = limit;
        }

        private boolean consume() {
            if (this.used >= this.limit) {
                return false;
            }
            this.used++;
            return true;
        }

        private int used() {
            return this.used;
        }
    }
}
