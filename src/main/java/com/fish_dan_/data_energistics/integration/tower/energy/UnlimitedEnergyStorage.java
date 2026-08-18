package com.fish_dan_.data_energistics.integration.tower.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Exposes a trusted, type-safe long-width energy amount for storages whose public API supports direct mutation.
 *
 * <p>
 * Implementations retain the sided permissions reported through {@link IEnergyStorage}; this contract only bypasses
 * numeric per-call transfer limits.
 */
public interface UnlimitedEnergyStorage extends IEnergyStorage {

    /**
     * Returns the full stored amount without the integer-width capability clamp.
     *
     * @return non-negative stored energy
     */
    long getStoredEnergyLong();

    /**
     * Returns the full storage capacity without the integer-width capability clamp.
     *
     * @return non-negative storage capacity
     */
    long getEnergyCapacityLong();

    /**
     * Replaces the stored amount after {@link UnlimitedEnergyAccess} validates its range.
     *
     * @param amount new stored amount
     */
    void setStoredEnergyLong(long amount);

    /**
     * Publishes a successful direct amount mutation to the underlying storage implementation.
     */
    void onUnlimitedEnergyChanged();
}
