package com.fish_dan_.data_energistics.blockentity.tower.network.discovery;

/**
 * Describes why one grid exposed at an anchor cannot be claimed.
 */
public enum TowerTargetGridFailure {
    NONE,
    PRIMARY_GRID,
    CONTROLLER_PRESENT,
    ALREADY_SUBORDINATE,
    SCOPE_REQUIRES_SINGLE_UNCONNECTED_NODE
}
