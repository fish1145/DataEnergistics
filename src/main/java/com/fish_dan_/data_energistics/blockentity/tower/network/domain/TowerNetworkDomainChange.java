package com.fish_dan_.data_energistics.blockentity.tower.network.domain;

/**
 * Identifies events that invalidate one tower network domain snapshot.
 */
public enum TowerNetworkDomainChange {
    PHYSICAL_NODE,
    VIRTUAL_MEMBERSHIP,
    TOWER,
    BINDING,
    MODE,
    CAPABILITY,
    CHUNK,
    ENERGY_ENDPOINT
}
