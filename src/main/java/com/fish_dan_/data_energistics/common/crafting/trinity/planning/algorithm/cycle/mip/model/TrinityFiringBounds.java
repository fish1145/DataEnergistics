package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Exact inclusive domain for one logical pattern firing axis.
 *
 * @param lowerInclusive smallest permitted firing count
 * @param upperInclusive finite largest permitted firing count, or empty for an open BigInteger domain
 */
public record TrinityFiringBounds(BigInteger lowerInclusive, Optional<BigInteger> upperInclusive) {

    /**
     * Validates one non-empty, non-negative, exactly representable interval.
     */
    public TrinityFiringBounds {
        if (lowerInclusive.signum() < 0 ||
                upperInclusive.stream().anyMatch(upper -> lowerInclusive.compareTo(upper) > 0)) {
            throw new IllegalArgumentException("A Trinity firing domain must be ordered and non-negative");
        }
    }

    /** Creates one finite inclusive interval. */
    public TrinityFiringBounds(BigInteger lowerInclusive, BigInteger upperInclusive) {
        this(lowerInclusive, Optional.of(upperInclusive));
    }

    /**
     * @return complete non-negative BigInteger firing domain
     */
    public static TrinityFiringBounds full() {
        return new TrinityFiringBounds(BigInteger.ZERO, Optional.empty());
    }

    /**
     * Creates a singleton firing domain.
     */
    public static TrinityFiringBounds fixed(BigInteger value) {
        return new TrinityFiringBounds(value, value);
    }

    /** Returns whether no count above the lower bound is permitted. */
    public boolean fixed() {
        return this.upperInclusive.filter(this.lowerInclusive::equals).isPresent();
    }

    /** Caps an open interval only for one request-private finite model representation. */
    public BigInteger upperOr(BigInteger modelUpper) {
        return this.upperInclusive.map(modelUpper::min).orElse(modelUpper);
    }

    /** Returns whether a positive firing remains permitted. */
    public boolean permitsPositive() {
        return this.upperInclusive.map(upper -> upper.signum() > 0).orElse(true);
    }

    /**
     * @return whether the exact count lies inside this interval
     */
    public boolean contains(BigInteger value) {
        return lowerInclusive.compareTo(value) <= 0 &&
                upperInclusive.map(upper -> upper.compareTo(value) >= 0).orElse(true);
    }
}
