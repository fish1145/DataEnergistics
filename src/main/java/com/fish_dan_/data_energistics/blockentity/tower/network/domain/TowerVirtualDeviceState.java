package com.fish_dan_.data_energistics.blockentity.tower.network.domain;

/**
 * User-facing state of one resolved or pending virtual device.
 */
public enum TowerVirtualDeviceState {
    ALLOCATED,
    WAITING_CHANNEL,
    DISABLED,
    WAITING_TARGET,
    CONFLICT,
    BRIDGE_ERROR
}
