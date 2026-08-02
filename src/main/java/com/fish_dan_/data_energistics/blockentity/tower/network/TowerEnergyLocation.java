package com.fish_dan_.data_energistics.blockentity.tower.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Loaded block location that may expose one or more FE capabilities.
 *
 * @param level    level containing the endpoint owner
 * @param position endpoint owner position
 */
public record TowerEnergyLocation(Level level, BlockPos position) {

    /** Validates and normalizes one energy location. */
    public TowerEnergyLocation {
        position = position.immutable();
    }
}
