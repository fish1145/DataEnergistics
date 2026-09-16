package com.fish_dan_.data_energistics.api.registry.connector;

/** Direction selected for an individual distribution-tower FE link. */
public enum EnergyTransferDirection {

    INPUT,
    OUTPUT;

    public EnergyTransferDirection opposite() {
        return this == INPUT ? OUTPUT : INPUT;
    }
}
