package com.fish_dan_.data_energistics.integration.energy.industrialforegoing;

import com.fish_dan_.data_energistics.blockentity.tower.energy.access.UnlimitedEnergyStorage;

import net.neoforged.neoforge.energy.IEnergyStorage;

import com.buuz135.industrial.item.infinity.InfinityEnergyStorage;

/** Adapts Industrial Foregoing's long-width infinity storage to the tower energy contract. */
public final class IndustrialForegoingEnergyIntegration {

    private IndustrialForegoingEnergyIntegration() {}

    public static boolean supports(IEnergyStorage storage) {
        return storage instanceof InfinityEnergyStorage<?>;
    }

    public static UnlimitedEnergyStorage wrap(IEnergyStorage storage) {
        if (!(storage instanceof InfinityEnergyStorage<?> infinity)) {
            throw new IllegalArgumentException("Storage is not an Industrial Foregoing infinity storage");
        }
        return new InfinityStorage(infinity);
    }

    private static final class InfinityStorage implements UnlimitedEnergyStorage {

        private final InfinityEnergyStorage<?> storage;

        private InfinityStorage(InfinityEnergyStorage<?> storage) {
            this.storage = storage;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return this.storage.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return this.storage.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return this.storage.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return this.storage.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return this.storage.canExtract();
        }

        @Override
        public boolean canReceive() {
            return this.storage.canReceive();
        }

        @Override
        public long getStoredEnergyLong() {
            return this.storage.getLongEnergyStored();
        }

        @Override
        public long getEnergyCapacityLong() {
            return this.storage.getLongCapacity();
        }

        @Override
        public void setStoredEnergyLong(long amount) {
            this.storage.setEnergyStored(amount);
        }

        @Override
        public void onUnlimitedEnergyChanged() {}
    }
}
