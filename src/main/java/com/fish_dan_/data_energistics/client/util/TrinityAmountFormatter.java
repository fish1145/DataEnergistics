package com.fish_dan_.data_energistics.client.util;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formats Trinity's arbitrary-precision storage amounts for compact UI display. */
public final class TrinityAmountFormatter {

    private static final BigInteger UNIT_BASE = BigInteger.valueOf(1_024L);
    private static final String[] COMPACT_UNITS = { "", "K", "M", "G", "T", "P", "E" };
    private static final BigInteger SCIENTIFIC_THRESHOLD = UNIT_BASE.pow(COMPACT_UNITS.length);

    private TrinityAmountFormatter() {}

    /**
     * Uses binary compact units through exbibytes, then switches to four-significant-digit scientific notation.
     *
     * @param value signed decimal integer, with surrounding whitespace permitted
     * @return compact display text
     */
    public static String format(String value) {
        if (value.isBlank()) {
            return "0";
        }
        BigInteger amount = new BigInteger(value.trim());
        if (amount.signum() == 0) {
            return "0";
        }

        BigInteger absoluteAmount = amount.abs();
        if (absoluteAmount.compareTo(SCIENTIFIC_THRESHOLD) >= 0) {
            return formatScientific(amount);
        }

        BigInteger divisor = BigInteger.ONE;
        int unitIndex = 0;
        while (unitIndex < COMPACT_UNITS.length - 1 && absoluteAmount.compareTo(divisor.multiply(UNIT_BASE)) >= 0) {
            divisor = divisor.multiply(UNIT_BASE);
            unitIndex++;
        }
        if (unitIndex == 0) {
            return amount.toString();
        }

        BigInteger whole = absoluteAmount.divide(divisor);
        BigInteger fraction = absoluteAmount.remainder(divisor).multiply(BigInteger.TEN).divide(divisor);
        String sign = amount.signum() < 0 ? "-" : "";
        if (whole.compareTo(BigInteger.TEN) >= 0 || fraction.signum() == 0) {
            return sign + whole + COMPACT_UNITS[unitIndex];
        }
        return sign + whole + "." + fraction + COMPACT_UNITS[unitIndex];
    }

    private static String formatScientific(BigInteger amount) {
        DecimalFormat format = new DecimalFormat("0.###E0", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount);
    }
}
