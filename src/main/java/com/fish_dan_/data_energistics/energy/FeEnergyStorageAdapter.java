package com.fish_dan_.data_energistics.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.Objects;

public final class FeEnergyStorageAdapter implements MutableDataEnergyStorage {
    private final IEnergyStorage storage;

    public FeEnergyStorageAdapter(IEnergyStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public IEnergyStorage unwrap() {
        return this.storage;
    }

    @Override
    public double getStored() {
        return this.storage.getEnergyStored();
    }

    @Override
    public double getCapacity() {
        return this.storage.getMaxEnergyStored();
    }

    @Override
    public double insert(double amount, boolean simulate) {
        return this.storage.receiveEnergy(toFeAmount(amount), simulate);
    }

    @Override
    public double extract(double amount, boolean simulate) {
        return this.storage.extractEnergy(toFeAmount(amount), simulate);
    }

    @Override
    public boolean canInsert() {
        return this.storage.canReceive();
    }

    @Override
    public boolean canExtract() {
        return this.storage.canExtract();
    }

    private static int toFeAmount(double amount) {
        if (!(amount > 0.0D)) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(amount));
    }
}
