package com.fish_dan_.data_energistics.integration.tower.energy.brandonscore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.capability.CapabilityOP;
import org.jspecify.annotations.Nullable;

/**
 * Type-safe access to BrandonsCore's public long-width Operational Potential capability.
 */
public final class BrandonsCoreEnergyIntegration {

    private BrandonsCoreEnergyIntegration() {}

    /**
     * Resolves the public OP capability for one block side.
     *
     * @param level level containing the target
     * @param pos   target position
     * @param side  queried side, or null for unsided access
     * @return OP storage exposed at the target, or null
     */
    @Nullable
    public static IEnergyStorage findEnergyStorage(Level level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(CapabilityOP.BLOCK, pos, side);
    }

    /**
     * Checks whether a standard energy view is backed by BrandonsCore's OP API.
     *
     * @param storage energy view to inspect
     * @return true when long-width OP operations are available
     */
    public static boolean supports(IEnergyStorage storage) {
        return storage instanceof IOPStorage;
    }

    /**
     * Reads the complete stored OP amount.
     *
     * @param storage OP-backed energy view
     * @return stored OP without an integer-width clamp
     */
    public static long stored(IEnergyStorage storage) {
        return requireOpStorage(storage).getOPStored();
    }

    /**
     * Reads the complete OP capacity.
     *
     * @param storage OP-backed energy view
     * @return OP capacity without an integer-width clamp
     */
    public static long capacity(IEnergyStorage storage) {
        return requireOpStorage(storage).getMaxOPStored();
    }

    /**
     * Returns whether the OP capability currently accepts energy.
     */
    public static boolean canReceive(IEnergyStorage storage) {
        return requireOpStorage(storage).canReceive();
    }

    /**
     * Returns whether the OP capability currently provides energy.
     */
    public static boolean canExtract(IEnergyStorage storage) {
        return requireOpStorage(storage).canExtract();
    }

    /**
     * Inserts OP through the storage's public long-width transfer method.
     *
     * @param storage  OP-backed energy view
     * @param amount   maximum OP to insert
     * @param simulate whether to simulate the transfer
     * @return accepted OP
     */
    public static long insert(IEnergyStorage storage, long amount, boolean simulate) {
        IOPStorage opStorage = requireOpStorage(storage);
        if (amount <= 0L || !opStorage.canReceive()) {
            return 0L;
        }
        return opStorage.receiveOP(amount, simulate);
    }

    /**
     * Extracts OP through the storage's public long-width transfer method.
     *
     * @param storage  OP-backed energy view
     * @param amount   maximum OP to extract
     * @param simulate whether to simulate the transfer
     * @return extracted OP
     */
    public static long extract(IEnergyStorage storage, long amount, boolean simulate) {
        IOPStorage opStorage = requireOpStorage(storage);
        if (amount <= 0L || !opStorage.canExtract()) {
            return 0L;
        }
        return opStorage.extractOP(amount, simulate);
    }

    private static IOPStorage requireOpStorage(IEnergyStorage storage) {
        if (storage instanceof IOPStorage opStorage) {
            return opStorage;
        }
        throw new IllegalArgumentException("Energy storage does not expose BrandonsCore OP capability");
    }
}
