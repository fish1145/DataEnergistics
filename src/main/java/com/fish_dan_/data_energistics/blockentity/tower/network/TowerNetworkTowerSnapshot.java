package com.fish_dan_.data_energistics.blockentity.tower.network;

import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBindingRuntimeSnapshot;

import java.util.List;

/**
 * Complete immutable domain result published to one active tower.
 *
 * @param revision domain revision used to invalidate protocol/UI caches
 * @param channels primary-grid channel overview
 * @param bindings binding and per-device results
 */
public record TowerNetworkTowerSnapshot(long revision,
                                        TowerChannelOverview channels,
                                        List<TowerBindingRuntimeSnapshot> bindings) {

    /** Validates and defensively copies one tower result. */
    public TowerNetworkTowerSnapshot {
        if (revision < 0) {
            throw new IllegalArgumentException("Tower network snapshot fields are invalid");
        }
        bindings = List.copyOf(bindings);
    }
}
