package com.fish_dan_.data_energistics.blockentity.tower.network.binding;

/**
 * Distinguishes ordinary transfer targets from peer towers that only form logical tower-network membership.
 */
public enum TowerBindingKind {
    TARGET,
    TOWER_PEER
}
