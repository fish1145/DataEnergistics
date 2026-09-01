package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Declares all exact lower bounds that one SCC firing vector must satisfy together.
 *
 * @param settledWithdrawals           positive balances consumed after this SCC has completed
 * @param terminalBalanceLowerBounds   positive balances that must remain after every settled withdrawal
 * @param requiredNetChangeLowerBounds positive net production lower bounds independent of initial inventory
 * @param netNewKeys                   scalar targets whose requested amount must retain NET_NEW semantics
 * @param finalBalanceLowerBounds      precomputed balance required before downstream settlement
 */
public record TrinityCycleDemand(
                                 Map<AEKey, BigInteger> settledWithdrawals,
                                 Map<AEKey, BigInteger> terminalBalanceLowerBounds,
                                 Map<AEKey, BigInteger> requiredNetChangeLowerBounds,
                                 Set<AEKey> netNewKeys,
                                 Map<AEKey, BigInteger> finalBalanceLowerBounds) {

    /**
     * Computes the immutable effective final bound once instead of rebuilding it in every solver pass.
     */
    public TrinityCycleDemand(
                              Map<AEKey, BigInteger> settledWithdrawals,
                              Map<AEKey, BigInteger> terminalBalanceLowerBounds,
                              Map<AEKey, BigInteger> requiredNetChangeLowerBounds) {
        this(
                settledWithdrawals,
                terminalBalanceLowerBounds,
                requiredNetChangeLowerBounds,
                Set.of(),
                combineFinalBalances(settledWithdrawals, terminalBalanceLowerBounds));
    }

    public TrinityCycleDemand(
                              Map<AEKey, BigInteger> settledWithdrawals,
                              Map<AEKey, BigInteger> terminalBalanceLowerBounds,
                              Map<AEKey, BigInteger> requiredNetChangeLowerBounds,
                              Set<AEKey> netNewKeys) {
        this(
                settledWithdrawals,
                terminalBalanceLowerBounds,
                requiredNetChangeLowerBounds,
                netNewKeys,
                combineFinalBalances(settledWithdrawals, terminalBalanceLowerBounds));
    }

    /**
     * Compatibility constructor for callers whose lower bounds are already terminal balances.
     */
    public TrinityCycleDemand(
                              Map<AEKey, BigInteger> finalBalanceLowerBounds,
                              Map<AEKey, BigInteger> requiredNetChangeLowerBounds) {
        this(Map.of(), finalBalanceLowerBounds, requiredNetChangeLowerBounds);
    }

    private static Map<AEKey, BigInteger> combineFinalBalances(
                                                               Map<AEKey, BigInteger> settledWithdrawals,
                                                               Map<AEKey, BigInteger> terminalBalanceLowerBounds) {
        LinkedHashMap<AEKey, BigInteger> combined = new LinkedHashMap<>(settledWithdrawals);
        terminalBalanceLowerBounds.forEach((key, amount) -> combined.merge(key, amount, BigInteger::add));
        return Collections.unmodifiableMap(combined);
    }

    /**
     * Adds internal restart reserves without changing delivery or net-new semantics.
     */
    public TrinityCycleDemand withRetainedSeed(Map<AEKey, BigInteger> retainedSeed) {
        LinkedHashMap<AEKey, BigInteger> terminal = new LinkedHashMap<>(terminalBalanceLowerBounds);
        retainedSeed.forEach((key, amount) -> terminal.merge(key, amount, BigInteger::max));
        return new TrinityCycleDemand(
                settledWithdrawals,
                terminal,
                requiredNetChangeLowerBounds,
                netNewKeys);
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
        BigInteger availableTarget = available.getOrDefault(target, BigInteger.ZERO);
        if (quantityMode == CraftingQuantityMode.NET_NEW) {
            return new TrinityCycleDemand(
                    Map.of(),
                    Map.of(),
                    Map.of(target, requestedAmount),
                    Set.of(target));
        }
        BigInteger requiredNet = requestedAmount
                .subtract(availableTarget)
                .max(BigInteger.ZERO)
                .max(BigInteger.ONE);
        return new TrinityCycleDemand(
                Map.of(),
                Map.of(target, requestedAmount),
                Map.of(target, requiredNet));
    }
}
