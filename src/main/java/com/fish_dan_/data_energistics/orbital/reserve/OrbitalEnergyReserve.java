package com.fish_dan_.data_energistics.orbital.reserve;

import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;

/**
 * Persistent orbital reserves for Celestial Energy and AE energy, which remain independent resources.
 */
public record OrbitalEnergyReserve(
                                   long celestialEnergy,
                                   long aeEnergy) {

    private static final OrbitalEnergyReserve EMPTY = new OrbitalEnergyReserve(0L, 0L);

    public OrbitalEnergyReserve {
        if (celestialEnergy < 0L || aeEnergy < 0L) {
            throw new IllegalArgumentException("Orbital energy reserves must not be negative");
        }
    }

    /**
     * Returns the shared empty reserve used by newly provisioned weapons and migrated legacy records.
     */
    public static OrbitalEnergyReserve empty() {
        return EMPTY;
    }

    /**
     * Returns the remaining Celestial Energy capacity after applying the current configuration.
     */
    public long celestialEnergySpace(DataEnergisticsSettings.OrbitalWeapon settings) {
        return settings.celestialEnergyCapacity() - Math.min(this.celestialEnergy, settings.celestialEnergyCapacity());
    }

    /**
     * Returns the remaining AE energy capacity after applying the current configuration.
     */
    public long aeEnergySpace(DataEnergisticsSettings.OrbitalWeapon settings) {
        return settings.aeEnergyCapacity() - Math.min(this.aeEnergy, settings.aeEnergyCapacity());
    }

    /**
     * Applies a successful endpoint transfer and clamps both reserves to the active configuration capacities.
     */
    public OrbitalEnergyReserve withTransfer(
                                             long transferredCelestialEnergy,
                                             long transferredAeEnergy,
                                             DataEnergisticsSettings.OrbitalWeapon settings) {
        if (transferredCelestialEnergy < 0L || transferredAeEnergy < 0L) {
            throw new IllegalArgumentException("Transferred orbital energy must not be negative");
        }

        long normalizedCelestialEnergy = Math.min(this.celestialEnergy, settings.celestialEnergyCapacity());
        long normalizedAeEnergy = Math.min(this.aeEnergy, settings.aeEnergyCapacity());
        long acceptedCelestialEnergy = Math.min(
                transferredCelestialEnergy,
                settings.celestialEnergyCapacity() - normalizedCelestialEnergy);
        long acceptedAeEnergy = Math.min(
                transferredAeEnergy,
                settings.aeEnergyCapacity() - normalizedAeEnergy);
        long updatedCelestialEnergy = normalizedCelestialEnergy + acceptedCelestialEnergy;
        long updatedAeEnergy = normalizedAeEnergy + acceptedAeEnergy;
        if (updatedCelestialEnergy == this.celestialEnergy && updatedAeEnergy == this.aeEnergy) {
            return this;
        }
        return new OrbitalEnergyReserve(updatedCelestialEnergy, updatedAeEnergy);
    }

    /**
     * Clamps reserves after a configuration capacity reduction without creating a new object when unchanged.
     */
    public OrbitalEnergyReserve withinCapacity(DataEnergisticsSettings.OrbitalWeapon settings) {
        return withTransfer(0L, 0L, settings);
    }
}
