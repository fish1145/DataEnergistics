package com.fish_dan_.data_energistics.blockentity.tower.equalization;

import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyDirection;

/**
 * Frozen scalar state of one tower energy endpoint used by the equalization planner.
 *
 * @param endpoint  stable endpoint identity
 * @param stored    FE stored when the snapshot was captured
 * @param capacity  maximum FE the endpoint can store
 * @param direction transfer permissions captured with the scalar state
 */
public record TowerEnergyEndpointSnapshot(TowerEnergyEndpointId endpoint, long stored, long capacity,
                                          TowerEnergyDirection direction) {

    /**
     * Validates the energy bounds and required endpoint metadata at the snapshot boundary.
     *
     * @param endpoint  stable endpoint identity
     * @param stored    FE stored when the snapshot was captured
     * @param capacity  maximum FE the endpoint can store
     * @param direction transfer permissions captured with the scalar state
     */
    public TowerEnergyEndpointSnapshot {
        if (stored < 0) {
            throw new IllegalArgumentException("Stored energy must not be negative");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("Energy capacity must not be negative");
        }
        if (stored > capacity) {
            throw new IllegalArgumentException("Stored energy must not exceed capacity");
        }
    }
}
