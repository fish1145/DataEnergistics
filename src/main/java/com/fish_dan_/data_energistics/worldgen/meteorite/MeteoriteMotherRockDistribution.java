package com.fish_dan_.data_energistics.worldgen.meteorite;

/**
 * Selects one mother-rock tier for each independent position in a digitized meteorite core.
 *
 * <p>
 * The selector accepts an integer basis-point roll so generation probabilities remain exact and deterministic.
 */
public interface MeteoriteMotherRockDistribution {

    /** Number of mutually exclusive integer rolls in the complete probability space. */
    int TOTAL_BASIS_POINTS = 10_000;

    /**
     * Identifies every mother rock that may occupy a digitized meteorite core position.
     */
    enum MotherRock {
        /** Damaged Certus Quartz mother rock. */
        DAMAGED_CERTUS,
        /** Chipped Certus Quartz mother rock. */
        CHIPPED_CERTUS,
        /** Flawed Certus Quartz mother rock. */
        FLAWED_CERTUS,
        /** Flawless Certus Quartz mother rock. */
        FLAWLESS_CERTUS,
        /** Deactivated Data Crystal mother rock. */
        DEACTIVATED_DATA,
        /** Powerless Data Crystal mother rock. */
        POWERLESS_DATA,
        /** Fatigued Data Crystal mother rock. */
        FATIGUED_DATA,
        /** Deficient Data Crystal mother rock. */
        DEFICIENT_DATA,
        /** Charged Data Crystal mother rock. */
        CHARGED_DATA
    }

    /**
     * Selects the mother rock whose half-open probability interval contains the supplied roll.
     *
     * @param basisPoint a value from {@code 0} through {@code 9999}
     * @return the selected mother-rock tier
     * @throws IllegalArgumentException when the roll is outside the supported range
     */
    MotherRock select(int basisPoint);
}
