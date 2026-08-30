package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.InputRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization.TrinityAcyclicCompetitionPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization.TrinityAcyclicCompetitionPlanner.Attempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization.TrinityAcyclicRouteOptimizer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization.TrinityAcyclicRouteOptimizer.ShortageEvidence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization.TrinityAcyclicRoutePruner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Propagates aggregate demand through acyclic keys without expanding one state per requested item.
 * <p>
 * Reverse condensation traversal using exact ceil-division and one aggregate state per demanded graph key.
 */
public final class TrinityAcyclicDemandPropagator {

    /**
     * @return stateless exact propagator
     */
    public static TrinityAcyclicDemandPropagator create() {
        return new TrinityAcyclicDemandPropagator(
                TrinityAcyclicRouteOptimizer.create(),
                TrinityAcyclicRoutePruner.create());
    }

    private final TrinityAcyclicRouteOptimizer routeOptimizer;
    private final TrinityAcyclicRoutePruner routePruner;
    private final TrinityAcyclicCompetitionPlanner competitionPlanner;

    TrinityAcyclicDemandPropagator(TrinityAcyclicRouteOptimizer routeOptimizer,
                                   TrinityAcyclicRoutePruner routePruner) {
        this.routeOptimizer = routeOptimizer;
        this.routePruner = routePruner;
        this.competitionPlanner = TrinityAcyclicCompetitionPlanner.create(routeOptimizer);
    }

