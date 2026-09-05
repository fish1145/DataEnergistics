package com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory;

import com.fish_dan_.data_energistics.ae2.grid.FiniteNetworkStorageAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressMeasure;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressPhase;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressReporter;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressSnapshot;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.math.BigInteger;
import java.util.Collection;

/**
 * Immutable server-thread capture of the exact inventory keys relevant to one Trinity planning request.
 *
 * <p>
 * Every relevant key is inspected across concrete mounts so a finite BigInteger total and a confirmed non-consuming
 * source remain distinct planning states without inferring capabilities from cached numeric sentinels.
 * </p>
 *
 * @param inventory      exact finite/unlimited inventory
 * @param sentinelProbes number of high cached keys inspected across concrete mounts
 */
public record TrinityPlanningInventorySnapshot(
                                               TrinityPlanningInventory inventory,
                                               int sentinelProbes) {

    /**
     * Captures one request without retaining the mutable network, action source, or cached counter.
     *
     * @param keys          exact graph keys relevant to the request
     * @param liveInventory effective network storage used for exact mount inspection
     * @param actionSource  request source used by AE2 security checks
     * @return detached immutable planning inventory
     * @throws CaptureException when a high-amount live extraction probe cannot be completed safely
     */
    public static TrinityPlanningInventorySnapshot capture(
                                                           Collection<AEKey> keys,
                                                           MEStorage liveInventory,
                                                           IActionSource actionSource,
                                                           TrinityPlanningProgressReporter progress) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> finiteAmounts = new Object2ObjectLinkedOpenHashMap<>();
        ObjectOpenHashSet<AEKey> unlimitedKeys = new ObjectOpenHashSet<>();
        int totalKeys = keys.size();
        progress.publish(totalKeys == 0 ?
                TrinityPlanningProgressSnapshot.withoutUnits(
                        TrinityPlanningProgressPhase.CAPTURING_INPUT,
                        TrinityPlanningProgressMeasure.NONE) :
                TrinityPlanningProgressSnapshot.exact(TrinityPlanningProgressPhase.CAPTURING_INPUT, 0, totalKeys));
        int sentinelProbes = 0;
        for (AEKey key : keys) {
            sentinelProbes = Math.incrementExact(sentinelProbes);
            try {
                if (!(liveInventory instanceof FiniteNetworkStorageAccess storageAccess)) {
                    throw new IllegalStateException("AE network storage does not expose exact mount availability");
                }
                TrinityAvailableAmount exact = storageAccess.exactAvailability(key, actionSource);
                if (exact.unlimited()) {
                    unlimitedKeys.add(key);
                } else {
                    BigInteger finite = ((TrinityAvailableAmount.Finite) exact).amount();
                    if (finite.signum() > 0) {
                        finiteAmounts.put(key, finite);
                    }
                }
            } catch (RuntimeException exception) {
                throw new CaptureException(key, exception);
            }
            if ((sentinelProbes & 31) == 0 || sentinelProbes == totalKeys) {
                progress.publish(TrinityPlanningProgressSnapshot.exact(
                        TrinityPlanningProgressPhase.CAPTURING_INPUT,
                        sentinelProbes,
                        totalKeys));
            }
        }
        return new TrinityPlanningInventorySnapshot(
                TrinityPlanningInventory.frozen(finiteAmounts, unlimitedKeys),
                sentinelProbes);
    }

    /**
     * @return empty detached capture used while no immutable crafting graph is published
     */
    public static TrinityPlanningInventorySnapshot empty() {
        return new TrinityPlanningInventorySnapshot(TrinityPlanningInventory.empty(), 0);
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
