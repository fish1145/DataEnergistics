package com.fish_dan_.data_energistics.api.registry.tower.energy;

import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegration;

/**
 * Stages energy adapters in the owning annotation plugin's common-setup transaction.
 * Do not retain this registry after the callback; runtime dispatch uses the frozen snapshot.
 */
public interface TowerEnergyIntegrationRegistry {

    /** Registers a non-null adapter with a unique ID; closed transactions and duplicate IDs are rejected. */
    void register(TowerEnergyEndpointIntegration integration);
}
