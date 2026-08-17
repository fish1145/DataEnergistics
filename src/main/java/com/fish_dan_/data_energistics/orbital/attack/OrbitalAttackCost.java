package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;

/**
 * Immutable resource escrow and cooldown captured when an attack is confirmed.
 */
public record OrbitalAttackCost(
                                long celestialEnergy,
                                long aeEnergy,
                                int cooldownTicks) {

    public OrbitalAttackCost {
        if (celestialEnergy <= 0L || aeEnergy <= 0L || cooldownTicks <= 0) {
            throw new IllegalArgumentException("Attack costs and cooldown must be positive");
        }
    }

    /**
     * Reads the kinetic cost from one immutable configuration snapshot.
     */
    public static OrbitalAttackCost kinetic(DataEnergisticsSettings.OrbitalWeapon settings) {
        return new OrbitalAttackCost(
                settings.kineticCelestialEnergyCost(),
                settings.kineticAeEnergyCost(),
                settings.kineticCooldownTicks());
    }
}
