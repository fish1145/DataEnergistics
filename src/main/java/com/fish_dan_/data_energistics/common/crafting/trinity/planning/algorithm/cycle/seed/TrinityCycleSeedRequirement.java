package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
     * Replays one route unit, including one-time prefix and suffix batches, without observing request inventory.
     *
     * @param selection    completely scheduled cycle selection
     * @param internalKeys keys owned by the selected SCC
     * @return immutable exact restart requirement
     */
    public static TrinityCycleSeedRequirement fromSelection(
                                                            TrinityCycleSelection selection,
                                                            Set<AEKey> internalKeys) {
        if (selection == null || internalKeys == null || internalKeys.isEmpty()) {
            throw new IllegalArgumentException("A Trinity cycle seed proof requires a selection and internal keys");
        }
        ArrayList<TrinityVariantFiring> oneUnit = new ArrayList<>();
        oneUnit.addAll(selection.prefixOrder());
        oneUnit.addAll(selection.localOrder());
        oneUnit.addAll(selection.suffixOrder());
        return new TrinityCycleSeedRequirement(minimumSeed(oneUnit, internalKeys));
    }

    /**
     * Freezes a positive exact reserve.
     */
    public TrinityCycleSeedRequirement {
        if (amounts == null) {
            throw new IllegalArgumentException("A Trinity cycle seed requirement cannot be null");
        }
    }

    private static Map<AEKey, BigInteger> minimumSeed(
                                                      List<TrinityVariantFiring> order,
                                                      Set<AEKey> internalKeys) {
        LinkedHashMap<AEKey, BigInteger> balance = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> seed = new LinkedHashMap<>();
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
                    if (internalKeys.contains(key)) {
                        seed.merge(key, deficit, BigInteger::add);
                    }
                }
            });
            firing.variant().netChange().forEach(
                    (key, amount) -> balance.merge(key, amount.multiply(count), BigInteger::add));
        }
        return Collections.unmodifiableMap(seed);
    }
}
