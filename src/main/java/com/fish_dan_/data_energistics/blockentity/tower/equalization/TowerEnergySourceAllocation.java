package com.fish_dan_.data_energistics.blockentity.tower.equalization;

/**
 * Planned positive FE withdrawal from one frozen endpoint.
 *
 * @param endpoint endpoint that must provide the energy
 * @param amount   positive FE amount to withdraw
 */
public record TowerEnergySourceAllocation(TowerEnergyEndpointId endpoint, long amount) {

    /**
     * Rejects unusable zero or negative transfer operations at construction time.
     *
     * @param endpoint endpoint that must provide the energy
     * @param amount   positive FE amount to withdraw
     */
    public TowerEnergySourceAllocation {
        if (amount <= 0) {
            throw new IllegalArgumentException("Source allocation amount must be positive");
        }
    }
}
