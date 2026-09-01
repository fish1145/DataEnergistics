package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicDiagnostics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicFiringMath;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

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

/**
 * Captures the unique positive-net producer residual remaining after one reservoir axis is chosen as the cycle basis.
 */
public final class TrinityDeterministicResidualTopology {

    private final AEKey reservoir;
    private final Map<AEKey, TrinityPatternVariant> producerByKey;
    private final Set<AEKey> ambiguousResidualOutputs;
    private final List<TrinityPatternVariant> executionOrder;

    private TrinityDeterministicResidualTopology(
                                                 AEKey reservoir,
                                                 Map<AEKey, TrinityPatternVariant> producerByKey,
                                                 Set<AEKey> ambiguousResidualOutputs,
                                                 List<TrinityPatternVariant> executionOrder) {
        this.reservoir = reservoir;
        this.producerByKey = producerByKey;
        this.ambiguousResidualOutputs = ambiguousResidualOutputs;
        this.executionOrder = executionOrder;
    }

    public static Optional<TrinityDeterministicResidualTopology> create(
                                                                        TrinityStronglyConnectedComponent component,
                                                                        AEKey reservoir) {
        List<TrinityPatternVariant> variants = component.cycleVariants().stream().sorted().toList();
        LinkedHashMap<AEKey, TrinityPatternVariant> producerByKey = new LinkedHashMap<>();
        LinkedHashSet<AEKey> ambiguous = new LinkedHashSet<>();
        for (TrinityPatternVariant variant : variants) {
            for (Map.Entry<AEKey, BigInteger> effect : variant.netChange().entrySet()) {
                AEKey output = effect.getKey();
                if (effect.getValue().signum() <= 0 || output.equals(reservoir) || ambiguous.contains(output)) {
                    continue;
                }
                TrinityPatternVariant existing = producerByKey.putIfAbsent(output, variant);
                if (existing != null && !existing.equals(variant)) {
                    producerByKey.remove(output);
                    ambiguous.add(output);
                }
            }
        }

        HashMap<TrinityPatternVariant, Integer> indegrees = new HashMap<>();
        HashMap<TrinityPatternVariant, LinkedHashSet<TrinityPatternVariant>> successors = new HashMap<>();
        variants.forEach(variant -> {
            indegrees.put(variant, 0);
            successors.put(variant, new LinkedHashSet<>());
        });
        for (TrinityPatternVariant consumer : variants) {
            for (Map.Entry<AEKey, BigInteger> effect : consumer.netChange().entrySet()) {
                if (effect.getValue().signum() >= 0) {
                    continue;
                }
                AEKey input = effect.getKey();
                if (input.equals(reservoir)) {
                    continue;
                }
                if (ambiguous.contains(input)) {
                    return Optional.empty();
                }
                TrinityPatternVariant producer = producerByKey.get(input);
                if (producer == null) {
                    continue;
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
        ready.sort(Comparator.naturalOrder());
        ArrayList<TrinityPatternVariant> order = new ArrayList<>(variants.size());
        while (!ready.isEmpty()) {
            TrinityPatternVariant selected = ready.removeFirst();
            order.add(selected);
            for (TrinityPatternVariant successor : successors.get(selected)) {
                int degree = indegrees.merge(successor, -1, Integer::sum);
                if (degree == 0) {
                    ready.add(successor);
                    ready.sort(Comparator.naturalOrder());
                }
            }
        }
        if (order.size() != variants.size()) {
            return Optional.empty();
        }
        return Optional.of(new TrinityDeterministicResidualTopology(
                reservoir,
                Collections.unmodifiableMap(producerByKey),
                Collections.unmodifiableSet(ambiguous),
                List.copyOf(order)));
    }

    public TrinityAlgorithmResult<TrinityDeterministicResidualResult> solveResidual(
                                                                                    Map<AEKey, BigInteger> requiredNet,
                                                                                    Map<AEKey, BigInteger> primitiveNet,
                                                                                    BigInteger repetitions) {
        LinkedHashMap<AEKey, BigInteger> requirements = new LinkedHashMap<>();
        requiredNet.forEach((key, amount) -> {
            if (!key.equals(this.reservoir)) {
                BigInteger remaining = amount.subtract(
                        primitiveNet.getOrDefault(key, TrinityDeterministicFiringMath.ZERO).multiply(repetitions));
                if (remaining.signum() > 0) {
                    requirements.put(key, remaining);
                }
            }
        });
        for (AEKey key : requirements.keySet()) {
            if (this.ambiguousResidualOutputs.contains(key) || !this.producerByKey.containsKey(key)) {
                return TrinityDeterministicDiagnostics.unsupported();
            }
        }

        LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
        for (int index = this.executionOrder.size() - 1; index >= 0; index--) {
            TrinityPatternVariant variant = this.executionOrder.get(index);
            BigInteger count = TrinityDeterministicFiringMath.ZERO;
            for (Map.Entry<AEKey, BigInteger> effect : variant.netChange().entrySet()) {
                if (effect.getValue().signum() <= 0 || !variant.equals(this.producerByKey.get(effect.getKey()))) {
                    continue;
                }
                BigInteger required = requirements.getOrDefault(
                        effect.getKey(),
                        TrinityDeterministicFiringMath.ZERO);
                if (required.signum() > 0) {
                    count = count.max(TrinityDeterministicFiringMath.ceilDivide(required, effect.getValue()));
                }
            }
            if (count.signum() == 0) {
                continue;
            }
            firings.put(variant, count);
            BigInteger selectedCount = count;
            variant.netChange().forEach((key, effect) -> {
                if (effect.signum() < 0 && !key.equals(this.reservoir) && this.producerByKey.containsKey(key)) {
                    requirements.merge(key, effect.negate().multiply(selectedCount), BigInteger::add);
                }
            });
        }
        Map<AEKey, BigInteger> net = TrinityDeterministicFiringMath.netChange(firings);
        ArrayList<TrinityVariantFiring> ordered = new ArrayList<>();
        for (TrinityPatternVariant variant : this.executionOrder) {
            BigInteger count = firings.getOrDefault(variant, TrinityDeterministicFiringMath.ZERO);
            if (count.signum() > 0) {
                ordered.add(new TrinityVariantFiring(variant, count));
            }
        }
        return TrinityAlgorithmResult.success(new TrinityDeterministicResidualResult(
                Collections.unmodifiableMap(firings),
                net,
                List.copyOf(ordered)));
    }

    public List<TrinityPatternVariant> executionOrder() {
        return this.executionOrder;
    }
}
