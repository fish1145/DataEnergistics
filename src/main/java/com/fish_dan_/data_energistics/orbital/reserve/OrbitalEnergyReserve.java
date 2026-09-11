package com.fish_dan_.data_energistics.orbital.reserve;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

/**
 * Persistent orbital reserves for Stellar Flux and AE energy, which remain independent resources.
 */
public record OrbitalEnergyReserve(
                                   long stellarFlux,
                                   long aeEnergy) {

    private static final OrbitalEnergyReserve EMPTY = new OrbitalEnergyReserve(0L, 0L);

    public OrbitalEnergyReserve {
        if (stellarFlux < 0L || aeEnergy < 0L) {
            throw new IllegalArgumentException("Orbital energy reserves must not be negative");
        }
    }

    /** Returns whether either independent reserve has reached zero. */
    public boolean hasZeroResource() {
        return this.stellarFlux == 0L || this.aeEnergy == 0L;
    }

    /**
     * Returns whether both reserves satisfy the configured deployment threshold.
     * The calculation uses a ceiling so a non-zero fractional threshold cannot deploy with a zero reserve.
     */
    public boolean meetsDeploymentThreshold(DataEnergisticsConfiguration.StellarErasureDeviceSchema settings) {
        return this.stellarFlux >= threshold(settings.stellarFluxCapacity, settings.deploymentThreshold) && this.aeEnergy >= threshold(settings.aeEnergyCapacity, settings.deploymentThreshold);
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
     * Returns the remaining Stellar Flux capacity after applying the current configuration.
     */
    public long stellarFluxSpace(DataEnergisticsConfiguration.StellarErasureDeviceSchema settings) {
        return settings.stellarFluxCapacity - Math.min(this.stellarFlux, settings.stellarFluxCapacity);
    }

    /**
     * Returns the remaining AE energy capacity after applying the current configuration.
     */
    public long aeEnergySpace(DataEnergisticsConfiguration.StellarErasureDeviceSchema settings) {
        return settings.aeEnergyCapacity - Math.min(this.aeEnergy, settings.aeEnergyCapacity);
    }

    /**
     * Applies a successful endpoint transfer and clamps both reserves to the active configuration capacities.
     */
    public OrbitalEnergyReserve withTransfer(
                                             long transferredStellarFlux,
                                             long transferredAeEnergy,
                                             DataEnergisticsConfiguration.StellarErasureDeviceSchema settings) {
        if (transferredStellarFlux < 0L || transferredAeEnergy < 0L) {
            throw new IllegalArgumentException("Transferred orbital energy must not be negative");
        }

        long normalizedStellarFlux = Math.min(this.stellarFlux, settings.stellarFluxCapacity);
        long normalizedAeEnergy = Math.min(this.aeEnergy, settings.aeEnergyCapacity);
        long acceptedStellarFlux = Math.min(
                transferredStellarFlux,
                settings.stellarFluxCapacity - normalizedStellarFlux);
        long acceptedAeEnergy = Math.min(
                transferredAeEnergy,
                settings.aeEnergyCapacity - normalizedAeEnergy);
        long updatedStellarFlux = normalizedStellarFlux + acceptedStellarFlux;
        long updatedAeEnergy = normalizedAeEnergy + acceptedAeEnergy;
        if (updatedStellarFlux == this.stellarFlux && updatedAeEnergy == this.aeEnergy) {
            return this;
        }
        return new OrbitalEnergyReserve(updatedStellarFlux, updatedAeEnergy);
    }

    /**
     * Clamps reserves after a configuration capacity reduction without creating a new object when unchanged.
     */
    public OrbitalEnergyReserve withinCapacity(DataEnergisticsConfiguration.StellarErasureDeviceSchema settings) {
        return withTransfer(0L, 0L, settings);
    }

    /**
     * Returns whether both resources can be atomically reserved for an attack.
     */
    public boolean canAfford(long requiredStellarFlux, long requiredAeEnergy) {
        if (requiredStellarFlux < 0L || requiredAeEnergy < 0L) {
            throw new IllegalArgumentException("Required orbital energy must not be negative");
        }
        return this.stellarFlux >= requiredStellarFlux && this.aeEnergy >= requiredAeEnergy;
    }

    /**
     * Removes an already validated escrow amount without applying capacity normalization.
     */
    public OrbitalEnergyReserve withDebit(long debitedStellarFlux, long debitedAeEnergy) {
        if (!canAfford(debitedStellarFlux, debitedAeEnergy)) {
            throw new IllegalArgumentException("Orbital energy reserve cannot cover the debit");
        }
        if (debitedStellarFlux == 0L && debitedAeEnergy == 0L) {
            return this;
        }
        return new OrbitalEnergyReserve(
                this.stellarFlux - debitedStellarFlux,
                this.aeEnergy - debitedAeEnergy);
    }

    /**
     * Returns escrow to the reserve while failing loudly on a numeric overflow.
     */
    public OrbitalEnergyReserve withCredit(long creditedStellarFlux, long creditedAeEnergy) {
        if (creditedStellarFlux < 0L || creditedAeEnergy < 0L) {
            throw new IllegalArgumentException("Credited orbital energy must not be negative");
        }
        if (creditedStellarFlux == 0L && creditedAeEnergy == 0L) {
            return this;
        }
        try {
            return new OrbitalEnergyReserve(
                    Math.addExact(this.stellarFlux, creditedStellarFlux),
                    Math.addExact(this.aeEnergy, creditedAeEnergy));
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Orbital energy reserve overflow while refunding escrow", exception);
        }
    }
}
