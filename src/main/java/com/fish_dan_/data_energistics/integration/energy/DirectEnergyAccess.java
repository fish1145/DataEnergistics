package com.fish_dan_.data_energistics.integration.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Adapter for energy storages that expose usable capacity through non-standard APIs.
 *
 * <p>
 * Data Distribution Tower needs this boundary because some optional integrations can receive energy but report
 * {@link IEnergyStorage#canReceive()} as unavailable. Keeping the access behind this interface prevents optional-mod
 * reflection from leaking into core tower transfer logic.
 */
public interface DirectEnergyAccess {

    /**
     * Sentinel returned when an adapter cannot safely write to the inspected storage.
     */
    long INSERT_UNAVAILABLE = Long.MIN_VALUE;

    /**
     * Returns whether the given storage has a supported direct receive path.
     *
     * @param storage energy storage being inspected
     * @return true when direct insertion can be attempted
     */
    boolean canReceive(IEnergyStorage storage);

    /**
     * Attempts to insert energy through the supported direct receive path.
     *
     * @param storage  target storage
     * @param amount   amount requested
     * @param simulate whether the write should be simulated
     * @return inserted amount, or {@link #INSERT_UNAVAILABLE} if direct insertion is not safe
     */
    long insert(IEnergyStorage storage, long amount, boolean simulate);

    /**
     * Notifies a storage object after a successful direct write.
     *
     * @param storage storage object to notify
     */
    void notifyStorageChanged(IEnergyStorage storage);
}
