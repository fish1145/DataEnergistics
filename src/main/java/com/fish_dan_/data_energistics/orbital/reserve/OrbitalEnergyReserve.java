package com.fish_dan_.data_energistics.orbital.reserve;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

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

    /** Returns whether either independent reserve has reached zero. */
    public boolean hasZeroResource() {
        return this.celestialEnergy == 0L || this.aeEnergy == 0L;
    }

    /**
     * Returns whether both reserves satisfy the configured deployment threshold.
     * The calculation uses a ceiling so a non-zero fractional threshold cannot deploy with a zero reserve.
     */
    public boolean meetsDeploymentThreshold(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        return this.celestialEnergy >= threshold(settings.celestialEnergyCapacity(), settings.deploymentThreshold()) && this.aeEnergy >= threshold(settings.aeEnergyCapacity(), settings.deploymentThreshold());
    }

    private static long threshold(long capacity, double fraction) {
        double required = Math.ceil(capacity * fraction);
        return required >= capacity ? capacity : Math.max(1L, (long) required);
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
    public long celestialEnergySpace(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        return settings.celestialEnergyCapacity() - Math.min(this.celestialEnergy, settings.celestialEnergyCapacity());
    }

    /**
     * Returns the remaining AE energy capacity after applying the current configuration.
     */
    public long aeEnergySpace(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        return settings.aeEnergyCapacity() - Math.min(this.aeEnergy, settings.aeEnergyCapacity());
    }

    /**
     * Applies a successful endpoint transfer and clamps both reserves to the active configuration capacities.
     */
    public OrbitalEnergyReserve withTransfer(
                                             long transferredCelestialEnergy,
                                             long transferredAeEnergy,
                                             DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
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
    public OrbitalEnergyReserve withinCapacity(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        return withTransfer(0L, 0L, settings);
    }

    /**
     * Returns whether both resources can be atomically reserved for an attack.
     */
    public boolean canAfford(long requiredCelestialEnergy, long requiredAeEnergy) {
        if (requiredCelestialEnergy < 0L || requiredAeEnergy < 0L) {
            throw new IllegalArgumentException("Required orbital energy must not be negative");
        }
        return this.celestialEnergy >= requiredCelestialEnergy && this.aeEnergy >= requiredAeEnergy;
    }

    /**
     * Removes an already validated escrow amount without applying capacity normalization.
     */
    public OrbitalEnergyReserve withDebit(long debitedCelestialEnergy, long debitedAeEnergy) {
        if (!canAfford(debitedCelestialEnergy, debitedAeEnergy)) {
            throw new IllegalArgumentException("Orbital energy reserve cannot cover the debit");
        }
        if (debitedCelestialEnergy == 0L && debitedAeEnergy == 0L) {
            return this;
        }
        return new OrbitalEnergyReserve(
                this.celestialEnergy - debitedCelestialEnergy,
                this.aeEnergy - debitedAeEnergy);
    }

    /**
     * Returns escrow to the reserve while failing loudly on a numeric overflow.
     */
    public OrbitalEnergyReserve withCredit(long creditedCelestialEnergy, long creditedAeEnergy) {
        if (creditedCelestialEnergy < 0L || creditedAeEnergy < 0L) {
            throw new IllegalArgumentException("Credited orbital energy must not be negative");
        }
        if (creditedCelestialEnergy == 0L && creditedAeEnergy == 0L) {
            return this;
        }
        try {
            return new OrbitalEnergyReserve(
                    Math.addExact(this.celestialEnergy, creditedCelestialEnergy),
                    Math.addExact(this.aeEnergy, creditedAeEnergy));
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Orbital energy reserve overflow while refunding escrow", exception);
        }
    }
}
