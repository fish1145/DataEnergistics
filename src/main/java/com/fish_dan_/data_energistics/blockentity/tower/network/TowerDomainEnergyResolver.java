package com.fish_dan_.data_energistics.blockentity.tower.network;

import java.util.List;

/**
 * Resolves loaded FE capabilities for domain-level topology snapshots without forcing chunks.
 */
public interface TowerDomainEnergyResolver {

    /**
     * Resolves all distinct capability access routes at one loaded location in stable side order. Multiple routes may
     * share one physical backing identity when their access context differs.
     *
     * @param location candidate location
     * @return immutable endpoint list, or empty when the chunk is unloaded
     */
    List<TowerDomainEnergyEndpoint> resolve(TowerEnergyLocation location);
}
