package com.fish_dan_.data_energistics.client.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityAmountFormatterTest {

    private static final BigInteger SCIENTIFIC_THRESHOLD = BigInteger.valueOf(1_024L).pow(7);

    @Test
    void preservesExistingCompactUnitsThroughExbibytes() {
        assertEquals("0", TrinityAmountFormatter.format("  "));
        assertEquals("1023", TrinityAmountFormatter.format("1023"));
        assertEquals("1K", TrinityAmountFormatter.format("1024"));
        assertEquals("1.5K", TrinityAmountFormatter.format("1536"));
        assertEquals("1E", TrinityAmountFormatter.format(BigInteger.valueOf(1_024L).pow(6).toString()));
        assertEquals("1023E", TrinityAmountFormatter.format(SCIENTIFIC_THRESHOLD.subtract(BigInteger.ONE).toString()));
    }

    @Test
    void usesScientificNotationAfterCompactUnitsAreExhausted() {
        assertEquals("1.181E21", TrinityAmountFormatter.format(SCIENTIFIC_THRESHOLD.toString()));
        assertEquals("1.235E29", TrinityAmountFormatter.format("123456789012345678901234567890"));
        assertEquals("-1.235E29", TrinityAmountFormatter.format("-123456789012345678901234567890"));
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThrows(NumberFormatException.class, () -> TrinityAmountFormatter.format("not-a-number"));
    }
}
