package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact result of reverse demand propagation through an acyclic condensation region.
 *
 * @param executionOrder input-to-output firing order
 * @param firings        aggregate firing vector
 * @param externalInputs positive amounts actually reserved from the immutable inventory snapshot
 * @param netChange      exact signed transition effect
 * @param statesVisited  graph-dependent propagation states, independent of requested quantity
 */
public record TrinityAcyclicPlan(
                                 List<TrinityVariantFiring> executionOrder,
                                 Map<TrinityPatternVariant, BigInteger> firings,
                                 Map<AEKey, BigInteger> externalInputs,
                                 Map<AEKey, BigInteger> netChange,
                                 int statesVisited) {

    /**
     * Copies every exact amount and checks aggregate ordering consistency.
     */
    public TrinityAcyclicPlan {
        if (executionOrder == null || firings == null || externalInputs == null || netChange == null ||
                statesVisited < 0) {
            throw new IllegalArgumentException("A Trinity acyclic plan requires complete non-negative accounting");
        }
        executionOrder = List.copyOf(executionOrder);
        firings = copyPositiveFirings(firings);
        externalInputs = copyAmounts(externalInputs, false);
        netChange = copyAmounts(netChange, true);
        LinkedHashMap<TrinityPatternVariant, BigInteger> orderedFirings = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : executionOrder) {
            if (firing == null || orderedFirings.put(firing.variant(), firing.count()) != null) {
                throw new IllegalArgumentException("A Trinity acyclic execution order must aggregate each variant");
            }
        }
        if (!orderedFirings.equals(firings)) {
            throw new IllegalArgumentException("A Trinity acyclic execution order must match its firing vector");
        }
    }

    private static Map<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                              Map<TrinityPatternVariant, BigInteger> source) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((variant, count) -> {
            if (variant == null || count == null || count.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity acyclic firing count must be positive");
            }
            copied.put(variant, count);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copyAmounts(Map<AEKey, BigInteger> source, boolean signed) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() == 0 || (!signed && amount.signum() < 0)) {
                throw new IllegalArgumentException("A Trinity acyclic amount is invalid");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
