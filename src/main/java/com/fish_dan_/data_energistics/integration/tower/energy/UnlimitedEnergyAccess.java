package com.fish_dan_.data_energistics.integration.tower.energy;

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
     * Sentinel returned only when the inspected storage has no direct unlimited-access plan.
     *
     * <p>
     * Callers may use the standard capability after this result. Once a direct plan has been selected, failures are
     * reported with {@link UnlimitedEnergyAccessException} instead so callers cannot apply an unsafe second mutation.
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
     * Reads stored amount and capacity from one consistent access pass.
     *
     * <p>
     * Implementations capture both values together so callers do not perform two independent state validations.
     * </p>
     *
     * @param storage energy storage being inspected
     * @return paired stored amount and capacity
     */
    EnergySnapshot snapshot(IEnergyStorage storage);

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
     * @return inserted amount in {@code [0, amount]}, or {@link #UNAVAILABLE} when no direct plan exists
     * @throws UnlimitedEnergyAccessException when a selected direct plan fails or cannot be verified
     */
    long insert(IEnergyStorage storage, long amount, boolean simulate);

    /**
     * Extracts energy without applying a numeric per-call transfer limit.
     *
     * @param storage  source sided capability
     * @param amount   non-negative amount requested
     * @param simulate whether the mutation should only be simulated
     * @return extracted amount in {@code [0, amount]}, or {@link #UNAVAILABLE} when no direct plan exists
     * @throws UnlimitedEnergyAccessException when a selected direct plan fails or cannot be verified
     */
    long extract(IEnergyStorage storage, long amount, boolean simulate);

    /**
     * Restores energy removed by the immediately preceding extraction when a downstream receiver short-writes.
     *
     * <p>
     * This is compensation, not a receive operation: it may restore a source-only storage to its prior amount, but
     * it must never increase the storage above the state that existed before the failed transfer.
     *
     * @param storage source storage whose extraction is being compensated
     * @param amount  non-negative extracted amount that was not delivered
     * @return restored amount in {@code [0, amount]}, or {@link #UNAVAILABLE} when the source has no direct mutation
     *         plan
     * @throws UnlimitedEnergyAccessException when a selected direct plan fails or cannot be verified
     */
    long rollbackExtraction(IEnergyStorage storage, long amount);

    /**
     * Invokes known storage change callbacks after a successful direct mutation.
     *
     * @param storage mutated sided capability
     */
    void notifyStorageChanged(IEnergyStorage storage);

    /** Immutable paired energy state captured from one endpoint. */
    record EnergySnapshot(long stored, long capacity) {}
}
