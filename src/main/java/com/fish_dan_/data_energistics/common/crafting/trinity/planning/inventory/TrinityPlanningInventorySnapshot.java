package com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable server-thread capture of the exact inventory keys relevant to one Trinity planning request.
 *
 * <p>
 * AE2 creative cells advertise {@link Integer#MAX_VALUE} through stack listings while their extraction contract
 * can satisfy the complete {@code long} domain. Ordinary small amounts keep the cached-listing fast path; values in
 * the sentinel range are confirmed against the live storage using a non-mutating extraction simulation.
 * </p>
 *
 * @param amounts              positive effective amounts keyed by exact AE identity
 * @param sentinelProbes       number of high cached amounts confirmed against live storage
 * @param effectiveLongMaxKeys number of confirmed keys that can satisfy {@link Long#MAX_VALUE}
 */
public record TrinityPlanningInventorySnapshot(
                                               Map<AEKey, BigInteger> amounts,
                                               int sentinelProbes,
                                               int effectiveLongMaxKeys) {

    /**
     * Captures one request without retaining the mutable network, action source, or cached counter.
     *
     * @param keys            exact graph keys relevant to the request
     * @param cachedInventory current AE2 stack-listing cache
     * @param liveInventory   effective network storage used for sentinel confirmation
     * @param actionSource    request source used by AE2 security checks
     * @return detached immutable planning inventory
     * @throws CaptureException when a high-amount live extraction probe cannot be completed safely
     */
    public static TrinityPlanningInventorySnapshot capture(
                                                           Collection<AEKey> keys,
                                                           KeyCounter cachedInventory,
                                                           MEStorage liveInventory,
                                                           IActionSource actionSource) {
        if (keys == null || cachedInventory == null || liveInventory == null || actionSource == null) {
            throw new IllegalArgumentException("A Trinity planning inventory capture requires complete inputs");
        }
        LinkedHashMap<AEKey, BigInteger> amounts = new LinkedHashMap<>();
        int sentinelProbes = 0;
        int effectiveLongMaxKeys = 0;
        for (AEKey key : keys) {
            if (key == null) {
                throw new IllegalArgumentException("A Trinity planning inventory key cannot be null");
            }
            long amount = cachedInventory.get(key);
            if (amount >= Integer.MAX_VALUE) {
                sentinelProbes = Math.incrementExact(sentinelProbes);
                try {
                    amount = liveInventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
                } catch (RuntimeException exception) {
                    throw new CaptureException(key, exception);
                }
                if (amount < 0L) {
                    throw new CaptureException(
                            key,
                            new IllegalStateException("AE storage returned a negative simulated extraction"));
                }
                if (amount == Long.MAX_VALUE) {
                    effectiveLongMaxKeys = Math.incrementExact(effectiveLongMaxKeys);
                }
            }
            if (amount > 0L) {
                amounts.put(key, BigInteger.valueOf(amount));
            }
        }
        return new TrinityPlanningInventorySnapshot(amounts, sentinelProbes, effectiveLongMaxKeys);
    }

    /**
     * @return empty detached capture used while no immutable crafting graph is published
     */
    public static TrinityPlanningInventorySnapshot empty() {
        return new TrinityPlanningInventorySnapshot(Map.of(), 0, 0);
    }

    /**
     * Freezes captured amounts and validates telemetry counters.
     */
    public TrinityPlanningInventorySnapshot {
        if (amounts == null || sentinelProbes < 0 || effectiveLongMaxKeys < 0) {
            throw new IllegalArgumentException("A Trinity planning inventory snapshot is invalid");
        }
        if (effectiveLongMaxKeys > sentinelProbes) {
            throw new IllegalArgumentException("A Trinity planning inventory snapshot is invalid");
        }
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException(
                        "A Trinity planning inventory snapshot may contain only positive named amounts");
            }
            copied.put(key, amount);
        });
        amounts = Collections.unmodifiableMap(copied);
    }

    /**
     * Identifies the exact key whose live sentinel confirmation failed at the server-thread boundary.
     */
    public static final class CaptureException extends RuntimeException {

        private final AEKey key;

        private CaptureException(AEKey key, RuntimeException cause) {
            super("Failed to confirm a Trinity planning inventory sentinel for " + key, cause);
            this.key = key;
        }

        /**
         * @return exact key whose simulated extraction failed
         */
        public AEKey key() {
            return this.key;
        }
    }
}
