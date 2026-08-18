package com.fish_dan_.data_energistics.blockentity.tower.energy.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

/**
 * Immutable context for one resolved, side-aware energy route.
 */
public record TowerEnergyEndpointContext(
                                         Level level,
                                         BlockPos position,
                                         @Nullable Direction side,
                                         IEnergyStorage storage) {

    public TowerEnergyEndpointContext {
        position = position.immutable();
    }
}
