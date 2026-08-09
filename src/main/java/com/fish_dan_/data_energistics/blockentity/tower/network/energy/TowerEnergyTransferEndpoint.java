package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;

/**
 * Type-safe transaction boundary for one capability or Applied Flux energy endpoint.
 *
 * <p>
 * Implementations must check that their backing chunk or network is still available before every query and
 * mutation. Returned transfer amounts must remain within the requested range.
 * </p>
 */
public interface TowerEnergyTransferEndpoint {

    /**
     * Returns the stable identity used by the proportional equalization plan.
     *
     * @return endpoint identity
     */
    TowerEnergyEndpointId endpoint();

    /**
     * Freezes stored energy, capacity, current transfer permissions, planning role, and the transfer budget available
     * to one transaction without mutation.
     *
     * @return immutable endpoint snapshot
     */
    TowerEnergyEndpointSnapshot freeze();

    /**
     * Simulates the complete planned extraction.
     *
     * @param amount non-negative requested FE
     * @return extractable FE in {@code [0, amount]}
     */
    long simulateExtraction(long amount);

    /**
     * Performs the complete planned extraction.
     *
     * @param amount non-negative requested FE
     * @return extracted FE in {@code [0, amount]}
     */
    long extract(long amount);

    /**
     * Restores energy removed by the current transaction, even for a source-only endpoint when a verified direct
     * state writer is available.
     *
     * @param amount non-negative FE to restore
     * @return restored FE in {@code [0, amount]}
     */
    long compensateExtraction(long amount);

    /**
     * Simulates the complete planned insertion.
     *
     * @param amount non-negative requested FE
     * @return acceptable FE in {@code [0, amount]}
     */
    long simulateInsertion(long amount);

    /**
     * Performs the complete planned insertion.
     *
     * @param amount non-negative requested FE
     * @return inserted FE in {@code [0, amount]}
     */
    long insert(long amount);

    /**
     * Publishes callbacks required by a successful or partially successful mutation.
     */
    void publishMutation();

    /**
     * Returns a concise endpoint description for rate-limited transaction diagnostics.
     *
     * @return diagnostic description
     */
    String description();
}
