package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact multi-route SCC result after joint objective selection and compressed reachability validation.
 *
 * @param firings        exact integer firing vector
 * @param externalInputs minimal boundary inputs under the first lexicographic objective
 * @param minimumSeed    exact prefix seed proven by scheduling
 * @param initialInputs  external inputs plus seed/final-total contribution reserved before execution
 * @param netChange      exact signed effect of all firings
 * @param schedule       compressed executable proof
 * @param searchStates   total firing-box and compressed-schedule states used to prove the result
 * @param solverPasses   completed ojAlgo optimisation passes
 * @param solverNanos    measured time spent in ojAlgo
 * @param quality        exact proof strength retained by this executable plan
 */
public record TrinityJointCyclePlan(
                                    Map<TrinityPatternVariant, BigInteger> firings,
                                    Map<AEKey, BigInteger> externalInputs,
                                    Map<AEKey, BigInteger> minimumSeed,
                                    Map<AEKey, BigInteger> initialInputs,
                                    Map<AEKey, BigInteger> netChange,
                                    TrinityCompressedSchedule schedule,
                                    int searchStates,
                                    int solverPasses,
                                    long solverNanos,
                                    TrinityPlanQuality quality) {

    /**
     * Copies the result and repeats all final BigInteger conservation checks.
     */
    public TrinityJointCyclePlan {
        if (firings == null || firings.isEmpty() || externalInputs == null || minimumSeed == null ||
                initialInputs == null || netChange == null || schedule == null || searchStates <= 0 ||
                solverPasses <= 0 ||
                solverNanos < 0L || quality == null) {
            throw new IllegalArgumentException("A Trinity joint cycle plan requires complete exact accounting");
        }
        firings = copyPositiveFirings(firings);
        externalInputs = copyPositiveAmounts(externalInputs);
        minimumSeed = copyPositiveAmounts(minimumSeed);
        initialInputs = copyPositiveAmounts(initialInputs);
        netChange = copySignedNonZero(netChange);
        if (!schedule.aggregateFirings().equals(firings)) {
            throw new IllegalArgumentException("A Trinity joint cycle schedule must match its firing vector");
        }
        LinkedHashMap<AEKey, BigInteger> calculatedNet = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> calculatedNet.merge(key, amount.multiply(count), BigInteger::add)));
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A Trinity joint cycle net change must equal its firing vector");
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
            throw new IllegalArgumentException("A Trinity joint cycle final balance cannot be negative");
        }
        finalBalances.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!finalBalances.equals(schedule.finalBalances())) {
            throw new IllegalArgumentException("A Trinity joint cycle schedule must conserve its final balances");
        }
    }

    private static void requireIncluded(Map<AEKey, BigInteger> initialInputs,
                                        AEKey key,
                                        BigInteger amount,
                                        String role) {
        if (initialInputs.getOrDefault(key, BigInteger.ZERO).compareTo(amount) < 0) {
            throw new IllegalArgumentException("A Trinity joint cycle initial input must include every " + role);
        }
    }

    private static Map<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                              Map<TrinityPatternVariant, BigInteger> source) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((variant, amount) -> {
            if (variant == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity joint cycle firing count must be positive");
            }
            copied.put(variant, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copyPositiveAmounts(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity joint cycle amount must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copySignedNonZero(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() == 0) {
                throw new IllegalArgumentException("A Trinity joint cycle net amount must be non-zero");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
