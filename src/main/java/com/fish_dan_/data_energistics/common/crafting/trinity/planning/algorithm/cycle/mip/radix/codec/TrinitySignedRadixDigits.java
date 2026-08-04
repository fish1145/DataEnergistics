package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import java.math.BigInteger;

/**
 * Sign plus unsigned base-2^15 magnitude used by constant-by-variable convolution.
 *
 * @param sign      {@code -1}, {@code 0}, or {@code 1}
 * @param magnitude exact unsigned magnitude digits
 */
public record TrinitySignedRadixDigits(int sign, TrinityRadixDigits magnitude) {

    /**
     * Rejects non-canonical sign/magnitude combinations.
     */
    public TrinitySignedRadixDigits {
        if (sign < -1 || sign > 1 || magnitude == null ||
                (sign == 0) != (magnitude.value().signum() == 0)) {
            throw new IllegalArgumentException("A Trinity signed radix constant must be canonical");
        }
    }

    /**
     * @return exact signed value
     */
    public BigInteger value() {
        return magnitude.value().multiply(BigInteger.valueOf(sign));
    }

    /**
     * @param coefficientDigit least-significant-first constant digit
     * @return signed convolution coefficient for a variable digit
     */
    public int signedCoefficient(int coefficientDigit) {
        return Math.multiplyExact(sign, magnitude.digit(coefficientDigit));
    }
}
