package com.fish_dan_.data_energistics.worldgen.meteorite;

import com.fish_dan_.data_energistics.worldgen.meteorite.MeteoriteMotherRockDistribution.MotherRock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies every exact interval and boundary of the production digitized-meteorite distribution.
 */
final class MeteoriteMotherRockDistributionImplTest {

    /** Expected half-open intervals in the same externally specified order as meteorite generation. */
    private static final List<IntervalExpectation> EXPECTED_INTERVALS = List.of(
            new IntervalExpectation(0, 1_375, MotherRock.DAMAGED_CERTUS),
            new IntervalExpectation(1_375, 2_750, MotherRock.CHIPPED_CERTUS),
            new IntervalExpectation(2_750, 4_125, MotherRock.FLAWED_CERTUS),
            new IntervalExpectation(4_125, 5_500, MotherRock.FLAWLESS_CERTUS),
            new IntervalExpectation(5_500, 6_675, MotherRock.DEACTIVATED_DATA),
            new IntervalExpectation(6_675, 7_750, MotherRock.POWERLESS_DATA),
            new IntervalExpectation(7_750, 8_725, MotherRock.FATIGUED_DATA),
            new IntervalExpectation(8_725, 9_600, MotherRock.DEFICIENT_DATA),
            new IntervalExpectation(9_600, 10_000, MotherRock.CHARGED_DATA));

    /** Production selector exercised directly by the deterministic tests. */
    private final MeteoriteMotherRockDistribution distribution = new MeteoriteMotherRockDistributionImpl();

    /**
     * Covers the first and final roll of all nine mutually exclusive probability intervals.
     */
    @Test
    void selectsAllNineIntervalsAtBothBoundaries() {
        for (IntervalExpectation interval : EXPECTED_INTERVALS) {
            assertEquals(
                    interval.motherRock(),
                    this.distribution.select(interval.startInclusive()),
                    "Unexpected mother rock at interval start " + interval.startInclusive());
            assertEquals(
                    interval.motherRock(),
                    this.distribution.select(interval.endExclusive() - 1),
                    "Unexpected mother rock at interval end " + (interval.endExclusive() - 1));
        }
    }

    /**
     * Confirms that the validated 10,000-point probability space rejects rolls on either side.
     */
    @Test
    void rejectsRollsOutsideTheCompleteProbabilitySpace() {
        assertThrows(IllegalArgumentException.class, () -> this.distribution.select(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> this.distribution.select(MeteoriteMotherRockDistribution.TOTAL_BASIS_POINTS));
    }

    /**
     * Describes one expected half-open interval for concise deterministic boundary coverage.
     *
     * @param startInclusive first basis point assigned to the tier
     * @param endExclusive   first basis point assigned to the following tier
     * @param motherRock     tier selected throughout the interval
     */
    private record IntervalExpectation(int startInclusive, int endExclusive, MotherRock motherRock) {}
}
