package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

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
    public static OrbitalAttackCost kinetic(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        return new OrbitalAttackCost(
                settings.kineticCelestialEnergyCost,
                settings.kineticAeEnergyCost,
                settings.kineticCooldownTicks);
    }

    /**
     * Calculates the complete directed-energy escrow, including every scheduled disk coordinate.
     */
    public static OrbitalAttackCost directedEnergy(
                                                   DataEnergisticsConfiguration.OrbitalWeaponSchema settings,
                                                   long scheduledCoordinates) {
        if (scheduledCoordinates <= 0L) {
            throw new IllegalArgumentException("A directed-energy scan must schedule at least one coordinate");
        }
        long celestial = Math.addExact(
                settings.directedEnergyBaseCelestialEnergyCost,
                Math.multiplyExact(settings.directedEnergyCelestialEnergyPerCoordinate, scheduledCoordinates));
        long ae = Math.addExact(
                settings.directedEnergyBaseAeEnergyCost,
                Math.multiplyExact(settings.directedEnergyAeEnergyPerCoordinate, scheduledCoordinates));
        return new OrbitalAttackCost(celestial, ae, settings.directedEnergyCooldownTicks);
    }

    /**
     * Reads the fixed digital-annihilation payload cost from one immutable configuration snapshot.
     */
    public static OrbitalAttackCost digitalAnnihilation(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        return new OrbitalAttackCost(
                settings.digitalAnnihilationCelestialEnergyCost,
                settings.digitalAnnihilationAeEnergyCost,
                settings.digitalAnnihilationCooldownTicks);
    }
}
