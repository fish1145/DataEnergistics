package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact internal balance that must remain after a selected cycle so the same proved route can start again.
 *
 * @param amounts positive prefix deficits restricted to the owning SCC keys
 */
public record TrinityCycleSeedRequirement(Map<AEKey, BigInteger> amounts) {

    /**
     * Replays only the normalized route unit. Quantity-bound prefix and suffix batches are settled by the current
     * plan and cannot become a permanent restart reserve for the next request.
     *
     * @param selection    completely scheduled cycle selection
     * @param internalKeys keys owned by the selected SCC
     * @return immutable exact restart requirement
     */
    public static TrinityCycleSeedRequirement fromSelection(
                                                            TrinityCycleSelection selection,
                                                            Set<AEKey> internalKeys) {
        if (internalKeys.isEmpty()) {
            throw new IllegalArgumentException("A Trinity cycle seed proof requires a selection and internal keys");
        }
        return fromOrder(selection.localOrder(), internalKeys);
    }

    /**
     * Derives the internal restart reserve of one already normalized unit order.
     */
    public static TrinityCycleSeedRequirement fromOrder(
                                                        List<TrinityVariantFiring> order,
                                                        Set<AEKey> internalKeys) {
        Map<AEKey, BigInteger> minimumInputs = minimumInputs(order);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> internal = new Object2ObjectLinkedOpenHashMap<>();
        minimumInputs.forEach((key, amount) -> {
            if (internalKeys.contains(key)) {
                internal.put(key, amount);
            }
        });
        return new TrinityCycleSeedRequirement(Object2ObjectMaps.unmodifiable(internal));
    }

    /**
     * @return exact prefix input for every key touched by the ordered firing unit
     */
    public static Map<AEKey, BigInteger> minimumInputs(List<TrinityVariantFiring> order) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balance = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> required = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityVariantFiring firing : order) {
            BigInteger count = firing.count();
            firing.variant().inputs().forEach((key, inputAmount) -> {
                BigInteger net = firing.variant().netChange().getOrDefault(key, BigInteger.ZERO);
                BigInteger requiredAtStart = inputAmount;
                if (net.signum() < 0) {
                    requiredAtStart = requiredAtStart.add(net.negate().multiply(count.subtract(BigInteger.ONE)));
                }
                BigInteger deficit = requiredAtStart.subtract(balance.getOrDefault(key, BigInteger.ZERO));
                if (deficit.signum() > 0) {
                    balance.merge(key, deficit, BigInteger::add);
                    required.merge(key, deficit, BigInteger::add);
                }
            });
            firing.variant().netChange().forEach(
                    (key, amount) -> balance.merge(key, amount.multiply(count), BigInteger::add));
        }
        return Object2ObjectMaps.unmodifiable(required);
    }

    /**
     * Computes the exact initial balance for a fixed unit repeated without expanding its BigInteger count.
     */
    public static Map<AEKey, BigInteger> repeatedMinimumInputs(
                                                               List<TrinityVariantFiring> order,
                                                               BigInteger repetitions) {
        if (repetitions.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity repeated seed proof requires a positive count");
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> required = new Object2ObjectLinkedOpenHashMap<>(minimumInputs(order));
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> unitNet = new Object2ObjectLinkedOpenHashMap<>();
        order.forEach(firing -> firing.variant().netChange().forEach(
                (key, amount) -> unitNet.merge(key, amount.multiply(firing.count()), BigInteger::add)));
        BigInteger additionalUnits = repetitions.subtract(BigInteger.ONE);
        unitNet.forEach((key, amount) -> {
            if (amount.signum() < 0) {
                required.merge(key, amount.negate().multiply(additionalUnits), BigInteger::add);
            }
        });
        return Object2ObjectMaps.unmodifiable(required);
    }
}
