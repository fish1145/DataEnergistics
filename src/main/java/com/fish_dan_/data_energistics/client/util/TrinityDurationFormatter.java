package com.fish_dan_.data_energistics.client.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formats one planning duration with a compact unit selected from microseconds, milliseconds, and seconds.
 */
public final class TrinityDurationFormatter {

    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private TrinityDurationFormatter() {}

    /**
     * @param nanos non-negative duration measured with {@link System#nanoTime()}
     * @return duration rounded to one decimal place with a dynamically selected unit
     */
    public static String formatNanos(long nanos) {
        if (nanos < 0L) {
            throw new IllegalArgumentException("A Trinity duration must not be negative");
        }
        if (nanos < NANOS_PER_MILLISECOND) {
            return format(nanos, 3, " μs");
        }
        if (nanos < NANOS_PER_SECOND) {
            return format(nanos, 6, " ms");
        }
        return format(nanos, 9, " s");
    }

    private static String format(long nanos, int scale, String unit) {
        BigDecimal rounded = BigDecimal.valueOf(nanos, scale).setScale(1, RoundingMode.HALF_EVEN);
        if (nanos > 0L && rounded.signum() == 0) {
            return "<0.1" + unit;
        }
        return rounded.stripTrailingZeros().toPlainString() + unit;
    }
}
