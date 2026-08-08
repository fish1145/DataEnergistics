package com.fish_dan_.data_energistics.blockentity.tower;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Performs active FE balancing for a Data Distribution Tower cluster.
 *
 * <p>
 * The distributor owns transfer scan caches, simulated extraction caches, and round-robin cursors so the block entity
 * can expose energy capability behavior without embedding the transfer algorithm.
 */
public interface TowerEnergyDistributor {

    /**
     * Runs one active range transfer tick for the cluster coordinator.
     *
     * @return true when at least one FE transfer completed
     */
    boolean performActiveRangeTransfer();

    /**
     * Attempts to deliver energy retained by the owning tower after an incomplete transfer.
     *
     * @return true when buffered FE was delivered
     */
    boolean flushBufferedEnergy();

    /**
     * Inserts FE into receiver endpoints in range.
     *
     * @param amount      requested FE amount
     * @param simulate    true for simulation
     * @param excludedPos target position to exclude, or null
     * @return inserted amount
     */
    long distributeEnergyInRange(long amount, boolean simulate, @Nullable BlockPos excludedPos);

    /**
     * Extracts FE from source endpoints and optional AE flux storage.
     *
     * @param amount      requested FE amount
     * @param simulate    true for simulation
     * @param excludedPos target position to exclude, or null
     * @return extracted amount clamped to integer storage limits
     */
    int extractEnergyFromRange(int amount, boolean simulate, @Nullable BlockPos excludedPos);

    /**
     * Returns total extractable FE for UI/capability queries.
     *
     * @param excludedPos target position to exclude, or null
     * @return extractable FE
     */
    long getTotalExtractableEnergy(@Nullable BlockPos excludedPos);

    /**
     * Returns total FE capacity for source endpoints.
     *
     * @param excludedPos target position to exclude, or null
     * @return FE capacity
     */
    long getTotalEnergyCapacity(@Nullable BlockPos excludedPos);

    /**
     * Returns the FE that receiver endpoints can currently accept.
     *
     * @param excludedPos target position to exclude, or null
     * @return currently receivable FE
     */
    long getTotalReceivableEnergy(@Nullable BlockPos excludedPos);

    /**
     * Checks whether any receiver endpoint is available.
     *
     * @param excludedPos target position to exclude, or null
     * @return true when energy can be inserted somewhere
     */
    boolean hasAnyReceiver(@Nullable BlockPos excludedPos);

    /**
     * Checks whether any source endpoint or AE flux source is available.
     *
     * @param excludedPos target position to exclude, or null
     * @return true when energy can be extracted somewhere
     */
    boolean hasAnySource(@Nullable BlockPos excludedPos);

    /**
     * Clears transfer and query caches after storage state changes.
     */
    void invalidateEnergyQueryCache();

    /**
     * Clears cursor state and dependent transfer caches after endpoint topology changes.
     */
    void invalidateResolvedEndpointCache();

    /**
     * Trims bounded caches used by round-robin and query summaries.
     */
    void trimCaches();
}
