package com.fish_dan_.data_energistics.integration.tower;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.oritech.OritechEnergyIntegration;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

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
        return isolateEnergyStorageLookup(() -> OritechEnergyIntegration.findEnergyStorage(level, pos, side), pos, side);
    }

    @Nullable
    static IEnergyStorage isolateEnergyStorageLookup(EnergyStorageLookup lookup, BlockPos pos,
                                                     @Nullable Direction side) {
        try {
            return lookup.find();
        } catch (Throwable throwable) {
            ThrowableIsolation.rethrowIfFatal(throwable);
            Data_Energistics.LOGGER.error("Failed to resolve Oritech energy storage at {} side {}", pos, side, throwable);
            return null;
        }
    }

    /** Executes one optional Oritech storage lookup behind the shared fatal-failure boundary. */
    @FunctionalInterface
    interface EnergyStorageLookup {

        /**
         * Resolves the third-party storage.
         *
         * @return resolved storage, or null
         */
        @Nullable
        IEnergyStorage find();
    }
}
