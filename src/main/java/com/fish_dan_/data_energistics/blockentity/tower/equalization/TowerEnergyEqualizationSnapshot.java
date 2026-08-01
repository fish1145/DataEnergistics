package com.fish_dan_.data_energistics.blockentity.tower.equalization;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordered, immutable collection of endpoint states captured before an equalization pass.
 *
 * <p>
 * Endpoint order is significant: it provides deterministic extraction order and resolves indivisible FE rounding
 * ties. An endpoint identity may therefore occur at most once.
 * </p>
 *
 * @param endpoints endpoint states in stable caller-defined order
 */
public record TowerEnergyEqualizationSnapshot(List<TowerEnergyEndpointSnapshot> endpoints) {

    /**
     * Defensively copies the endpoint order and rejects ambiguous duplicate identities.
     *
     * @param endpoints endpoint states in stable caller-defined order
     */
    public TowerEnergyEqualizationSnapshot {
        endpoints = List.copyOf(endpoints);
        Set<TowerEnergyEndpointId> identities = new HashSet<>();
        for (TowerEnergyEndpointSnapshot endpoint : endpoints) {
            if (!identities.add(endpoint.endpoint())) {
                throw new IllegalArgumentException("Endpoint snapshot contains a duplicate identity: " + endpoint.endpoint());
            }
        }
    }
}
