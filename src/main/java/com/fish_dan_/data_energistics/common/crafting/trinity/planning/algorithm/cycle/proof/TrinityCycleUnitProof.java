package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCycleSequence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicFiringMath;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed.TrinityCycleSeedRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Exact quantity-independent unit route and restart seed for one deterministic productive cycle axis.
 *
 * @param reservoir     productive internal output
 * @param order         stable unit firing order
 * @param firings       unit aggregate firing vector
 * @param netChange     unit exact net change
 * @param internalSeed  SCC-internal restart reserve
 * @param externalInput per-unit external prefix input
 */
public record TrinityCycleUnitProof(
                                    AEKey reservoir,
                                    List<TrinityVariantFiring> order,
                                    Map<TrinityPatternVariant, BigInteger> firings,
                                    Map<AEKey, BigInteger> netChange,
                                    Map<AEKey, BigInteger> internalSeed,
                                    Map<AEKey, BigInteger> externalInput) {

    /** Derives a proof only for a complete unique-producer component route. */
    public static Optional<TrinityCycleUnitProof> derive(
                                                         TrinityStronglyConnectedComponent component,
                                                         AEKey reservoir) {
        Optional<List<TrinityVariantFiring>> resolved = TrinityDeterministicCycleSequence.create()
                .resolve(component, reservoir, Map.of());
        if (resolved.isEmpty() || !completeUniqueRoute(component, resolved.orElseThrow())) {
            return Optional.empty();
        }
        List<TrinityVariantFiring> order = resolved.orElseThrow();
        Map<TrinityPatternVariant, BigInteger> firings = Object2ObjectMaps.unmodifiable(
                new Object2ObjectLinkedOpenHashMap<>(TrinityDeterministicFiringMath.aggregate(order)));
        Map<AEKey, BigInteger> net = TrinityDeterministicFiringMath.netChange(firings);
        Set<AEKey> internalKeys = new ObjectOpenHashSet<>(component.keys());
        Map<AEKey, BigInteger> minimumInputs = TrinityCycleSeedRequirement.minimumInputs(order);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> internalSeed = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> externalInput = new Object2ObjectLinkedOpenHashMap<>();
        minimumInputs.forEach((key, amount) -> (internalKeys.contains(key) ? internalSeed : externalInput)
                .put(key, amount));
        return Optional.of(new TrinityCycleUnitProof(
                reservoir,
                order,
                firings,
                net,
                Object2ObjectMaps.unmodifiable(internalSeed),
                Object2ObjectMaps.unmodifiable(externalInput)));
    }

    /**
     * Reorders the cached unit firing ratio against current inventory and recomputes its exact prefix reserves.
     */
    public TrinityCycleUnitProof instantiate(
                                             Map<AEKey, BigInteger> available,
                                             List<AEKey> internalKeys) {
        ObjectArrayList<TrinityVariantFiring> remaining = new ObjectArrayList<>(order);
        ObjectArrayList<TrinityVariantFiring> ordered = new ObjectArrayList<>(order.size());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balances = new Object2ObjectLinkedOpenHashMap<>(available);
        while (!remaining.isEmpty()) {
            TrinityVariantFiring selected = remaining.stream()
                    .filter(firing -> hasInputs(balances, requiredAtStart(firing)))
                    .findFirst()
                    .orElse(remaining.getFirst());
            remaining.remove(selected);
            ordered.add(selected);
            selected.variant().netChange().forEach((key, amount) -> balances.merge(
                    key,
                    amount.multiply(selected.count()),
                    BigInteger::add));
        }
        Map<AEKey, BigInteger> minimumInputs = TrinityCycleSeedRequirement.minimumInputs(ordered);
        Set<AEKey> internal = new ObjectOpenHashSet<>(internalKeys);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> newInternalSeed = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> newExternalInput = new Object2ObjectLinkedOpenHashMap<>();
        minimumInputs.forEach((key, amount) -> (internal.contains(key) ? newInternalSeed : newExternalInput)
                .put(key, amount));
        return new TrinityCycleUnitProof(
                reservoir,
                ObjectLists.unmodifiable(ordered),
                firings,
                netChange,
                Object2ObjectMaps.unmodifiable(newInternalSeed),
                Object2ObjectMaps.unmodifiable(newExternalInput));
    }

    private static Map<AEKey, BigInteger> requiredAtStart(TrinityVariantFiring firing) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> required = new Object2ObjectLinkedOpenHashMap<>();
        firing.variant().inputs().forEach((key, input) -> {
            BigInteger net = firing.variant().netChange().getOrDefault(key, BigInteger.ZERO);
            required.put(key, net.signum() < 0 ?
                    input.add(net.negate().multiply(firing.count().subtract(BigInteger.ONE))) : input);
        });
        return required;
    }

    private static boolean hasInputs(
                                     Map<AEKey, BigInteger> balances,
                                     Map<AEKey, BigInteger> required) {
        return required.entrySet().stream().allMatch(entry -> balances
                .getOrDefault(entry.getKey(), BigInteger.ZERO)
                .compareTo(entry.getValue()) >= 0);
    }

    private static boolean completeUniqueRoute(
                                               TrinityStronglyConnectedComponent component,
                                               List<TrinityVariantFiring> order) {
        Set<TrinityPatternVariant> selected = new ObjectOpenHashSet<>();
        order.forEach(firing -> selected.add(firing.variant()));
        if (selected.size() != order.size() ||
                !selected.equals(new ObjectOpenHashSet<>(component.cycleVariants()))) {
            return false;
        }
        return component.keys().stream().allMatch(key -> component.cycleVariants().stream()
                .filter(variant -> variant.outputs().containsKey(key))
                .limit(2L)
                .count() == 1L);
    }
}
