package com.fish_dan_.data_energistics.integration.oritech;

import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;
import rearth.oritech.api.energy.EnergyApi;
import rearth.oritech.api.energy.EnergyApi.BlockProvider;
import rearth.oritech.api.energy.EnergyApi.EnergyStorage;

/**
 * Type-safe Oritech energy bridge used when no NeoForge energy capability is exposed.
 */
public final class OritechEnergyIntegration {

    private OritechEnergyIntegration() {}

    /**
     * Resolves Oritech's public long-width storage API for one block side.
     *
     * @param level level containing the target
     * @param pos   target position
     * @param side  queried side, or null for internal access
     * @return NeoForge view backed by Oritech's public storage, or null
     */
    @Nullable
    public static IEnergyStorage findEnergyStorage(Level level, BlockPos pos, @Nullable Direction side) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        EnergyStorage storage = EnergyApi.BLOCK.find(level, pos, state, blockEntity, side);
        if (storage == null && blockEntity instanceof BlockProvider provider) {
            storage = provider.getEnergyStorage(side);
        }
        return storage == null ? null : wrapEnergyStorage(storage);
    }

    /**
     * Wraps Oritech's public long-width API for the tower's standard and unlimited energy paths.
     *
     * @param storage Oritech storage returned by its public API
     * @return NeoForge storage with a type-safe unlimited mutation path
     */
    static IEnergyStorage wrapEnergyStorage(EnergyStorage storage) {
        return new OritechEnergyStorageImpl(storage);
    }

    /**
     * Preserves Oritech's long-width amount behind the standard capability boundary.
     */
    private static final class OritechEnergyStorageImpl implements UnlimitedEnergyStorage {

        private final EnergyStorage storage;

        private OritechEnergyStorageImpl(EnergyStorage storage) {
            this.storage = storage;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0 || !canReceive()) {
                return 0;
            }
            long inserted = this.storage.insert(maxReceive, simulate);
            if (!simulate && inserted > 0) {
                onUnlimitedEnergyChanged();
            }
            return clampToInt(inserted);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0 || !canExtract()) {
                return 0;
            }
            long extracted = this.storage.extract(maxExtract, simulate);
            if (!simulate && extracted > 0) {
                onUnlimitedEnergyChanged();
            }
            return clampToInt(extracted);
        }

        @Override
        public int getEnergyStored() {
            return clampToInt(getStoredEnergyLong());
        }

        @Override
        public int getMaxEnergyStored() {
            return clampToInt(getEnergyCapacityLong());
        }

        @Override
        public boolean canExtract() {
            return this.storage.supportsExtraction();
        }

        @Override
        public boolean canReceive() {
            return this.storage.supportsInsertion();
        }

        @Override
        public long getStoredEnergyLong() {
            return this.storage.getAmount();
        }

        @Override
        public long getEnergyCapacityLong() {
            return this.storage.getCapacity();
        }

        @Override
        public void setStoredEnergyLong(long amount) {
            this.storage.setAmount(amount);
        }

        @Override
        public void onUnlimitedEnergyChanged() {
            this.storage.update();
        }

        private static int clampToInt(long amount) {
            if (amount <= 0) {
                return 0;
            }
            return (int) Math.min(amount, Integer.MAX_VALUE);
        }
    }
}
