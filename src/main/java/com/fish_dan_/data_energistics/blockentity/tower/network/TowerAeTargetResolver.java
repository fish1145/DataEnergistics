package com.fish_dan_.data_energistics.blockentity.tower.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;

/**
 * Resolves every distinct AE grid exposed through the six sided in-world capabilities of one anchor.
 */
public interface TowerAeTargetResolver {

    /**
     * Resolves and locally validates one loaded anchor without creating connections or loading chunks.
     *
     * @param level       anchor level
     * @param anchor      connector/range anchor
     * @param primaryGrid requesting tower grid
     * @param mode        point or scope validation
     * @return immutable partial-success result; empty when the anchor chunk is unloaded
     */
    TowerTargetResolution resolve(Level level,
                                  BlockPos anchor,
                                  IGrid primaryGrid,
                                  TowerTargetDiscoveryMode mode);
}
