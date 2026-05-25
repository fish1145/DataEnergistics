package com.fish_dan_.data_energistics.energy;

public interface MutableDataEnergyStorage extends DataEnergyStorage {
    double insert(double amount, boolean simulate);

    double extract(double amount, boolean simulate);

    default boolean canInsert() {
        return true;
    }

    default boolean canExtract() {
        return true;
    }
}
