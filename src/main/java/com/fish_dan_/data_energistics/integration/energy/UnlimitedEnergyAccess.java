package com.fish_dan_.data_energistics.integration.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Provides rate-limit-free, long-width access to an {@link IEnergyStorage} while preserving its declared direction
 * permissions.
 *
 * <p>
 * A Data Distribution Tower uses this boundary only after resolving a valid sided capability. Implementations may
 * bypass numeric receive and extract limits, but must never bypass {@link IEnergyStorage#canReceive()} or
 * {@link IEnergyStorage#canExtract()}.
 */
public interface UnlimitedEnergyAccess {

    /**
     * Sentinel returned when the inspected storage cannot be accessed or mutated safely through the unlimited path.
     */
    long UNAVAILABLE = Long.MIN_VALUE;

    /**
     * Reads the full stored amount when a verified long-width representation is available.
     *
     * @param storage energy storage being inspected
     * @return full stored amount, or the standard capability value when no safe direct representation is available
     */
    long stored(IEnergyStorage storage);

    /**
     * Reads the full capacity when a verified long-width representation is available.
     *
     * @param storage energy storage being inspected
     * @return full capacity, or the standard capability value when no safe direct representation is available
     */
    long capacity(IEnergyStorage storage);

    /**
     * Checks whether the sided capability permits insertion.
     *
     * @param storage energy storage being inspected
     * @return the capability's receive permission
     */
    boolean canReceive(IEnergyStorage storage);

    /**
     * Checks whether the sided capability permits extraction.
     *
     * @param storage energy storage being inspected
     * @return the capability's extract permission
     */
    boolean canExtract(IEnergyStorage storage);

    /**
     * Inserts energy without applying a numeric per-call transfer limit.
     *
     * @param storage  target sided capability
     * @param amount   non-negative amount requested
     * @param simulate whether the mutation should only be simulated
     * @return inserted amount in {@code [0, amount]}, or {@link #UNAVAILABLE} when direct access is unsafe
     */
    long insert(IEnergyStorage storage, long amount, boolean simulate);

    /**
     * Extracts energy without applying a numeric per-call transfer limit.
     *
     * @param storage  source sided capability
     * @param amount   non-negative amount requested
     * @param simulate whether the mutation should only be simulated
     * @return extracted amount in {@code [0, amount]}, or {@link #UNAVAILABLE} when direct access is unsafe
     */
    long extract(IEnergyStorage storage, long amount, boolean simulate);

    /**
     * Invokes known storage change callbacks after a successful direct mutation.
     *
     * @param storage mutated sided capability
     */
    void notifyStorageChanged(IEnergyStorage storage);
}
