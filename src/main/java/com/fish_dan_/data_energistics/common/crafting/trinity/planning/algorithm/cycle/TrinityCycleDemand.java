package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.math.BigInteger;
import java.util.Collections;
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

    /** Computes the effective final bound while retaining explicit net-new target semantics. */
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

    private static Map<AEKey, BigInteger> combineFinalBalances(
                                                               Map<AEKey, BigInteger> settledWithdrawals,
                                                               Map<AEKey, BigInteger> terminalBalanceLowerBounds) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> combined = new Object2ObjectLinkedOpenHashMap<>(settledWithdrawals);
        terminalBalanceLowerBounds.forEach((key, amount) -> combined.merge(key, amount, BigInteger::add));
        return Collections.unmodifiableMap(combined);
    }

    /**
     * Adds internal restart reserves without changing delivery or net-new semantics.
     */
    public TrinityCycleDemand withRetainedSeed(Map<AEKey, BigInteger> retainedSeed) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> terminal = new Object2ObjectLinkedOpenHashMap<>(terminalBalanceLowerBounds);
        retainedSeed.forEach((key, amount) -> terminal.merge(key, amount, BigInteger::max));
        return new TrinityCycleDemand(
                settledWithdrawals,
                terminal,
                requiredNetChangeLowerBounds,
                netNewKeys);
    }
}
