package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
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

/**
 * Reverse condensation traversal using exact ceil-division and one aggregate state per demanded graph key.
 */
final class TrinityAcyclicDemandPropagatorImpl implements TrinityAcyclicDemandPropagator {

    @Override
    public TrinityAlgorithmResult<TrinityAcyclicPlan> propagate(
                                                                TrinityCraftingTopology topology,
                                                                List<TrinityPatternVariant> variants,
                                                                AEKey target,
                                                                BigInteger requestedAmount,
                                                                CraftingQuantityMode quantityMode,
                                                                Map<AEKey, BigInteger> available) {
        if (topology == null || variants == null || target == null || requestedAmount == null ||
                requestedAmount.signum() <= 0 || quantityMode == null || available == null) {
            throw new IllegalArgumentException("A Trinity acyclic propagation requires complete, positive inputs");
        }
        Map<AEKey, BigInteger> inventory = copyAvailable(available);

        LinkedHashMap<AEKey, BigInteger> need = new LinkedHashMap<>();
        merge(need, target, requestedAmount);
        Map<AEKey, List<TrinityPatternVariant>> producers = indexProducers(variants);
        LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> reservedInputs = new LinkedHashMap<>();
        int states = 0;
        List<Integer> componentOrder = topology.topologicalOrder();
        for (int position = componentOrder.size() - 1; position >= 0; position--) {
            TrinityStronglyConnectedComponent component = topology.components().get(componentOrder.get(position));
            for (AEKey key : component.keys()) {
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
                    return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                            TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                            Component.literal("Trinity planning inventory cannot satisfy an uncraftable input"),
                            Map.of(
                                    "key", key.toString(),
                                    "required", required.max(BigInteger.ZERO).toString(),
                                    "available", availableAmount.toString())));
                }
                if (component.cyclic()) {
                    return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                            TrinityPlanningDiagnosticCode.NO_PRODUCTIVE_CYCLE,
                            Component.literal("A cyclic demand reached the acyclic Trinity propagator"),
                            Map.of("component", Integer.toString(component.index()))));
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
        return TrinityAlgorithmResult.success(new TrinityAcyclicPlan(
                executionOrder,
                orderedFirings,
                reservedInputs,
                net,
                states));
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
}
