package com.fish_dan_.data_energistics.blockentity.tower.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Supplies typed long-width OP operations to tower transfer logic.
 */
interface TowerOpEnergyAccess {

    /**
     * Returns whether the storage exposes the OP API.
     */
    boolean supports(IEnergyStorage storage);

    /**
     * Returns the complete stored OP amount.
     */
    long stored(IEnergyStorage storage);

    /**
     * Returns the complete OP capacity.
     */
    long capacity(IEnergyStorage storage);

    /**
     * Returns whether the OP endpoint accepts energy.
     */
    boolean canReceive(IEnergyStorage storage);

    /**
     * Returns whether the OP endpoint provides energy.
     */
    boolean canExtract(IEnergyStorage storage);

    /**
     * Inserts OP through the public long-width API.
     */
    long insert(IEnergyStorage storage, long amount, boolean simulate);

    /**
     * Extracts OP through the public long-width API.
     */
    long extract(IEnergyStorage storage, long amount, boolean simulate);
}
