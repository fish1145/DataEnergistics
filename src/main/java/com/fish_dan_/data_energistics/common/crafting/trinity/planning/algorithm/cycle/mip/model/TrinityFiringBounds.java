package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import java.math.BigInteger;

/**
 * Exact inclusive domain for one logical pattern firing axis.
 *
 * @param lowerInclusive smallest permitted firing count
 * @param upperInclusive largest permitted firing count within the AE2 {@code long} boundary
 */
public record TrinityFiringBounds(BigInteger lowerInclusive, BigInteger upperInclusive) {

    /** Largest firing count representable by the downstream AE2 interfaces. */
    public static final BigInteger MAXIMUM_FIRINGS = BigInteger.valueOf(Long.MAX_VALUE);

    /**
     * Validates one non-empty, non-negative, exactly representable interval.
     */
    public TrinityFiringBounds {
        if (lowerInclusive == null || upperInclusive == null || lowerInclusive.signum() < 0 ||
                lowerInclusive.compareTo(upperInclusive) > 0 ||
                upperInclusive.compareTo(MAXIMUM_FIRINGS) > 0) {
            throw new IllegalArgumentException("A Trinity firing domain must be an ordered non-negative long interval");
        }
    }

    /**
     * @return complete exactly representable firing domain
     */
    public static TrinityFiringBounds full() {
        return new TrinityFiringBounds(BigInteger.ZERO, MAXIMUM_FIRINGS);
    }

    /**
     * Creates a singleton firing domain.
     */
    public static TrinityFiringBounds fixed(BigInteger value) {
        return new TrinityFiringBounds(value, value);
    }

    /**
     * @return whether the exact count lies inside this interval
     */
    public boolean contains(BigInteger value) {
        return value != null && lowerInclusive.compareTo(value) <= 0 && upperInclusive.compareTo(value) >= 0;
    }
}
