package com.fish_dan_.data_energistics.util;

/** Arithmetic for aggregating non-negative long amounts without wrapping requested totals below zero. */
public final class LongAmountMath {

    /** Prevents instantiation of this stateless arithmetic utility. */
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
}
