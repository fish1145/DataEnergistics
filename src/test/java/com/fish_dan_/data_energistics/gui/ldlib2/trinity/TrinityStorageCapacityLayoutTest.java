package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TrinityStorageCapacityLayoutTest {

    @Test
    void finiteEmptyStorageLeavesOnlyTheTrackVisible() {
        assertLayout(10, 0, 0, 0, 20, false, 0, 0, 0, 0);
    }

    @Test
    void finitePartialStorageUsesFloorFillAndLargestRemainders() {
        assertLayout(10, 1, 1, 1, 6, false, 2, 2, 1, 0);
    }

    @Test
    void exactCapacityFillsTheWholeWidth() {
        assertLayout(12, 2, 3, 5, 10, false, 2, 4, 6, 0);
    }

    @Test
    void overCapacityClampsToTheWholeWidth() {
        assertLayout(7, 8, 1, 1, 5, false, 5, 1, 1, 0);
    }

    @Test
    void nonEmptyZeroCapacityIsTreatedAsFull() {
        assertLayout(9, 1, 0, 0, 0, false, 9, 0, 0, 0);
    }

    @Test
    void unlimitedStorageFillsTheWholeWidthUsingCurrentRatios() {
        assertLayout(8, 1, 2, 1, 0, true, 2, 4, 2, 0);
    }

    @Test
    void unlimitedEmptyStorageUsesTheNeutralFullBar() {
        assertLayout(11, 0, 0, 0, 0, true, 0, 0, 0, 11);
    }

    @Test
    void equalRemaindersPreferItemThenFluidThenOther() {
        assertLayout(2, 1, 1, 1, 3, false, 1, 1, 0, 0);
    }

    @Test
    void zeroWidthAlwaysProducesZeroWidthSegments() {
        assertLayout(0, 10, 20, 30, 0, true, 0, 0, 0, 0);
        assertLayout(0, 0, 0, 0, 0, true, 0, 0, 0, 0);
    }

    @Test
    void arithmeticRetainsValuesBeyondLongRange() {
        BigInteger scale = BigInteger.TEN.pow(40);
        TrinityStorageCapacityLayout actual = TrinityStorageCapacityLayout.calculate(
                13,
                scale,
                scale.multiply(BigInteger.TWO),
                scale,
                scale.multiply(BigInteger.valueOf(8)),
                false);

        assertEquals(new TrinityStorageCapacityLayout(2, 3, 1, 0), actual);
        assertEquals(6, actual.filledWidth());
    }

    private static void assertLayout(int width,
                                     long item,
                                     long fluid,
                                     long other,
                                     long capacity,
                                     boolean unlimited,
                                     int expectedItemWidth,
                                     int expectedFluidWidth,
                                     int expectedOtherWidth,
                                     int expectedNeutralWidth) {
        TrinityStorageCapacityLayout actual = TrinityStorageCapacityLayout.calculate(
                width,
                BigInteger.valueOf(item),
                BigInteger.valueOf(fluid),
                BigInteger.valueOf(other),
                BigInteger.valueOf(capacity),
                unlimited);
        TrinityStorageCapacityLayout expected = new TrinityStorageCapacityLayout(
                expectedItemWidth,
                expectedFluidWidth,
                expectedOtherWidth,
                expectedNeutralWidth);

        assertEquals(expected, actual);
        assertEquals(expected.filledWidth(), actual.filledWidth());
    }
}
