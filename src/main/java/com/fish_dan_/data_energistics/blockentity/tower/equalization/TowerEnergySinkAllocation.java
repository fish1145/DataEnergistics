package com.fish_dan_.data_energistics.blockentity.tower.equalization;

/**
 * Planned positive FE deposit into one frozen endpoint.
 *
 * @param endpoint endpoint that must receive the energy
 * @param amount   positive FE amount to deposit
 */
public record TowerEnergySinkAllocation(TowerEnergyEndpointId endpoint, long amount) {

    /**
     * Rejects unusable zero or negative transfer operations at construction time.
     *
     * @param endpoint endpoint that must receive the energy
     * @param amount   positive FE amount to deposit
     */
    public TowerEnergySinkAllocation {
        if (amount <= 0) {
            throw new IllegalArgumentException("Sink allocation amount must be positive");
        }
    }
}
