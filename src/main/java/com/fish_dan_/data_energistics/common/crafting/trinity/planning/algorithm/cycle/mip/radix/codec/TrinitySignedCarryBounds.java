package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import java.math.BigInteger;

/**
 * Exact inclusive bounds for one signed carry between adjacent radix columns.
 *
 * @param lowerBound inclusive signed lower bound
 * @param upperBound inclusive signed upper bound
 */
public record TrinitySignedCarryBounds(BigInteger lowerBound, BigInteger upperBound) {

    /**
     * Validates one non-empty signed interval.
     */
    public TrinitySignedCarryBounds {
        if (lowerBound == null || upperBound == null || lowerBound.compareTo(upperBound) > 0) {
            throw new IllegalArgumentException("A Trinity signed carry requires an ordered exact interval");
        }
    }
}
