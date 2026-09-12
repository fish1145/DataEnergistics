package com.fish_dan_.data_energistics.orbital.astronomy;

import com.fish_dan_.data_energistics.ae2.key.StellarFluxKey;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;

/**
 * Commits one all-or-nothing-oriented AE power to Stellar Flux production transaction.
 *
 * <p>
 * The method must run on the logical server thread. Storage capacity and AE power are both simulated before either
 * resource is mutated. A storage implementation that accepts less than its successful simulation promised is treated
 * as an external transaction mismatch: the accepted Stellar Flux remains stored and the unused proportional AE
 * power is refunded. Invalid third-party return values fail fast.
 * </p>
 */
public final class StellarFluxGridTransaction {

    private static final double ENERGY_TOLERANCE = 0.0001D;

    private StellarFluxGridTransaction() {}

    /**
     * Attempts to insert {@code stellarFlux} after charging the independent {@code requiredAeEnergy} cost.
     *
     * @return the actual stored amount; zero means that either preflight check rejected the complete operation
     * @throws IllegalArgumentException when an amount is negative or the Stellar Flux offer is zero
     * @throws IllegalStateException    when an AE service violates its public return-value contract or a refund fails
     */
    public static long commit(
                              IGrid grid,
                              IActionSource actionSource,
                              long stellarFlux,
                              double requiredAeEnergy) {
        if (stellarFlux <= 0L) {
            throw new IllegalArgumentException("Stellar Flux production must be positive: " + stellarFlux);
        }
        if (!Double.isFinite(requiredAeEnergy) || requiredAeEnergy < 0.0D) {
            throw new IllegalArgumentException("Required AE energy must be finite and non-negative: " + requiredAeEnergy);
        }

        MEStorage storage = grid.getStorageService().getInventory();
        long insertable = storage.insert(
                StellarFluxKey.of(),
                stellarFlux,
                Actionable.SIMULATE,
                actionSource);
        requireValidInsertion(insertable, stellarFlux, "simulated");
        if (insertable < stellarFlux) {
            return 0L;
        }

        IEnergyService energyService = grid.getEnergyService();
        double simulatedEnergy = energyService.extractAEPower(
                requiredAeEnergy,
                Actionable.SIMULATE,
                PowerMultiplier.ONE);
        if (!containsRequiredEnergy(simulatedEnergy, requiredAeEnergy)) {
            return 0L;
        }

        double extractedEnergy = energyService.extractAEPower(
                requiredAeEnergy,
                Actionable.MODULATE,
                PowerMultiplier.ONE);
        if (!containsRequiredEnergy(extractedEnergy, requiredAeEnergy)) {
            refundEnergy(energyService, extractedEnergy);
            return 0L;
        }

        long inserted;
        try {
            inserted = storage.insert(
                    StellarFluxKey.of(),
                    stellarFlux,
                    Actionable.MODULATE,
                    actionSource);
        } catch (RuntimeException insertionFailure) {
            try {
                refundEnergy(energyService, extractedEnergy);
            } catch (RuntimeException refundFailure) {
                insertionFailure.addSuppressed(refundFailure);
            }
            throw insertionFailure;
        }
        try {
            requireValidInsertion(inserted, stellarFlux, "actual");
        } catch (RuntimeException invalidInsertion) {
            try {
                refundEnergy(energyService, extractedEnergy);
            } catch (RuntimeException refundFailure) {
                invalidInsertion.addSuppressed(refundFailure);
            }
            throw invalidInsertion;
        }
        if (inserted < stellarFlux) {
            double consumedFraction = (double) inserted / stellarFlux;
            refundEnergy(energyService, extractedEnergy * (1.0D - consumedFraction));
        }
        return inserted;
    }

    private static boolean containsRequiredEnergy(double extracted, double required) {
        if (!Double.isFinite(extracted) || extracted < 0.0D) {
            throw new IllegalStateException("AE grid returned an invalid extracted energy amount: " + extracted);
        }
        return extracted + ENERGY_TOLERANCE >= required;
    }

    private static void requireValidInsertion(long inserted, long offered, String phase) {
        if (inserted < 0L || inserted > offered) {
            throw new IllegalStateException(
                    "AE storage returned an invalid " + phase + " Stellar Flux insertion: " + inserted);
        }
    }

    private static void refundEnergy(IEnergyService energyService, double energy) {
        if (energy <= ENERGY_TOLERANCE) {
            return;
        }
        double overflow = energyService.injectPower(energy, Actionable.MODULATE);
        if (!Double.isFinite(overflow) || overflow > ENERGY_TOLERANCE) {
            throw new IllegalStateException("AE grid rejected a production energy refund of " + energy);
        }
    }
}
