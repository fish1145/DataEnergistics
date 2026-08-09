package com.fish_dan_.data_energistics.common.trinity.host;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityDataCoreStorageStatusTest {

    @Test
    void totalAmountIsDerivedFromAllCategories() {
        TrinityDataCoreStorageStatus status = new TrinityDataCoreStorageStatus(
                3,
                8,
                BigInteger.valueOf(11L),
                BigInteger.valueOf(13L),
                BigInteger.valueOf(17L),
                BigInteger.valueOf(100L),
                false);

        assertEquals(BigInteger.valueOf(41L), status.totalAmount());
    }

    @Test
    void constructorRejectsMissingOrNegativeValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityDataCoreStorageStatus(
                        -1,
                        0,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityDataCoreStorageStatus(
                        0,
                        -1,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityDataCoreStorageStatus(
                        0,
                        0,
                        BigInteger.valueOf(-1L),
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityDataCoreStorageStatus(
                        0,
                        0,
                        BigInteger.ZERO,
                        BigInteger.valueOf(-1L),
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityDataCoreStorageStatus(
                        0,
                        0,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.valueOf(-1L),
                        BigInteger.ZERO,
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityDataCoreStorageStatus(
                        0,
                        0,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.valueOf(-1L),
                        false));
        assertThrows(
                NullPointerException.class,
                () -> new TrinityDataCoreStorageStatus(
                        0,
                        0,
                        null,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        BigInteger.ZERO,
                        false));
    }

    @Test
    void codecRoundTripsAmountsBeyondLongRange() {
        TrinityDataCoreStorageStatus expected = new TrinityDataCoreStorageStatus(
                9,
                12,
                new BigInteger("184467440737095516160"),
                new BigInteger("368934881474191032320"),
                BigInteger.valueOf(7L),
                new BigInteger("999999999999999999999"),
                true);

        JsonElement encoded = TrinityDataCoreStorageStatus.CODEC
                .encodeStart(JsonOps.INSTANCE, expected)
                .getOrThrow();
        TrinityDataCoreStorageStatus decoded = TrinityDataCoreStorageStatus.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(expected, decoded);
    }
}
