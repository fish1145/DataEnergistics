package com.fish_dan_.data_energistics.integration.tower;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.modernindustrialization.ModernIndustrializationEnergyIntegration;
import com.fish_dan_.data_energistics.integration.modernindustrialization.ModernIndustrializationEnergyStorage;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * Optional class-loading boundary for Modern Industrialization energy storage lookup.
 */
public final class ModernIndustrializationEnergyBridge {

    /**
     * Resolves a type-safe MI energy route without loading MI classes when the mod is absent.
     *
     * @param level level containing the target
     * @param pos   target block position
     * @param side  queried side, or null for unsided access
     * @return MI energy storage, or null when unavailable
     */
    @Nullable
    public ModernIndustrializationEnergyStorage findEnergyStorage(
                                                                  Level level,
                                                                  BlockPos pos,
                                                                  @Nullable Direction side) {
        if (!ModFlags.isModernIndustrializationEnergySupportLoaded()) {
            return null;
        }
        try {
            return LoadedAccess.findEnergyStorage(level, pos, side);
        } catch (Throwable throwable) {
            ThrowableIsolation.rethrowIfFatal(throwable);
            Data_Energistics.LOGGER.error(
                    "Failed to resolve Modern Industrialization energy storage at {} side {}",
                    pos,
                    side,
                    throwable);
            return null;
        }
    }

    /**
     * Holds all direct MI references behind the successful mod-presence branch.
     */
    private static final class LoadedAccess {

        private LoadedAccess() {}

        @Nullable
        private static ModernIndustrializationEnergyStorage findEnergyStorage(
                                                                              Level level,
                                                                              BlockPos pos,
                                                                              @Nullable Direction side) {
            return ModernIndustrializationEnergyIntegration.findEnergyStorage(level, pos, side);
        }
    }
}
