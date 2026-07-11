package com.fish_dan_.data_energistics.client.util;

import java.text.DecimalFormat;

/**
 * Formats non-negative AE2 counts and byte capacities without overflowing the available unit table.
 *
 * <p>
 * The thresholds, precision, and unit spelling intentionally follow AE2's tooltip formatting for existing ranges.
 * Additional binary {@code P} and {@code E} scales keep very large virtual capacities representable.
 * </p>
 */
public final class Ae2AmountFormatter {

    /** Decimal scales paired with {@link #UNITS}. */
    private static final long[] DECIMAL_SCALES = {
            1_000L,
            1_000_000L,
            1_000_000_000L,
            1_000_000_000_000L,
            1_000_000_000_000_000L,
            1_000_000_000_000_000_000L
    };
    /** Binary byte scales paired with {@link #UNITS}. */
    private static final long[] BYTE_SCALES = {
            1L << 10,
            1L << 20,
            1L << 30,
            1L << 40,
            1L << 50,
            1L << 60
    };
    /** Compact suffixes shared by decimal counts and binary byte capacities. */
    private static final String[] UNITS = { "k", "M", "G", "T", "P", "E" };
    /** Locale-specific decimal separator used when trimming formatted fractions. */
    private static final char DECIMAL_SEPARATOR = ((DecimalFormat) DecimalFormat.getInstance())
            .getDecimalFormatSymbols()
            .getDecimalSeparator();

    private Ae2AmountFormatter() {}

    /**
     * Formats a non-negative count using AE2's decimal compact-number rules.
     *
     * @param amount count in the inclusive range {@code 0..Long.MAX_VALUE}
     * @return separated digits and suffix
     */
    public static FormattedAmount formatAmount(long amount) {
        validateAmount(amount);
        if (amount < 10_000L) {
            return new FormattedAmount(Long.toString(amount), "");
        }
        return formatScaled(amount, DECIMAL_SCALES);
    }

    /**
     * Formats a non-negative byte capacity using AE2's existing binary rules plus {@code P} and {@code E} scales.
     *
     * @param amount byte capacity in the inclusive range {@code 0..Long.MAX_VALUE}
     * @return separated digits and suffix
     */
    public static FormattedAmount formatByteAmount(long amount) {
        validateAmount(amount);
        if (amount < BYTE_SCALES[0]) {
            return new FormattedAmount(Long.toString(amount), "");
        }
        return formatScaled(amount, BYTE_SCALES);
    }

    private static FormattedAmount formatScaled(long amount, long[] scales) {
        int index = 0;
        while (index + 1 < scales.length && amount / scales[index] >= 1_000L) {
            index++;
        }
        return new FormattedAmount(formatDigits(amount, scales[index]), UNITS[index]);
    }

    private static String formatDigits(long amount, long scale) {
        double fraction = (double) amount / scale;
        String digits;
        if (fraction < 10.0D) {
            digits = String.format("%.3f", fraction);
        } else if (fraction < 100.0D) {
            digits = String.format("%.2f", fraction);
        } else {
            digits = String.format("%.1f", fraction);
        }
        while (digits.endsWith("0")) {
            digits = digits.substring(0, digits.length() - 1);
        }
        if (digits.endsWith(String.valueOf(DECIMAL_SEPARATOR))) {
            digits = digits.substring(0, digits.length() - 1);
        }
        return digits;
    }

    private static void validateAmount(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("AE2 compact amount must not be negative");
        }
    }

    /**
     * One compact numeric value split so callers can preserve AE2's number and unit styling.
     *
     * @param digits localized numeric portion
     * @param unit   compact suffix, or an empty string for unscaled values
     */
    public record FormattedAmount(String digits, String unit) {

        /**
         * Joins the numeric portion and suffix for controls that render a plain string.
         *
         * @return complete compact value
         */
        public String text() {
            return this.digits + this.unit;
        }
    }
}
