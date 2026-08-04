package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact executable ordering found without expanding one state per logical firing.
 *
 * @param batches       ordered maximum-safe or balance-breakpoint batches
 * @param finalBalances exact non-negative balance after every firing
 * @param statesVisited compressed search states explored
 */
public record TrinityCompressedSchedule(
                                        List<TrinityVariantFiring> batches,
                                        Map<AEKey, BigInteger> finalBalances,
                                        int statesVisited) {

    /**
     * Copies the schedule and verifies its counters.
     */
    public TrinityCompressedSchedule {
        if (batches == null || finalBalances == null || statesVisited < 0) {
            throw new IllegalArgumentException("A Trinity compressed schedule requires complete accounting");
        }
        batches = List.copyOf(batches);
        for (TrinityVariantFiring batch : batches) {
            if (batch == null) {
                throw new IllegalArgumentException("A Trinity compressed schedule cannot contain a null batch");
            }
        }
        LinkedHashMap<AEKey, BigInteger> copiedBalances = new LinkedHashMap<>();
        finalBalances.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("A Trinity final schedule balance cannot be negative");
            }
            if (amount.signum() > 0) {
                copiedBalances.put(key, amount);
            }
        });
        finalBalances = Collections.unmodifiableMap(copiedBalances);
    }

    /**
     * @return aggregate firing vector represented by all compressed batches
     */
    public Map<TrinityPatternVariant, BigInteger> aggregateFirings() {
        LinkedHashMap<TrinityPatternVariant, BigInteger> aggregate = new LinkedHashMap<>();
        for (TrinityVariantFiring batch : this.batches) {
            aggregate.merge(batch.variant(), batch.count(), BigInteger::add);
        }
        return Collections.unmodifiableMap(aggregate);
    }
}
