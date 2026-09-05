package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;

import java.math.BigInteger;
import java.util.Map;

/**
 * Read-only inventory allocation for an exactly verified candidate, not an optimization objective.
 *
 * @param actualInputs  owned positive allocation map; ownership is transferred to this value
 * @param missingInputs owned positive shortage map; ownership is transferred to this value
 */
public record TrinityShortageInputAllocation(
                                             Object2ObjectMap<AEKey, BigInteger> actualInputs,
                                             Object2ObjectMap<AEKey, BigInteger> missingInputs) {

    public TrinityShortageInputAllocation {
        actualInputs = Object2ObjectMaps.unmodifiable(actualInputs);
        missingInputs = Object2ObjectMaps.unmodifiable(missingInputs);
    }

    /**
     * Allocates the disjoint, already verified seed/external partitions without a merged intermediate map.
     * A craftable predecessor uses captured stock only when it fully covers this input, matching graph aggregation.
     */
    public static TrinityShortageInputAllocation from(
                                                      TrinityCycleFeasibilityRequest request,
                                                      Map<AEKey, BigInteger> seed,
                                                      Map<AEKey, BigInteger> external) {
        Object2ObjectMap<AEKey, BigInteger> actual = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectMap<AEKey, BigInteger> missing = new Object2ObjectLinkedOpenHashMap<>();
        allocate(request, external, actual, missing);
        allocate(request, seed, actual, missing);
        return new TrinityShortageInputAllocation(actual, missing);
    }

    private static void allocate(
                                 TrinityCycleFeasibilityRequest request,
                                 Map<AEKey, BigInteger> required,
                                 Object2ObjectMap<AEKey, BigInteger> actual,
                                 Object2ObjectMap<AEKey, BigInteger> missing) {
        required.forEach((key, amount) -> {
            BigInteger stored = request.available().getOrDefault(key, BigInteger.ZERO);
            if (request.producibleInputs().contains(key) && stored.compareTo(amount) < 0) return;
            BigInteger used = amount.min(stored);
            BigInteger absent = amount.subtract(used);
            if (used.signum() > 0) actual.put(key, used);
            if (absent.signum() > 0) missing.put(key, absent);
        });
    }
}
