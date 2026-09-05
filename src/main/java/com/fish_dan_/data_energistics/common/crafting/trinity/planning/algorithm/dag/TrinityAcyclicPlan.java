package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.math.BigInteger;
import java.util.Collections;
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
 * @param quality        exact proof strength retained by this plan
 */
public record TrinityAcyclicPlan(
                                 List<TrinityVariantFiring> executionOrder,
                                 Map<TrinityPatternVariant, BigInteger> firings,
                                 Map<AEKey, BigInteger> externalInputs,
                                 Map<AEKey, BigInteger> netChange,
                                 int statesVisited,
                                 TrinityPlanQuality quality) {

    /**
     * Copies every exact amount and checks aggregate ordering consistency.
     */
    public TrinityAcyclicPlan {
        if (statesVisited < 0) {
            throw new IllegalArgumentException("A Trinity acyclic plan requires complete non-negative accounting");
        }
        executionOrder = List.copyOf(executionOrder);
        firings = copyPositiveFirings(firings);
        externalInputs = copyAmounts(externalInputs, false);
        netChange = copyAmounts(netChange, true);
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> orderedFirings = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityVariantFiring firing : executionOrder) {
            if (orderedFirings.put(firing.variant(), firing.count()) != null) {
                throw new IllegalArgumentException("A Trinity acyclic execution order must aggregate each variant");
            }
        }
        if (!orderedFirings.equals(firings)) {
            throw new IllegalArgumentException("A Trinity acyclic execution order must match its firing vector");
        }
    }

    /**
     * Returns the same validated execution accounting with a weaker, explicit proof quality.
     */
    public TrinityAcyclicPlan withQuality(TrinityPlanQuality value) {
        return new TrinityAcyclicPlan(
                this.executionOrder,
                this.firings,
                this.externalInputs,
                this.netChange,
                this.statesVisited,
                value);
    }

    private static Map<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                              Map<TrinityPatternVariant, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((variant, count) -> {
            if (count.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity acyclic firing count must be positive");
            }
            copied.put(variant, count);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copyAmounts(Map<AEKey, BigInteger> source, boolean signed) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() == 0 || (!signed && amount.signum() < 0)) {
                throw new IllegalArgumentException("A Trinity acyclic amount is invalid");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
