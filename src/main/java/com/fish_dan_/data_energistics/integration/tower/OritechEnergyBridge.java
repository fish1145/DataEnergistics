package com.fish_dan_.data_energistics.integration.tower;

import com.fish_dan_.data_energistics.integration.oritech.OritechEnergyIntegration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

/**
 * Bridge for optional Oritech energy storage lookup.
 *
 * <p>
 * The Data Distribution Tower first queries NeoForge energy capability and only asks this bridge when no native
 * capability is exposed.
 */
public final class OritechEnergyBridge {

    /**
     * Looks up an Oritech-backed energy storage.
     *
     * @param level level containing the target
     * @param pos   target block position
     * @param side  queried side, or null for internal access
     * @return storage exposed by the Oritech integration, or null
     */
    @Nullable
    public IEnergyStorage findEnergyStorage(Level level, BlockPos pos, @Nullable Direction side) {
        return OritechEnergyIntegration.findEnergyStorage(level, pos, side);
    }
}
