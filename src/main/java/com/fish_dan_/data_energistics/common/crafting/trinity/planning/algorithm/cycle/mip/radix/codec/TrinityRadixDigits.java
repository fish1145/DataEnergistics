package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import java.math.BigInteger;
import java.util.List;

/**
 * Fixed-width unsigned base-2^8 digits stored least-significant first.
 *
 * @param values digit values in {@code [0, 255]}
 */
public record TrinityRadixDigits(List<Integer> values) {

    /** Exact base selected so every digit and digit-product coefficient remains safely integral in ojAlgo. */
    public static final int BASE = 1 << 8;

    /**
     * Freezes the trusted codec output once.
     */
    public TrinityRadixDigits {
        values = List.copyOf(values);
    }

    /**
     * @return exact non-negative reconstructed value
     */
    public BigInteger value() {
        BigInteger result = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(BASE);
        for (int index = values.size() - 1; index >= 0; index--) {
            result = result.multiply(base).add(BigInteger.valueOf(values.get(index)));
        }
        return result;
    }

    /**
     * @param index least-significant-first index
     * @return encoded digit or zero beyond this fixed width
     */
    public int digit(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("A Trinity radix digit index cannot be negative");
        }
        return index < values.size() ? values.get(index) : 0;
    }
}
