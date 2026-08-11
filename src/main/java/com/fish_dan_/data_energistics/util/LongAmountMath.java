package com.fish_dan_.data_energistics.util;

import java.math.BigInteger;

/**
 * Arithmetic for aggregating non-negative long amounts without wrapping requested totals below zero.
 */
public final class LongAmountMath {

    private static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

    /**
     * Prevents instantiation of this stateless arithmetic utility.
     */
    private LongAmountMath() {}

    /**
     * Adds two non-negative amounts and saturates at {@link Long#MAX_VALUE} when their exact sum is not representable.
     * Callers must uphold the non-negative input contract; requested-amount aggregation already guarantees it.
     *
     * @param left  existing non-negative amount
     * @param right non-negative amount to add
     * @return the exact sum or {@link Long#MAX_VALUE} on positive overflow
     */
    public static long saturatingAddNonNegative(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    /**
     * Multiplies two non-negative amounts and saturates at {@link Long#MAX_VALUE} when their exact product is not
     * representable. Callers must uphold the non-negative input contract.
     *
     * @param left  non-negative multiplicand
     * @param right non-negative multiplier
     * @return the exact product or {@link Long#MAX_VALUE} on positive overflow
     */
    public static long saturatingMultiplyNonNegative(long left, long right) {
        return left != 0L && right > Long.MAX_VALUE / left ? Long.MAX_VALUE : left * right;
    }

    /**
     * Converts one exact non-negative amount to AE2's signed-long transport range.
     *
     * @param amount exact non-negative amount
     * @return the exact value, or {@link Long#MAX_VALUE} when only a saturated transport view is representable
     */
    public static long saturatingLongValueNonNegative(BigInteger amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("A transport amount must not be negative");
        }
        return amount.compareTo(LONG_MAX_VALUE) > 0 ? Long.MAX_VALUE : amount.longValue();
    }
}
