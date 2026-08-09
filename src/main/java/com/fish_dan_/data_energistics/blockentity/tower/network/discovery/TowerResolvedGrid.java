package com.fish_dan_.data_energistics.blockentity.tower.network.discovery;

import appeng.api.networking.IGrid;

import java.util.List;

/**
 * Result for one distinct AE grid exposed by an anchor.
 *
 * @param grid    target grid identity
 * @param devices all loaded local members in stable allocation order
 * @param failure validation result
 */
public record TowerResolvedGrid(IGrid grid,
                                List<TowerResolvedDevice> devices,
                                TowerTargetGridFailure failure) {

    /** Validates and defensively copies one grid result. */
    public TowerResolvedGrid {
        devices = List.copyOf(devices);
    }

    /**
     * Tests whether this target passed local topology validation.
     *
     * @return whether ownership/lease processing may continue
     */
    public boolean accepted() {
        return this.failure == TowerTargetGridFailure.NONE;
    }
}
