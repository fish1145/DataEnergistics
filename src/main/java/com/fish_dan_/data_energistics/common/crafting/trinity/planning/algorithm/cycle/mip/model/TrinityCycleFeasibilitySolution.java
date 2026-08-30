package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact decoded result of all sequential feasibility objectives.
 *
 * @param firings          positive stable firing vector
 * @param modelSeed        positive conservation seed lower bounds
 * @param externalInputs   positive conservation boundary-input lower bounds
 * @param solverPasses     number of completed solver passes sharing one deadline
 * @param solverNanos      measured solver time
 * @param radix            whether the base-2^15 exact representation was required
 * @param quality          proof strength of the retained objective vector
 * @param actualInputs     diagnostic-only finite inventory allocated to required reserve
 * @param missingInputs    diagnostic-only virtual reserve absent from captured inventory
 * @param diagnosticStates actual solver calls charged to the local shortage search
 */
public record TrinityCycleFeasibilitySolution(
                                              Map<TrinityPatternVariant, BigInteger> firings,
                                              Map<AEKey, BigInteger> modelSeed,
                                              Map<AEKey, BigInteger> externalInputs,
                                              int solverPasses,
                                              long solverNanos,
                                              boolean radix,
                                              TrinityPlanQuality quality,
                                              Map<AEKey, BigInteger> actualInputs,
                                              Map<AEKey, BigInteger> missingInputs,
                                              int diagnosticStates) {

    /**
     * Copies decoded values before exact conservation and scheduling consume them.
     */
    public TrinityCycleFeasibilitySolution {
        if (firings == null || firings.isEmpty() || modelSeed == null || externalInputs == null ||
                solverPasses <= 0 || solverNanos < 0L || quality == null || actualInputs == null ||
                missingInputs == null || diagnosticStates < 0) {
            throw new IllegalArgumentException("A Trinity feasibility solution requires complete exact accounting");
        }
        firings = copyPositiveFirings(firings);
        modelSeed = copyPositiveAmounts(modelSeed, "seed");
        externalInputs = copyPositiveAmounts(externalInputs, "external input");
        actualInputs = copyPositiveAmounts(actualInputs, "actual input");
        missingInputs = copyPositiveAmounts(missingInputs, "missing input");
    }

    /**
     * Compatibility constructor for ordinary executable feasibility results.
     */
    public TrinityCycleFeasibilitySolution(
                                           Map<TrinityPatternVariant, BigInteger> firings,
                                           Map<AEKey, BigInteger> modelSeed,
                                           Map<AEKey, BigInteger> externalInputs,
                                           int solverPasses,
                                           long solverNanos,
                                           boolean radix,
                                           TrinityPlanQuality quality) {
        this(
                firings,
                modelSeed,
                externalInputs,
                solverPasses,
                solverNanos,
                radix,
                quality,
                Map.of(),
                Map.of(),
                0);
    }

    /**
     * Compatibility constructor for complete lexicographic proofs.
     */
    public TrinityCycleFeasibilitySolution(
                                           Map<TrinityPatternVariant, BigInteger> firings,
                                           Map<AEKey, BigInteger> modelSeed,
                                           Map<AEKey, BigInteger> externalInputs,
                                           int solverPasses,
                                           long solverNanos,
                                           boolean radix) {
        this(
                firings,
                modelSeed,
                externalInputs,
                solverPasses,
                solverNanos,
                radix,
                TrinityPlanQuality.PROVED_OPTIMAL,
                Map.of(),
                Map.of(),
                0);
    }

    /**
     * @return exact positive reserve required by the diagnostic firing vector
     */
    public Map<AEKey, BigInteger> requiredInputs() {
        LinkedHashMap<AEKey, BigInteger> required = new LinkedHashMap<>(externalInputs);
        modelSeed.forEach((key, amount) -> required.merge(key, amount, BigInteger::add));
        return Collections.unmodifiableMap(required);
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
