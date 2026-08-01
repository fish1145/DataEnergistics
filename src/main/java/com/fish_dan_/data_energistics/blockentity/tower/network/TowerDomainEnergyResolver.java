package com.fish_dan_.data_energistics.blockentity.tower.network;

import java.util.List;

/**
 * Resolves loaded FE capabilities for domain-level topology snapshots without forcing chunks.
 */
public interface TowerDomainEnergyResolver {

    /**
     * Resolves all distinct accessible storages at one loaded location in stable side order.
     *
     * @param location candidate location
     * @return immutable endpoint list, or empty when the chunk is unloaded
     */
    List<TowerDomainEnergyEndpoint> resolve(TowerEnergyLocation location);
}
