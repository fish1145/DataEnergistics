package com.fish_dan_.data_energistics.worldgen.meteorite;

import java.util.List;

/**
 * Selects one mother-rock tier for each independent position in a digitized meteorite core.
 *
 * <p>
 * The fixed selector accepts an integer basis-point roll so generation probabilities remain exact and deterministic.
 * </p>
 */
public final class FixedMeteoriteMotherRockDistribution {

    /** Number of mutually exclusive integer rolls in the complete probability space. */
    static final int TOTAL_BASIS_POINTS = 10_000;

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

    /** Ordered probability intervals for the four Certus tiers followed by the five Data Crystal tiers. */
    private static final List<WeightedMotherRock> DISTRIBUTION = List.of(
            new WeightedMotherRock(MotherRock.DAMAGED_CERTUS, 1_375),
            new WeightedMotherRock(MotherRock.CHIPPED_CERTUS, 1_375),
            new WeightedMotherRock(MotherRock.FLAWED_CERTUS, 1_375),
            new WeightedMotherRock(MotherRock.FLAWLESS_CERTUS, 1_375),
            new WeightedMotherRock(MotherRock.DEACTIVATED_DATA, 1_175),
            new WeightedMotherRock(MotherRock.POWERLESS_DATA, 1_075),
            new WeightedMotherRock(MotherRock.FATIGUED_DATA, 975),
            new WeightedMotherRock(MotherRock.DEFICIENT_DATA, 875),
            new WeightedMotherRock(MotherRock.CHARGED_DATA, 400));

    /** Validated upper bound used to reject rolls outside the configured distribution. */
    private final int totalBasisPoints;

    /**
     * Validates that the production distribution covers the complete basis-point space exactly once.
     */
    public FixedMeteoriteMotherRockDistribution() {
        this.totalBasisPoints = DISTRIBUTION.stream().mapToInt(WeightedMotherRock::basisPoints).sum();
        if (this.totalBasisPoints != TOTAL_BASIS_POINTS) {
            throw new IllegalStateException(
                    "Digitized meteorite mother-rock distribution must total " + TOTAL_BASIS_POINTS + " basis points, but totaled " + this.totalBasisPoints);
        }
    }

    /**
     * Walks the ordered mutually exclusive intervals to resolve one exact roll.
     *
     * @param basisPoint a value from {@code 0} through {@code 9999}
     * @return the selected mother-rock tier
     * @throws IllegalArgumentException when the roll is outside the validated distribution
     */
    public MotherRock select(int basisPoint) {
        if (basisPoint < 0 || basisPoint >= this.totalBasisPoints) {
            throw new IllegalArgumentException(
                    "Mother-rock basis point must be between 0 and " + (this.totalBasisPoints - 1) + ": " + basisPoint);
        }

        int intervalEnd = 0;
        for (WeightedMotherRock entry : DISTRIBUTION) {
            intervalEnd += entry.basisPoints();
            if (basisPoint < intervalEnd) {
                return entry.motherRock();
            }
        }
        throw new IllegalStateException("Validated mother-rock distribution did not contain basis point " + basisPoint);
    }

    /**
     * Couples one selectable mother-rock tier with the width of its exact probability interval.
     *
     * @param motherRock  selected tier for the interval
     * @param basisPoints number of integer rolls assigned to the interval
     */
    private record WeightedMotherRock(MotherRock motherRock, int basisPoints) {}
}
