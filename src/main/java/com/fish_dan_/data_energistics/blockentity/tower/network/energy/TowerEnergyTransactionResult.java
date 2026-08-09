package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;

import java.util.List;

/**
 * Immutable result of one domain FE equalization attempt.
 *
 * @param snapshots     endpoint state frozen before planning
 * @param plannedFe     conserved FE in the calculated plan
 * @param insertedFe    FE successfully delivered during actual execution
 * @param quarantinedFe FE that could not be restored after a runtime failure
 * @param mutated       whether at least one endpoint changed
 * @param failure       concise failure reason, empty after a complete or balanced transaction
 */
public record TowerEnergyTransactionResult(List<TowerEnergyEndpointSnapshot> snapshots,
                                           long plannedFe,
                                           long insertedFe,
                                           long quarantinedFe,
                                           boolean mutated,
                                           String failure) {

    /**
     * Validates counters and defensively copies the frozen snapshots.
     */
    public TowerEnergyTransactionResult {
        if (plannedFe < 0 || insertedFe < 0 || insertedFe > plannedFe || quarantinedFe < 0) {
            throw new IllegalArgumentException("Tower energy transaction counters are invalid");
        }
        snapshots = List.copyOf(snapshots);
    }
}
