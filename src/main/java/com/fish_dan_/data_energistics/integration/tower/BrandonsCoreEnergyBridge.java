package com.fish_dan_.data_energistics.integration.tower;

import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.tower.brandonscore.BrandonsCoreEnergyIntegration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

/**
 * Optional tower bridge for BrandonsCore's long-width energy capability.
 *
 * <p>
 * The outer bridge only exposes Minecraft and NeoForge types. The nested access class keeps BrandonsCore symbols
 * from being resolved when the optional mod is absent.
 * </p>
 */
public final class BrandonsCoreEnergyBridge {

    /**
     * Resolves a BrandonsCore OP storage without loading the integration when BrandonsCore is absent.
     *
     * @param level level containing the target
     * @param pos   target position
     * @param side  queried side, or null for unsided access
     * @return OP-backed energy storage, or null
     */
    @Nullable
    public IEnergyStorage findEnergyStorage(Level level, BlockPos pos, @Nullable Direction side) {
        if (!ModFlags.isBrandonsCoreLoaded()) {
            return null;
        }
        return LoadedAccess.findEnergyStorage(level, pos, side);
    }

    /**
     * Checks whether long-width OP operations are available for a storage.
     *
     * @param storage energy storage to inspect
     * @return true when BrandonsCore is loaded and the storage exposes OP
     */
    public boolean supports(IEnergyStorage storage) {
        return ModFlags.isBrandonsCoreLoaded() && LoadedAccess.supports(storage);
    }

    /**
     * Reads the complete stored OP amount.
     */
    public long stored(IEnergyStorage storage) {
        requireLoaded();
        return LoadedAccess.stored(storage);
    }

    /**
     * Reads the complete OP capacity.
     */
    public long capacity(IEnergyStorage storage) {
        requireLoaded();
        return LoadedAccess.capacity(storage);
    }

    /**
     * Returns whether the OP capability currently accepts energy.
     */
    public boolean canReceive(IEnergyStorage storage) {
        requireLoaded();
        return LoadedAccess.canReceive(storage);
    }

    /**
     * Returns whether the OP capability currently provides energy.
     */
    public boolean canExtract(IEnergyStorage storage) {
        requireLoaded();
        return LoadedAccess.canExtract(storage);
    }

    /**
     * Inserts OP through the public long-width capability.
     */
    public long insert(IEnergyStorage storage, long amount, boolean simulate) {
        requireLoaded();
        return LoadedAccess.insert(storage, amount, simulate);
    }

    /**
     * Extracts OP through the public long-width capability.
     */
    public long extract(IEnergyStorage storage, long amount, boolean simulate) {
        requireLoaded();
        return LoadedAccess.extract(storage, amount, simulate);
    }

    private static void requireLoaded() {
        if (!ModFlags.isBrandonsCoreLoaded()) {
            throw new IllegalStateException("BrandonsCore energy integration is not loaded");
        }
    }

    private static final class LoadedAccess {

        private LoadedAccess() {}

        @Nullable
        private static IEnergyStorage findEnergyStorage(Level level, BlockPos pos, @Nullable Direction side) {
            return BrandonsCoreEnergyIntegration.findEnergyStorage(level, pos, side);
        }

        private static boolean supports(IEnergyStorage storage) {
            return BrandonsCoreEnergyIntegration.supports(storage);
        }

        private static long stored(IEnergyStorage storage) {
            return BrandonsCoreEnergyIntegration.stored(storage);
        }

        private static long capacity(IEnergyStorage storage) {
            return BrandonsCoreEnergyIntegration.capacity(storage);
        }

        private static boolean canReceive(IEnergyStorage storage) {
            return BrandonsCoreEnergyIntegration.canReceive(storage);
        }

        private static boolean canExtract(IEnergyStorage storage) {
            return BrandonsCoreEnergyIntegration.canExtract(storage);
        }

        private static long insert(IEnergyStorage storage, long amount, boolean simulate) {
            return BrandonsCoreEnergyIntegration.insert(storage, amount, simulate);
        }

        private static long extract(IEnergyStorage storage, long amount, boolean simulate) {
            return BrandonsCoreEnergyIntegration.extract(storage, amount, simulate);
        }
    }
}
