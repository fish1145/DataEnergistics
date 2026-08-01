package com.fish_dan_.data_energistics.blockentity.tower.network;

import appeng.api.networking.IGridNode;

import java.util.List;

/**
 * Complete six-face capability result for one binding anchor.
 *
 * @param exposedNodes identity-de-duplicated nodes authorized through the anchor capability, including tower-only
 *                     mounted-device fallbacks
 * @param grids        identity-de-duplicated target-grid results
 */
public record TowerTargetResolution(List<IGridNode> exposedNodes, List<TowerResolvedGrid> grids) {

    /** Defensively copies one target resolution. */
    public TowerTargetResolution {
        exposedNodes = List.copyOf(exposedNodes);
        grids = List.copyOf(grids);
    }

    /**
     * Returns the number of grids that passed local validation.
     *
     * @return accepted grid count
     */
    public long acceptedGridCount() {
        return this.grids.stream().filter(TowerResolvedGrid::accepted).count();
    }
}
