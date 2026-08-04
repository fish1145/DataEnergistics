package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.complement;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Uses reverse demand propagation after externally costly cycle edges have cut the remaining dependency graph.
 */
final class TrinityFiringComplementOptimizerImpl implements TrinityFiringComplementOptimizer {

    private static final BigInteger ZERO = BigInteger.ZERO;

    @Override
    public Optional<Map<TrinityPatternVariant, BigInteger>> minimize(
                                                                     TrinityStronglyConnectedComponent component,
                                                                     TrinityCycleDemand demand,
                                                                     Map<AEKey, BigInteger> available,
                                                                     Set<AEKey> producibleInputs,
                                                                     Map<TrinityPatternVariant, BigInteger> firingUpperBound,
                                                                     Map<TrinityPatternVariant, BigInteger> reductions,
                                                                     Set<TrinityPatternVariant> fixedVariants) {
        if (component == null || demand == null || available == null || producibleInputs == null ||
                firingUpperBound == null || reductions == null || fixedVariants == null) {
            throw new IllegalArgumentException("A Trinity firing-complement request is incomplete");
        }
        List<TrinityPatternVariant> variants = component.cycleVariants().stream().sorted().toList();
        if (fixedVariants.isEmpty() || !firingUpperBound.keySet().containsAll(variants) ||
                !variants.containsAll(fixedVariants)) {
            return Optional.empty();
        }

        Optional<Map<AEKey, TrinityPatternVariant>> uniqueProducers = uniqueProducers(variants);
        if (uniqueProducers.isEmpty()) {
            return Optional.empty();
        }
        List<TrinityPatternVariant> adjustable = variants.stream()
                .filter(variant -> !fixedVariants.contains(variant))
                .toList();
        Optional<List<TrinityPatternVariant>> order = topologicalOrder(
                adjustable,
                uniqueProducers.orElseThrow());
        if (order.isEmpty()) {
            return Optional.empty();
        }

        LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            if (!fixedVariants.contains(variant)) {
                continue;
            }
            BigInteger count = firingUpperBound.get(variant)
                    .subtract(reductions.getOrDefault(variant, ZERO));
            if (count.signum() < 0) {
                return Optional.empty();
            }
            if (count.signum() > 0) {
                firings.put(variant, count);
            }
        }

        Map<AEKey, BigInteger> lowerBounds = lowerBounds(
                component,
                demand,
                available,
                producibleInputs);
        LinkedHashMap<AEKey, BigInteger> net = netChange(firings);
        List<TrinityPatternVariant> executionOrder = order.orElseThrow();
        for (int index = executionOrder.size() - 1; index >= 0; index--) {
            TrinityPatternVariant variant = executionOrder.get(index);
            BigInteger count = ZERO;
            for (Map.Entry<AEKey, BigInteger> output : variant.outputs().entrySet()) {
                if (!variant.equals(uniqueProducers.orElseThrow().get(output.getKey()))) {
                    continue;
                }
                BigInteger deficit = lowerBounds.getOrDefault(output.getKey(), ZERO)
                        .subtract(net.getOrDefault(output.getKey(), ZERO));
                if (deficit.signum() > 0) {
                    count = count.max(ceilDivide(deficit, output.getValue()));
                }
            }
            if (count.compareTo(firingUpperBound.get(variant)) > 0) {
                return Optional.empty();
            }
            if (count.signum() > 0) {
                firings.put(variant, count);
                BigInteger selectedCount = count;
                variant.netChange().forEach((key, amount) -> net.merge(key, amount.multiply(selectedCount), BigInteger::add));
            }
        }
        if (lowerBounds.entrySet().stream().anyMatch(entry -> net.getOrDefault(entry.getKey(), ZERO).compareTo(entry.getValue()) < 0)) {
            return Optional.empty();
        }

