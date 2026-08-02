package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact multi-route SCC result after sequential MIP objectives and compressed reachability validation.
 *
 * @param firings        exact integer firing vector
 * @param externalInputs minimal boundary inputs under the first lexicographic objective
 * @param minimumSeed    exact prefix seed proven by scheduling
 * @param initialInputs  external inputs plus seed/final-total contribution reserved before execution
 * @param netChange      exact signed effect of all firings
 * @param schedule       compressed executable proof
 * @param solverPasses   completed ojAlgo optimisation passes
 * @param solverNanos    measured time spent in ojAlgo
 */
public record TrinityMipCyclePlan(
                                  Map<TrinityPatternVariant, BigInteger> firings,
                                  Map<AEKey, BigInteger> externalInputs,
                                  Map<AEKey, BigInteger> minimumSeed,
                                  Map<AEKey, BigInteger> initialInputs,
                                  Map<AEKey, BigInteger> netChange,
                                  TrinityCompressedSchedule schedule,
                                  int solverPasses,
                                  long solverNanos) {

    /**
     * Copies the result and repeats all final BigInteger conservation checks.
     */
    public TrinityMipCyclePlan {
        if (firings == null || firings.isEmpty() || externalInputs == null || minimumSeed == null ||
                initialInputs == null || netChange == null || schedule == null || solverPasses < 3 ||
                solverNanos < 0L) {
            throw new IllegalArgumentException("A Trinity MIP cycle plan requires complete exact accounting");
        }
        firings = copyPositiveFirings(firings);
        externalInputs = copyPositiveAmounts(externalInputs);
        minimumSeed = copyPositiveAmounts(minimumSeed);
        initialInputs = copyPositiveAmounts(initialInputs);
        netChange = copySignedNonZero(netChange);
        if (!schedule.aggregateFirings().equals(firings)) {
            throw new IllegalArgumentException("A Trinity MIP schedule must match its firing vector");
        }
        LinkedHashMap<AEKey, BigInteger> calculatedNet = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> calculatedNet.merge(key, amount.multiply(count), BigInteger::add)));
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A Trinity MIP net change must equal its firing vector");
        }
        for (Map.Entry<AEKey, BigInteger> external : externalInputs.entrySet()) {
            requireIncluded(initialInputs, external.getKey(), external.getValue(), "external input");
        }
        for (Map.Entry<AEKey, BigInteger> seed : minimumSeed.entrySet()) {
            requireIncluded(initialInputs, seed.getKey(), seed.getValue(), "minimum seed");
        }
        LinkedHashMap<AEKey, BigInteger> finalBalances = new LinkedHashMap<>(initialInputs);
        netChange.forEach((key, amount) -> finalBalances.merge(key, amount, BigInteger::add));
        if (finalBalances.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalArgumentException("A Trinity MIP final balance cannot be negative");
        }
        finalBalances.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!finalBalances.equals(schedule.finalBalances())) {
            throw new IllegalArgumentException("A Trinity MIP schedule must conserve its final balances");
        }
    }

    private static void requireIncluded(Map<AEKey, BigInteger> initialInputs,
                                        AEKey key,
                                        BigInteger amount,
                                        String role) {
        if (initialInputs.getOrDefault(key, BigInteger.ZERO).compareTo(amount) < 0) {
            throw new IllegalArgumentException("A Trinity MIP initial input must include every " + role);
        }
    }

    private static Map<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                              Map<TrinityPatternVariant, BigInteger> source) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((variant, amount) -> {
            if (variant == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity MIP firing count must be positive");
            }
            copied.put(variant, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copyPositiveAmounts(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity MIP amount must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copySignedNonZero(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() == 0) {
                throw new IllegalArgumentException("A Trinity MIP net amount must be non-zero");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
