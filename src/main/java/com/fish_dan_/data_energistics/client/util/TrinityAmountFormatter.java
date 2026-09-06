package com.fish_dan_.data_energistics.client.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats Trinity and AE2 amounts with GregTech-MoreMachine's decimal compact-unit convention.
 *
 * <p>
 * Values advance by powers of one thousand through the same extended unit sequence used by both maintained
 * GregTech-MoreMachine branches. Values beyond {@code Att} use scientific notation, so arbitrary-precision Trinity
 * capacities remain representable without falling back to AE2's binary byte units.
 * </p>
 */
public final class TrinityAmountFormatter {

    private static final BigInteger UNIT_BASE = BigInteger.valueOf(1_000L);
    private static final String[] COMPACT_UNITS = {
            "", "K", "M", "G", "T", "P", "E", "Z", "Y", "B", "N", "D", "C", "S", "O", "Q", "X", "W", "V",
            "U", "Tt", "Gt", "Mt", "St", "Ot", "Nt", "Dt", "Ct", "Lt", "Kt", "Jt", "It", "Ht", "Gtt", "Ett",
            "Dtt", "Ctt", "Btt", "Att"
    };
    private static final BigDecimal SCIENTIFIC_THRESHOLD = new BigDecimal(UNIT_BASE.pow(COMPACT_UNITS.length));

    private TrinityAmountFormatter() {}

    /**
     * Parses and formats one signed decimal integer.
     *
     * @param value signed decimal integer, with surrounding whitespace permitted
     * @return compact display text
     */
    public static String format(String value) {
        if (value.isBlank()) {
            return "0";
        }
        return format(new BigInteger(value.trim()));
    }

    /**
     * Formats one signed {@code long} without converting through {@code double}.
     *
     * @param value amount to format
     * @return compact display text
     */
    public static String format(long value) {
        return format(BigInteger.valueOf(value));
    }

    /**
     * Formats one arbitrary-precision signed integer.
     *
     * @param value amount to format
     * @return compact display text
     */
    public static String format(BigInteger value) {
        return formatParts(value).text();
    }

    /** Formats an exact amount expressed in display units, including fractional fluid units. */
    public static String format(BigDecimal value) {
        return formatParts(value).text();
    }

    /**
     * Formats one {@code long} while keeping the numeric and unit portions separate for AE2 tooltip coloring.
     *
     * @param value amount to format
     * @return separated numeric text and compact unit
     */
    public static FormattedAmount formatParts(long value) {
        return formatParts(BigInteger.valueOf(value));
    }

    /**
     * Formats one arbitrary-precision integer while keeping its suffix separate.
     *
     * @param value amount to format
     * @return separated numeric text and compact unit
     */
    public static FormattedAmount formatParts(BigInteger value) {
        return formatParts(new BigDecimal(value));
    }

    private static FormattedAmount formatParts(BigDecimal value) {
        if (value.signum() == 0) {
            return new FormattedAmount("0", "");
        }

        BigDecimal absoluteAmount = value.abs();
        if (absoluteAmount.compareTo(SCIENTIFIC_THRESHOLD) >= 0) {
            return new FormattedAmount(formatScientific(value), "");
        }

        int unitIndex = Math.max(0, (absoluteAmount.precision() - absoluteAmount.scale() - 1) / 3);
        BigDecimal scaledAmount = absoluteAmount.movePointLeft(unitIndex * 3);
        String digits = compactFormat().format(scaledAmount);
        if (value.signum() < 0) {
            digits = "-" + digits;
        }
        return new FormattedAmount(digits, COMPACT_UNITS[unitIndex]);
    }

    private static String formatScientific(BigDecimal amount) {
        DecimalFormat format = decimalFormat("0.00E00");
        return format.format(amount);
    }

    private static DecimalFormat compactFormat() {
        return decimalFormat("#,##0.#");
    }

    private static DecimalFormat decimalFormat(String pattern) {
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        return format;
    }

    /**
     * One compact value split so AE2 callers can retain their separate number and unit styles.
     *
     * @param digits formatted numeric portion, including a sign when negative
     * @param unit   compact suffix, or an empty string for unscaled/scientific values
     */
    public record FormattedAmount(String digits, String unit) {

        /**
         * Joins the numeric portion and suffix for plain-text controls.
         *
         * @return complete formatted value
         */
        public String text() {
            return this.digits + this.unit;
        }
    }
}
