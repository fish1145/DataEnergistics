package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Closed-form deterministic production cycle and its exact compressed execution proof.
 *
 * @param oneCycleOrder    stable firing blocks in one logical production cycle
 * @param repetitions      compact complete-cycle count
 * @param aggregateFirings total logical firing vector
 * @param minimumSeed      exact maximum prefix deficit across all repetitions
 * @param initialInputs    exact inventory that must be reserved, including final-total target contribution
 * @param netChange        exact signed effect across all repetitions
 * @param schedule         executable compressed batch proof
 */
public record TrinityCyclePlan(
                               List<TrinityVariantFiring> oneCycleOrder,
                               BigInteger repetitions,
                               Map<TrinityPatternVariant, BigInteger> aggregateFirings,
                               Map<AEKey, BigInteger> minimumSeed,
                               Map<AEKey, BigInteger> initialInputs,
                               Map<AEKey, BigInteger> netChange,
                               TrinityCompressedSchedule schedule) {

    /**
     * Copies the complete cycle accounting and rejects an inconsistent firing vector.
     */
    public TrinityCyclePlan {
        if (oneCycleOrder.isEmpty() || repetitions.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity cycle plan requires complete positive accounting");
        }
        oneCycleOrder = List.copyOf(oneCycleOrder);
        aggregateFirings = copyPositiveFirings(aggregateFirings);
        minimumSeed = copyPositiveAmounts(minimumSeed);
        initialInputs = copyPositiveAmounts(initialInputs);
        netChange = copySignedNonZero(netChange);
        LinkedHashMap<TrinityPatternVariant, BigInteger> expected = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : oneCycleOrder) {
            expected.merge(firing.variant(), firing.count().multiply(repetitions), BigInteger::add);
        }
        if (!expected.equals(aggregateFirings) || !schedule.aggregateFirings().equals(aggregateFirings)) {
            throw new IllegalArgumentException("A Trinity cycle schedule must match its compact firing vector");
        }
        LinkedHashMap<AEKey, BigInteger> calculatedNet = new LinkedHashMap<>();
        aggregateFirings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> calculatedNet.merge(key, amount.multiply(count), BigInteger::add)));
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A Trinity cycle net change must equal its exact firing effects");
        }
        for (Map.Entry<AEKey, BigInteger> seed : minimumSeed.entrySet()) {
            if (initialInputs.getOrDefault(seed.getKey(), BigInteger.ZERO).compareTo(seed.getValue()) < 0) {
                throw new IllegalArgumentException("A Trinity cycle initial input must include every minimum seed");
            }
        }
        LinkedHashMap<AEKey, BigInteger> calculatedFinal = new LinkedHashMap<>(initialInputs);
        netChange.forEach((key, amount) -> calculatedFinal.merge(key, amount, BigInteger::add));
        if (calculatedFinal.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalArgumentException("A Trinity cycle final balance cannot be negative");
        }
        calculatedFinal.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedFinal.equals(schedule.finalBalances())) {
            throw new IllegalArgumentException("A Trinity cycle schedule must conserve its exact final balances");
        }
    }

    private static Map<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                              Map<TrinityPatternVariant, BigInteger> source) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((variant, count) -> {
            if (count.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity cycle firing count must be positive");
            }
            copied.put(variant, count);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copyPositiveAmounts(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity cycle input amount must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copySignedNonZero(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("A Trinity cycle net amount must be non-zero");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
