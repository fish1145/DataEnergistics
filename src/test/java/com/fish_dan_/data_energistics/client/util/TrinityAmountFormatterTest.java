package com.fish_dan_.data_energistics.client.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityAmountFormatterTest {

    private static final BigInteger SCIENTIFIC_THRESHOLD = BigInteger.valueOf(1_000L).pow(39);

    @Test
    void usesGregTechMoreMachineDecimalUnits() {
        assertEquals("0", TrinityAmountFormatter.format("  "));
        assertEquals("999", TrinityAmountFormatter.format("999"));
        assertEquals("1K", TrinityAmountFormatter.format("1000"));
        assertEquals("1K", TrinityAmountFormatter.format("1023"));
        assertEquals("1.5K", TrinityAmountFormatter.format("1536"));
        assertEquals("1,000K", TrinityAmountFormatter.format("999999"));
        assertEquals("1M", TrinityAmountFormatter.format("1000000"));
        assertEquals("-1.5K", TrinityAmountFormatter.format("-1536"));
        assertEquals("1Att", TrinityAmountFormatter.format(BigInteger.valueOf(1_000L).pow(38).toString()));
    }

    @Test
    void usesGregTechMoreMachineScientificFallbackAfterAtt() {
        assertEquals("1.00E117", TrinityAmountFormatter.format(SCIENTIFIC_THRESHOLD.toString()));
        assertEquals("-1.00E117", TrinityAmountFormatter.format(SCIENTIFIC_THRESHOLD.negate().toString()));
    }

    @Test
    void keepsDigitsAndUnitsSeparateForAe2Tooltips() {
        assertEquals(
                new TrinityAmountFormatter.FormattedAmount("9.2", "E"),
                TrinityAmountFormatter.formatParts(Long.MAX_VALUE));
        assertEquals(
                new TrinityAmountFormatter.FormattedAmount("0", ""),
                TrinityAmountFormatter.formatParts(0L));
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThrows(NumberFormatException.class, () -> TrinityAmountFormatter.format("not-a-number"));
    }
}
