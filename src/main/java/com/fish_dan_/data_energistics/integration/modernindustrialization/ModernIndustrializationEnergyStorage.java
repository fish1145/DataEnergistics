package com.fish_dan_.data_energistics.integration.modernindustrialization;

import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess.EnergySnapshot;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Long-width external-energy view of one sided Modern Industrialization energy storage.
 *
 * <p>
 * Amounts exposed by this interface use the same external energy unit as NeoForge Energy. The implementation reads
 * Modern Industrialization's configured external-energy-per-EU ratio and preserves its integral rounding rules.
 * </p>
 */
public interface ModernIndustrializationEnergyStorage extends IEnergyStorage {

    /**
     * Returns the physical MI storage used to merge equivalent sided access routes by object identity.
     *
     * @return backing MI storage identity
     */
    Object backingIdentity();

    /**
     * Captures the complete external-unit amount and capacity from the official long-width view.
     *
     * @return validated immutable energy state
     */
    EnergySnapshot snapshot();

    /**
     * Returns the current external-energy value of one indivisible MI EU.
     *
     * @return positive transfer quantum in external FE units
     */
    long transferQuantum();

    /**
     * Inserts external energy through MI's configurable EU conversion.
     *
     * @param amount   non-negative maximum external energy to insert
     * @param simulate whether to simulate the operation
     * @return inserted external energy in {@code [0, amount]}
     */
    long insert(long amount, boolean simulate);

    /**
     * Extracts external energy through MI's configurable EU conversion.
     *
     * @param amount   non-negative maximum external energy to extract
     * @param simulate whether to simulate the operation
     * @return extracted external energy in {@code [0, amount]}
     */
    long extract(long amount, boolean simulate);

    /**
     * Attempts to restore energy removed by the immediately preceding extraction.
     *
     * <p>
     * MI exposes no public direct amount writer, so compensation follows the sided MI insertion route. A source-only
     * route therefore returns zero instead of bypassing MI's permission.
     * </p>
     *
     * @param amount non-negative external energy to restore
     * @return restored external energy in {@code [0, amount]}
     */
    long compensateExtraction(long amount);
}
