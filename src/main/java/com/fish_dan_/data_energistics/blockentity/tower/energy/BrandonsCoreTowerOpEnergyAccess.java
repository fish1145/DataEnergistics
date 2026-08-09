package com.fish_dan_.data_energistics.blockentity.tower.energy;

import com.fish_dan_.data_energistics.integration.tower.BrandonsCoreEnergyBridge;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Delegates tower OP operations to the optional BrandonsCore bridge.
 */
final class BrandonsCoreTowerOpEnergyAccess implements TowerOpEnergyAccess {

    private final BrandonsCoreEnergyBridge bridge;

    BrandonsCoreTowerOpEnergyAccess(BrandonsCoreEnergyBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public boolean supports(IEnergyStorage storage) {
        return this.bridge.supports(storage);
    }

    @Override
    public long stored(IEnergyStorage storage) {
        return this.bridge.stored(storage);
    }

    @Override
    public long capacity(IEnergyStorage storage) {
        return this.bridge.capacity(storage);
    }

    @Override
    public boolean canReceive(IEnergyStorage storage) {
        return this.bridge.canReceive(storage);
    }

    @Override
    public boolean canExtract(IEnergyStorage storage) {
        return this.bridge.canExtract(storage);
    }

    @Override
    public long insert(IEnergyStorage storage, long amount, boolean simulate) {
        return this.bridge.insert(storage, amount, simulate);
    }

    @Override
    public long extract(IEnergyStorage storage, long amount, boolean simulate) {
        return this.bridge.extract(storage, amount, simulate);
    }
}
