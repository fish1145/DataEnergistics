package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declares all exact lower bounds that one SCC firing vector must satisfy together.
 *
 * @param finalBalanceLowerBounds      positive final inventory lower bounds after seed and net change
 * @param requiredNetChangeLowerBounds positive net production lower bounds independent of initial inventory
 */
public record TrinityCycleDemand(
                                 Map<AEKey, BigInteger> finalBalanceLowerBounds,
                                 Map<AEKey, BigInteger> requiredNetChangeLowerBounds) {

    /**
     * Validates and freezes a component-wide demand so solver passes cannot observe caller mutation.
     */
    public TrinityCycleDemand {
        finalBalanceLowerBounds = copyPositiveBounds(
                finalBalanceLowerBounds,
                false,
                "final balance");
        requiredNetChangeLowerBounds = copyPositiveBounds(
                requiredNetChangeLowerBounds,
                true,
                "required net change");
    }

    /**
     * Adapts the legacy single-target quantity semantics to component-wide lower-bound maps.
     *
     * @param target          requested output, which may be an SCC key or a boundary output
     * @param requestedAmount positive requested amount
     * @param quantityMode    net-new or final-total delivery semantics
     * @param available       current non-negative inventory snapshot
     * @return immutable generalized cycle demand
     */
    public static TrinityCycleDemand forTarget(
                                               AEKey target,
                                               BigInteger requestedAmount,
                                               CraftingQuantityMode quantityMode,
                                               Map<AEKey, BigInteger> available) {
        if (target == null || requestedAmount == null || requestedAmount.signum() <= 0 ||
                quantityMode == null || available == null) {
            throw new IllegalArgumentException("A Trinity cycle target demand is incomplete");
        }
        BigInteger availableTarget = available.getOrDefault(target, BigInteger.ZERO);
        if (availableTarget == null || availableTarget.signum() < 0) {
            throw new IllegalArgumentException("Trinity target inventory cannot be negative or null");
        }
        if (quantityMode == CraftingQuantityMode.NET_NEW) {
            return new TrinityCycleDemand(Map.of(), Map.of(target, requestedAmount));
        }
        BigInteger requiredNet = requestedAmount
                .subtract(availableTarget)
                .max(BigInteger.ZERO)
                .max(BigInteger.ONE);
        return new TrinityCycleDemand(
                Map.of(target, requestedAmount),
                Map.of(target, requiredNet));
    }

    private static Map<AEKey, BigInteger> copyPositiveBounds(
                                                             Map<AEKey, BigInteger> source,
                                                             boolean required,
                                                             String description) {
        if (source == null || required && source.isEmpty()) {
            throw new IllegalArgumentException("Trinity " + description + " bounds are incomplete");
        }
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity " + description + " bounds must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
