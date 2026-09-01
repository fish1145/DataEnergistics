package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact component-wide result for a unique-producer SCC whose residual dependencies are acyclic.
 *
 * @param firings       aggregate firing vector for the productive cycle and every demanded residual output
 * @param minimumSeed   exact maximum prefix deficit for the selected compressed order
 * @param initialInputs exact reserved inputs after applying final-balance lower bounds
 * @param netChange     exact signed effect of the aggregate firing vector
 * @param schedule      executable compressed proof of the complete component plan
 */
public record TrinityDeterministicComponentPlan(
                                                Map<TrinityPatternVariant, BigInteger> firings,
                                                Map<AEKey, BigInteger> minimumSeed,
                                                Map<AEKey, BigInteger> initialInputs,
                                                Map<AEKey, BigInteger> netChange,
                                                TrinityCompressedSchedule schedule) {

    /**
     * Freezes all accounting and rejects a plan whose schedule or conservation equation differs from its vector.
     */
    public TrinityDeterministicComponentPlan {
        if (firings.isEmpty()) {
            throw new IllegalArgumentException("A deterministic Trinity component plan requires complete accounting");
        }
        firings = copyPositiveFirings(firings);
        minimumSeed = copyPositiveAmounts(minimumSeed);
        initialInputs = copyPositiveAmounts(initialInputs);
        netChange = copySignedAmounts(netChange);
        Map<AEKey, BigInteger> copiedInitialInputs = initialInputs;
        if (!schedule.aggregateFirings().equals(firings)) {
            throw new IllegalArgumentException("A deterministic Trinity component schedule must match its firing vector");
        }

        LinkedHashMap<AEKey, BigInteger> calculatedNet = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> calculatedNet.merge(key, amount.multiply(count), BigInteger::add)));
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A deterministic Trinity component net change must match its vector");
        }
        minimumSeed.forEach((key, amount) -> {
            if (copiedInitialInputs.getOrDefault(key, BigInteger.ZERO).compareTo(amount) < 0) {
                throw new IllegalArgumentException("A deterministic Trinity component input must include its seed");
            }
        });

        LinkedHashMap<AEKey, BigInteger> finalBalances = new LinkedHashMap<>(initialInputs);
        netChange.forEach((key, amount) -> finalBalances.merge(key, amount, BigInteger::add));
        if (finalBalances.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalArgumentException("A deterministic Trinity component final balance cannot be negative");
        }
        finalBalances.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!finalBalances.equals(schedule.finalBalances())) {
            throw new IllegalArgumentException("A deterministic Trinity component schedule must conserve its balances");
        }
    }

    private static Map<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                              Map<TrinityPatternVariant, BigInteger> source) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((variant, count) -> {
            if (count.signum() <= 0) {
                throw new IllegalArgumentException("A deterministic Trinity component firing must be positive");
            }
            copied.put(variant, count);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copyPositiveAmounts(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A deterministic Trinity component amount must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copySignedAmounts(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("A deterministic Trinity component net amount must be non-zero");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
