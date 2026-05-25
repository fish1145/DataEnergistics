package com.fish_dan_.data_energistics.energy;

public interface DataEnergyStorage {
    double getStored();

    double getCapacity();

    default boolean isEmpty() {
        return getStored() <= 0.0D;
    }

    default boolean isFull() {
        double capacity = getCapacity();
        return capacity > 0.0D && getStored() >= capacity;
    }
}