    /**
     * @param topology        analyzed graph topology
     * @param variants        complete identity-ordered transition set
     * @param target          requested output key
     * @param requestedAmount positive requested amount
     * @param quantityMode    net-new or final-total semantics
     * @param available       immutable non-negative inventory snapshot
     * @param maxSearchStates maximum aggregate route-optimization states
     * @param mode            complete optimisation or first-feasible fallback
     * @param control         cooperative cancellation and shared deadline
     * @return compact plan, or an explicit cycle/unsupported diagnostic
     */
    public TrinityAlgorithmResult<TrinityAcyclicPlan> propagate(
                                                                TrinityCraftingTopology topology,
                                                                List<TrinityPatternVariant> variants,
                                                                AEKey target,
                                                                BigInteger requestedAmount,
                                                                CraftingQuantityMode quantityMode,
                                                                Map<AEKey, BigInteger> available,
                                                                int maxSearchStates,
                                                                TrinityPlanningMode mode,
                                                                TrinityPlanningControl control) {
        if (topology == null || variants == null || target == null || requestedAmount == null ||
                requestedAmount.signum() <= 0 || quantityMode == null || available == null ||
                maxSearchStates <= 0 || mode == null || control == null) {
            throw new IllegalArgumentException("A Trinity acyclic propagation requires complete, positive inputs");
        }
        StopState initialState = stopState(control);
        if (initialState != StopState.RUNNING) {
            return stopped(initialState);
        }
        Integer targetComponent = topology.componentByKey().get(target);
        if (targetComponent == null) {
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.target_absent"),
                    Map.of("key", target.toString())));
        }
        List<Integer> reachableComponents = reachablePredecessors(topology, targetComponent);
        for (Integer componentIndex : reachableComponents) {
            StopState state = stopState(control);
            if (state != StopState.RUNNING) {
                return stopped(state);
            }
            TrinityStronglyConnectedComponent component = topology.components().get(componentIndex);
            if (component.cyclic()) {
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.NO_PRODUCTIVE_CYCLE,
                        Component.translatable("gui.data_energistics.trinity_planning.diagnostic.cyclic_demand"),
                        Map.of("component", Integer.toString(component.index()))));
            }
        }
        List<TrinityPatternVariant> executableRoutes = this.routePruner.retainExecutableTargetRoutes(
                variants,
                target,
                available);
        List<TrinityPatternVariant> planningVariants = executableRoutes.isEmpty() ? variants : executableRoutes;
        Map<AEKey, List<TrinityPatternVariant>> producers = indexProducers(planningVariants);
        if (requiresGlobalRouteOptimization(topology, reachableComponents, producers)) {
            Optional<Attempt> competition = this.competitionPlanner.plan(
                    topology,
                    planningVariants,
                    producers,
                    target,
                    requestedAmount,
                    quantityMode,
                    available,
                    maxSearchStates,
                    mode,
                    control);
            if (competition.isPresent()) {
                Attempt attempt = competition.orElseThrow();
                return completeOptimizedResult(
                        attempt.result(),
                        variants,
                        target,
                        requestedAmount,
                        quantityMode,
                        available,
                        attempt.diagnosticBudget(),
                        control);
            }
            return optimizeWholeGraph(
                    topology,
                    planningVariants,
                    variants,
                    target,
                    requestedAmount,
                    quantityMode,
                    available,
                    maxSearchStates,
                    mode,
                    control);
        }

        Map<AEKey, BigInteger> inventory = copyAvailable(available);
        LinkedHashMap<AEKey, BigInteger> need = new LinkedHashMap<>();
        merge(need, target, requestedAmount);
        LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> reservedInputs = new LinkedHashMap<>();
        LinkedHashMap<AEKey, InputRequirement> shortages = new LinkedHashMap<>();
        int states = 0;
        List<Integer> componentOrder = topology.topologicalOrder();
        for (int position = componentOrder.size() - 1; position >= 0; position--) {
            StopState state = stopState(control);
            if (state != StopState.RUNNING) {
                return stopped(state, reservedInputs, firings, need, shortages);
            }
            TrinityStronglyConnectedComponent component = topology.components().get(componentOrder.get(position));
            for (AEKey key : component.keys()) {
                state = stopState(control);
                if (state != StopState.RUNNING) {
                    return stopped(state, reservedInputs, firings, need, shortages);
                }
                BigInteger required = need.getOrDefault(key, BigInteger.ZERO);
                boolean forceFinalTotalProduction = key.equals(target) &&
                        quantityMode == CraftingQuantityMode.FINAL_TOTAL;
                if (required.signum() <= 0 && !forceFinalTotalProduction) {
                    continue;
                }
                BigInteger availableAmount = inventory.getOrDefault(key, BigInteger.ZERO);
                BigInteger reserved = key.equals(target) && quantityMode == CraftingQuantityMode.NET_NEW ?
                        BigInteger.ZERO :
                        required.max(BigInteger.ZERO).min(availableAmount);
                if (reserved.signum() > 0) {
                    merge(reservedInputs, key, reserved);
                    inventory.put(key, availableAmount.subtract(reserved));
                    merge(need, key, reserved.negate());
                }
                BigInteger missing = need.getOrDefault(key, BigInteger.ZERO).max(BigInteger.ZERO);
                List<TrinityPatternVariant> candidates = producers.getOrDefault(key, List.of());
                if (missing.signum() <= 0 && !forceFinalTotalProduction) {
                    continue;
                }
                states = Math.addExact(states, Math.max(1, candidates.size()));
                if (candidates.isEmpty()) {
                    BigInteger positiveRequired = required.max(BigInteger.ZERO);
                    if (missing.signum() > 0) {
                        mergeRequirement(shortages, key, positiveRequired, reserved, missing);
                        merge(need, key, missing.negate());
                        continue;
                    }
                    TrinityPlanningDiagnostic diagnostic = new TrinityPlanningDiagnostic(
                            TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                            Component.translatable("gui.data_energistics.trinity_planning.diagnostic.insufficient_input"),
                            Map.of(
                                    "key", key.toString(),
                                    "required", positiveRequired.toString(),
                                    "available", availableAmount.toString()));
                    return TrinityAlgorithmResult.failure(withPartial(
                            diagnostic,
                            reservedInputs,
                            firings,
                            need,
                            shortages));
                }
                TrinityPatternVariant selected = candidates.getFirst();
                BigInteger outputPerFiring = selected.outputs().get(key);
                BigInteger count = missing.signum() > 0 ?
                        ceilDivide(missing, outputPerFiring) :
                        BigInteger.ONE;
                firings.merge(selected, count, BigInteger::add);
                selected.inputs().forEach((input, amount) -> merge(need, input, amount.multiply(count)));
                selected.outputs().forEach((output, amount) -> merge(need, output, amount.multiply(count).negate()));
            }
        }

        LinkedHashMap<AEKey, BigInteger> net = aggregateNetChange(firings);
        ArrayList<TrinityVariantFiring> executionOrder = new ArrayList<>();
        Map<Integer, Integer> topologicalPositions = topologicalPositions(topology);
        firings.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<TrinityPatternVariant, BigInteger> entry) -> producerPosition(topology, topologicalPositions, entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> executionOrder.add(new TrinityVariantFiring(entry.getKey(), entry.getValue())));
        LinkedHashMap<TrinityPatternVariant, BigInteger> orderedFirings = new LinkedHashMap<>();
        executionOrder.forEach(firing -> orderedFirings.put(firing.variant(), firing.count()));
        StopState completedState = stopState(control);
        if (completedState != StopState.RUNNING) {
            return stopped(completedState, reservedInputs, firings, need, shortages);
        }
        if (!shortages.isEmpty()) {
            return insufficient(reservedInputs, firings, shortages);
        }
        return TrinityAlgorithmResult.success(new TrinityAcyclicPlan(
                executionOrder,
                orderedFirings,
                reservedInputs,
                net,
                states));
    }

    private TrinityAlgorithmResult<TrinityAcyclicPlan> optimizeWholeGraph(
                                                                          TrinityCraftingTopology topology,
                                                                          List<TrinityPatternVariant> planningVariants,
                                                                          List<TrinityPatternVariant> diagnosticVariants,
                                                                          AEKey target,
                                                                          BigInteger requestedAmount,
                                                                          CraftingQuantityMode quantityMode,
                                                                          Map<AEKey, BigInteger> available,
                                                                          int maxSearchStates,
                                                                          TrinityPlanningMode mode,
                                                                          TrinityPlanningControl control) {
        TrinityAlgorithmResult<TrinityAcyclicPlan> optimized = this.routeOptimizer.optimize(
                topology,
                planningVariants,
                target,
                requestedAmount,
                quantityMode,
                available,
                maxSearchStates,
                mode,
                control);
        return completeOptimizedResult(
                optimized,
                diagnosticVariants,
                target,
                requestedAmount,
                quantityMode,
                available,
                maxSearchStates,
                control);
    }

    private TrinityAlgorithmResult<TrinityAcyclicPlan> completeOptimizedResult(
                                                                               TrinityAlgorithmResult<TrinityAcyclicPlan> optimized,
                                                                               List<TrinityPatternVariant> diagnosticVariants,
                                                                               AEKey target,
                                                                               BigInteger requestedAmount,
                                                                               CraftingQuantityMode quantityMode,
                                                                               Map<AEKey, BigInteger> available,
                                                                               int maxSearchStates,
                                                                               TrinityPlanningControl control) {
        if (optimized.successful()) {
            return optimized;
        }
        if (optimized.diagnostic().code() == TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT) {
            TrinityAlgorithmResult<ShortageEvidence> diagnosed = this.routeOptimizer.diagnoseShortage(
                    diagnosticVariants,
                    target,
                    requestedAmount,
                    quantityMode,
                    available,
                    maxSearchStates,
                    control);
            if (diagnosed.successful()) {
                return insufficient(diagnosed.value());
            }
            if (diagnosed.diagnostic().partialPlan().isPresent()) {
                return TrinityAlgorithmResult.failure(diagnosed.diagnostic());
            }
            return TrinityAlgorithmResult.failure(diagnosed.diagnostic().withDetail(
                    new TrinityPlanningDiagnostic.PartialPlan(
                            Map.of(),
                            Map.of(),
                            Map.of(target, requestedAmount))));
        }
        if (optimized.diagnostic().inputShortage().isPresent() ||
                optimized.diagnostic().partialPlan().isPresent()) {
            return optimized;
        }
        return TrinityAlgorithmResult.failure(optimized.diagnostic().withDetail(
                new TrinityPlanningDiagnostic.PartialPlan(
                        Map.of(),
                        Map.of(),
                        Map.of(target, requestedAmount))));
    }

    /**
     * Compatibility entry point that retains full optimisation.
     */
    public TrinityAlgorithmResult<TrinityAcyclicPlan> propagate(
                                                                TrinityCraftingTopology topology,
                                                                List<TrinityPatternVariant> variants,
                                                                AEKey target,
                                                                BigInteger requestedAmount,
                                                                CraftingQuantityMode quantityMode,
                                                                Map<AEKey, BigInteger> available,
                                                                int maxSearchStates,
                                                                TrinityPlanningControl control) {
        return propagate(
                topology,
                variants,
                target,
                requestedAmount,
                quantityMode,
                available,
                maxSearchStates,
                TrinityPlanningMode.OPTIMAL,
                control);
    }

    private static Map<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity available inventory cannot be negative or null");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return copied;
    }

    private static Map<AEKey, List<TrinityPatternVariant>> indexProducers(
                                                                          List<TrinityPatternVariant> variants) {
        HashMap<AEKey, ArrayList<TrinityPatternVariant>> mutable = new HashMap<>();
        for (TrinityPatternVariant variant : variants) {
            if (variant == null) {
                throw new IllegalArgumentException("A Trinity acyclic graph cannot contain a null variant");
            }
            variant.outputs().forEach((key, amount) -> {
                if (amount.signum() > 0) {
                    mutable.computeIfAbsent(key, ignored -> new ArrayList<>()).add(variant);
                }
            });
        }
        HashMap<AEKey, List<TrinityPatternVariant>> producers = new HashMap<>();
        mutable.forEach((key, candidates) -> {
            candidates.sort(Comparator.naturalOrder());
            producers.put(key, List.copyOf(candidates));
        });
        return producers;
    }

    private static List<Integer> reachablePredecessors(TrinityCraftingTopology topology, int targetComponent) {
        ArrayList<Integer> pending = new ArrayList<>();
        LinkedHashMap<Integer, Boolean> visited = new LinkedHashMap<>();
        pending.add(targetComponent);
        for (int index = 0; index < pending.size(); index++) {
            int component = pending.get(index);
            if (visited.putIfAbsent(component, Boolean.TRUE) != null) {
                continue;
            }
            pending.addAll(topology.components().get(component).predecessorIndexes());
        }
        return List.copyOf(visited.keySet());
    }

    private static boolean requiresGlobalRouteOptimization(
                                                           TrinityCraftingTopology topology,
                                                           List<Integer> reachableComponents,
                                                           Map<AEKey, List<TrinityPatternVariant>> producers) {
        for (Integer componentIndex : reachableComponents) {
            for (AEKey key : topology.components().get(componentIndex).keys()) {
                List<TrinityPatternVariant> candidates = producers.getOrDefault(key, List.of());
                if (candidates.size() > 1 ||
                        candidates.stream().anyMatch(variant -> variant.outputs().size() > 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int producerPosition(TrinityCraftingTopology topology,
                                        Map<Integer, Integer> topologicalPositions,
                                        TrinityPatternVariant variant) {
        int earliestOutput = Integer.MAX_VALUE;
        for (AEKey output : variant.outputs().keySet()) {
            Integer component = topology.componentByKey().get(output);
            if (component != null) {
                earliestOutput = Math.min(earliestOutput, topologicalPositions.get(component));
            }
        }
        if (earliestOutput == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A Trinity acyclic firing output is absent from topology");
        }
        return earliestOutput;
    }

    private static Map<Integer, Integer> topologicalPositions(TrinityCraftingTopology topology) {
        HashMap<Integer, Integer> positions = new HashMap<>();
        for (int position = 0; position < topology.topologicalOrder().size(); position++) {
            positions.put(topology.topologicalOrder().get(position), position);
        }
        return positions;
    }

    private static LinkedHashMap<AEKey, BigInteger> aggregateNetChange(
                                                                       Map<TrinityPatternVariant, BigInteger> firings) {
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange()
                .forEach((key, amount) -> merge(net, key, amount.multiply(count))));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return net;
    }

    private static <T> TrinityAlgorithmResult<T> stopped(
                                                         StopState state,
                                                         Map<AEKey, BigInteger> reservedInputs,
                                                         Map<TrinityPatternVariant, BigInteger> firings,
                                                         Map<AEKey, BigInteger> need,
                                                         Map<AEKey, InputRequirement> shortages) {
        TrinityPlanningDiagnostic diagnostic = stopped(state).diagnostic();
        return TrinityAlgorithmResult.failure(withPartial(
                diagnostic,
                reservedInputs,
                firings,
                need,
                shortages));
    }

    private static TrinityPlanningDiagnostic withPartial(
                                                         TrinityPlanningDiagnostic diagnostic,
                                                         Map<AEKey, BigInteger> reservedInputs,
                                                         Map<TrinityPatternVariant, BigInteger> firings,
                                                         Map<AEKey, BigInteger> need,
                                                         Map<AEKey, InputRequirement> shortages) {
        if (diagnostic.inputShortage().isPresent() || diagnostic.partialPlan().isPresent()) {
            return diagnostic;
        }
        LinkedHashMap<AEKey, BigInteger> unresolved = missingAmounts(shortages);
        need.forEach((key, amount) -> {
            if (amount.signum() > 0) {
                unresolved.merge(key, amount, BigInteger::add);
            }
        });
        return diagnostic.withDetail(new TrinityPlanningDiagnostic.PartialPlan(
                reservedInputs,
                aggregateOutputs(firings),
                unresolved));
    }

    private static <T> TrinityAlgorithmResult<T> insufficient(
                                                              Map<AEKey, BigInteger> reservedInputs,
                                                              Map<TrinityPatternVariant, BigInteger> firings,
                                                              Map<AEKey, InputRequirement> shortages) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("shortageKinds", Integer.toString(shortages.size()));
        if (shortages.size() == 1) {
            Map.Entry<AEKey, InputRequirement> shortage = shortages.entrySet().iterator().next();
            metadata.put("key", shortage.getKey().toString());
            metadata.put("required", shortage.getValue().required().toString());
            metadata.put("available", shortage.getValue().available().toString());
            metadata.put("missing", shortage.getValue().missing().toString());
        }
        TrinityPlanningDiagnostic diagnostic = new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic.insufficient_input"),
                metadata,
                new TrinityPlanningDiagnostic.PartialPlan(
                        reservedInputs,
                        aggregateOutputs(firings),
                        missingAmounts(shortages),
                        shortages));
        return TrinityAlgorithmResult.failure(diagnostic);
    }

    private static <T> TrinityAlgorithmResult<T> insufficient(ShortageEvidence evidence) {
        LinkedHashMap<AEKey, InputRequirement> shortages = new LinkedHashMap<>();
        evidence.inputRequirements().forEach((key, requirement) -> {
            if (requirement.missing().signum() > 0) {
                shortages.put(key, new InputRequirement(
                        requirement.required(),
                        requirement.allocated(),
                        requirement.missing()));
            }
        });
        if (shortages.isEmpty()) {
            throw new IllegalArgumentException("A Trinity shortage diagnosis must contain a positive missing input");
        }
        return insufficient(evidence.actualReserves(), evidence.firings(), shortages);
    }

    private static LinkedHashMap<AEKey, BigInteger> aggregateOutputs(
                                                                     Map<TrinityPatternVariant, BigInteger> firings) {
        LinkedHashMap<AEKey, BigInteger> emitted = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.outputs().forEach(
                (key, amount) -> emitted.merge(key, amount.multiply(count), BigInteger::add)));
        return emitted;
    }

    private static LinkedHashMap<AEKey, BigInteger> missingAmounts(
                                                                   Map<AEKey, InputRequirement> shortages) {
        LinkedHashMap<AEKey, BigInteger> missing = new LinkedHashMap<>();
        shortages.forEach((key, requirement) -> missing.put(key, requirement.missing()));
        return missing;
    }

    private static void mergeRequirement(
                                         Map<AEKey, InputRequirement> shortages,
                                         AEKey key,
                                         BigInteger required,
                                         BigInteger available,
                                         BigInteger missing) {
        InputRequirement added = new InputRequirement(required, available, missing);
        shortages.merge(key, added, (existing, value) -> new InputRequirement(
                existing.required().add(value.required()),
                existing.available().add(value.available()),
                existing.missing().add(value.missing())));
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() <= 0 || denominator.signum() <= 0) {
            throw new IllegalArgumentException("Trinity ceil division requires positive values");
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    private static void merge(Map<AEKey, BigInteger> amounts, AEKey key, BigInteger amount) {
        amounts.merge(key, amount, BigInteger::add);
    }

    private static StopState stopState(TrinityPlanningControl control) {
        if (control.cancellationRequested()) {
            return StopState.CANCELLED;
        }
        return control.deadlineExceeded() ? StopState.DEADLINE_EXCEEDED : StopState.RUNNING;
    }

    private static <T> TrinityAlgorithmResult<T> stopped(StopState state) {
        return switch (state) {
            case CANCELLED -> TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.cancelled"),
                    Map.of("phase", "dag")));
            case DEADLINE_EXCEEDED -> TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.timeout"),
                    Map.of("phase", "dag")));
            case RUNNING -> throw new IllegalArgumentException("A running Trinity propagation is not stopped");
        };
    }

    private enum StopState {
        RUNNING,
        CANCELLED,
        DEADLINE_EXCEEDED
    }
}