        LinkedHashMap<TrinityPatternVariant, BigInteger> ordered = new LinkedHashMap<>();
        variants.forEach(variant -> {
            BigInteger count = firings.getOrDefault(variant, ZERO);
            if (count.signum() > 0) {
                ordered.put(variant, count);
            }
        });
        return Optional.of(Collections.unmodifiableMap(ordered));
    }

    private static Optional<Map<AEKey, TrinityPatternVariant>> uniqueProducers(
                                                                               List<TrinityPatternVariant> variants) {
        LinkedHashMap<AEKey, TrinityPatternVariant> producers = new LinkedHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            for (AEKey output : variant.outputs().keySet()) {
                TrinityPatternVariant existing = producers.putIfAbsent(output, variant);
                if (existing != null && !existing.equals(variant)) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(Collections.unmodifiableMap(producers));
    }

    private static Optional<List<TrinityPatternVariant>> topologicalOrder(
                                                                          List<TrinityPatternVariant> variants,
                                                                          Map<AEKey, TrinityPatternVariant> producerByKey) {
        Set<TrinityPatternVariant> adjustable = Set.copyOf(variants);
        HashMap<TrinityPatternVariant, Integer> indegrees = new HashMap<>();
        HashMap<TrinityPatternVariant, LinkedHashSet<TrinityPatternVariant>> successors = new HashMap<>();
        variants.forEach(variant -> {
            indegrees.put(variant, 0);
            successors.put(variant, new LinkedHashSet<>());
        });
        for (TrinityPatternVariant consumer : variants) {
            for (AEKey input : consumer.inputs().keySet()) {
                TrinityPatternVariant producer = producerByKey.get(input);
                if (producer == null || !adjustable.contains(producer)) {
                    continue;
                }
                if (producer.equals(consumer)) {
                    return Optional.empty();
                }
                if (successors.get(producer).add(consumer)) {
                    indegrees.merge(consumer, 1, Integer::sum);
                }
            }
        }
        ArrayList<TrinityPatternVariant> ready = new ArrayList<>();
        indegrees.forEach((variant, degree) -> {
            if (degree == 0) {
                ready.add(variant);
            }
        });
        ready.sort(TrinityPatternVariant::compareTo);
        ArrayList<TrinityPatternVariant> ordered = new ArrayList<>(variants.size());
        while (!ready.isEmpty()) {
            TrinityPatternVariant selected = ready.removeFirst();
            ordered.add(selected);
            for (TrinityPatternVariant successor : successors.get(selected)) {
                int degree = indegrees.merge(successor, -1, Integer::sum);
                if (degree == 0) {
                    ready.add(successor);
                    ready.sort(TrinityPatternVariant::compareTo);
                }
            }
        }
        return ordered.size() == variants.size() ? Optional.of(List.copyOf(ordered)) : Optional.empty();
    }

    private static Map<AEKey, BigInteger> lowerBounds(
                                                      TrinityStronglyConnectedComponent component,
                                                      TrinityCycleDemand demand,
                                                      Map<AEKey, BigInteger> available,
                                                      Set<AEKey> producibleInputs) {
        LinkedHashMap<AEKey, BigInteger> lower = new LinkedHashMap<>(demand.requiredNetChangeLowerBounds());
        LinkedHashSet<AEKey> touched = new LinkedHashSet<>(component.keys());
        component.cycleVariants().forEach(variant -> touched.addAll(variant.netChange().keySet()));
        touched.addAll(demand.finalBalanceLowerBounds().keySet());
        for (AEKey key : touched) {
            if (producibleInputs.contains(key)) {
                continue;
            }
            BigInteger finiteLower = demand.finalBalanceLowerBounds()
                    .getOrDefault(key, ZERO)
                    .subtract(available.getOrDefault(key, ZERO));
            lower.merge(key, finiteLower, BigInteger::max);
        }
        return Collections.unmodifiableMap(lower);
    }

    private static LinkedHashMap<AEKey, BigInteger> netChange(
                                                              Map<TrinityPatternVariant, BigInteger> firings) {
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach((key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return net;
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() <= 0 || denominator.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity firing complement requires positive division operands");
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }
}
