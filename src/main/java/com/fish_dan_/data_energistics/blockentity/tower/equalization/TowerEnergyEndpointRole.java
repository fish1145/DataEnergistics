package com.fish_dan_.data_energistics.blockentity.tower.equalization;

/**
 * Defines how one endpoint participates in tower energy planning independently of its transfer permissions.
 */
public enum TowerEnergyEndpointRole {

    /**
     * Participates in proportional equalization with other connected machines.
     */
    BALANCED,

    /**
     * Supplies deficits and absorbs surplus after the balanced endpoints have been planned.
     */
    BUFFER
}
