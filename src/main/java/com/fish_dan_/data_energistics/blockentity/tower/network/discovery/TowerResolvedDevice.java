package com.fish_dan_.data_energistics.blockentity.tower.network.discovery;

import com.fish_dan_.data_energistics.blockentity.tower.network.TowerDeviceKey;

import appeng.api.networking.IGridNode;

/**
 * Runtime device descriptor used for stable per-node allocation and service registration.
 *
 * @param node              physical node retaining its subordinate-grid identity
 * @param key               stable display/persistence key
 * @param registrationOrder target-domain registration order for logical-node fallback
 * @param requiresChannel   whether the device consumes one virtual lease
 */
public record TowerResolvedDevice(IGridNode node,
                                  TowerDeviceKey key,
                                  long registrationOrder,
                                  boolean requiresChannel) {

    /** Validates one resolved runtime device. */
    public TowerResolvedDevice {
        if (registrationOrder < 0) {
            throw new IllegalArgumentException("Device registration order must be non-negative");
        }
    }
}
