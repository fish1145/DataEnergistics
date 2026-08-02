package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact decoded result of all sequential feasibility objectives.
 *
 * @param firings        positive stable firing vector
 * @param modelSeed      positive conservation seed lower bounds
 * @param externalInputs positive conservation boundary-input lower bounds
 * @param solverPasses   number of completed solver passes sharing one deadline
 * @param solverNanos    measured solver time
 * @param radix          whether the base-2^15 exact representation was required
 */
public record TrinityCycleFeasibilitySolution(
                                              Map<TrinityPatternVariant, BigInteger> firings,
                                              Map<AEKey, BigInteger> modelSeed,
                                              Map<AEKey, BigInteger> externalInputs,
                                              int solverPasses,
                                              long solverNanos,
                                              boolean radix) {

    /**
     * Copies decoded values before exact conservation and scheduling consume them.
     */
    public TrinityCycleFeasibilitySolution {
        if (firings == null || firings.isEmpty() || modelSeed == null || externalInputs == null ||
                solverPasses <= 0 || solverNanos < 0L) {
            throw new IllegalArgumentException("A Trinity feasibility solution requires complete exact accounting");
        }
        firings = copyPositiveFirings(firings);
        modelSeed = copyPositiveAmounts(modelSeed, "seed");
        externalInputs = copyPositiveAmounts(externalInputs, "external input");
    }

    /**
     * @return exact model external-input objective
     */
    public BigInteger externalTotal() {
        return total(externalInputs);
    }

    /**
     * @return exact model seed objective
     */
    public BigInteger seedTotal() {
        return total(modelSeed);
    }

    /**
     * @return exact logical firing objective
     */
    public BigInteger firingTotal() {
        return total(firings);
    }

    private static Map<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                              Map<TrinityPatternVariant, BigInteger> source) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((variant, amount) -> {
            if (variant == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity feasibility firing count must be positive");
            }
            copied.put(variant, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copyPositiveAmounts(Map<AEKey, BigInteger> source, String role) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity feasibility " + role + " must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static BigInteger total(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
    }
}
