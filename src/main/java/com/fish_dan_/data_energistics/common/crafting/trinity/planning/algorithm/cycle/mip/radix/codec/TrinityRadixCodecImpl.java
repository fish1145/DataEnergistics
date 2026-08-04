package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * BigInteger-only codec; no floating-point value participates in encoding or carry bounds.
 */
final class TrinityRadixCodecImpl implements TrinityRadixCodec {

    private static final BigInteger BASE = BigInteger.valueOf(TrinityRadixDigits.BASE);
    private static final BigInteger MAX_DIGIT = BASE.subtract(BigInteger.ONE);

    @Override
    public TrinityRadixDigits encode(BigInteger value) {
        validateNonNegative(value);
        ArrayList<Integer> digits = new ArrayList<>();
        BigInteger remaining = value;
        do {
            BigInteger[] divided = remaining.divideAndRemainder(BASE);
            digits.add(divided[1].intValueExact());
            remaining = divided[0];
        } while (remaining.signum() > 0);
        return new TrinityRadixDigits(digits);
    }

    @Override
    public TrinityRadixDigits encode(BigInteger value, int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("A Trinity radix width must be positive");
        }
        ArrayList<Integer> digits = new ArrayList<>(encode(value).values());
        if (digits.size() > width) {
            throw new ArithmeticException("The Trinity radix value exceeds its fixed width");
        }
        while (digits.size() < width) {
            digits.add(0);
        }
        return new TrinityRadixDigits(digits);
    }

    @Override
    public TrinitySignedRadixDigits encodeSigned(BigInteger value) {
        if (value == null) {
            throw new IllegalArgumentException("A Trinity signed radix value is required");
        }
        return new TrinitySignedRadixDigits(value.signum(), encode(value.abs()));
    }

    @Override
    public TrinitySignedCarryBounds nextCarryBounds(
                                                    List<Integer> signedDigitCoefficients,
                                                    TrinitySignedCarryBounds incomingCarry,
                                                    int rightHandDigit) {
        if (signedDigitCoefficients == null || incomingCarry == null ||
                rightHandDigit < 0 || rightHandDigit >= TrinityRadixDigits.BASE) {
            throw new IllegalArgumentException("A Trinity radix column requires coefficients, carry, and one digit");
        }
        BigInteger minimum = incomingCarry.lowerBound().subtract(BigInteger.valueOf(rightHandDigit));
        BigInteger maximum = incomingCarry.upperBound().subtract(BigInteger.valueOf(rightHandDigit));
        for (Integer coefficient : signedDigitCoefficients) {
            if (coefficient == null || Math.abs((long) coefficient) >= TrinityRadixDigits.BASE) {
                throw new IllegalArgumentException("A Trinity radix column coefficient must fit one signed digit");
            }
            BigInteger extent = BigInteger.valueOf(coefficient.longValue()).multiply(MAX_DIGIT);
            if (coefficient < 0) {
                minimum = minimum.add(extent);
            } else {
                maximum = maximum.add(extent);
            }
        }
        return new TrinitySignedCarryBounds(floorDivide(minimum, BASE), floorDivide(maximum, BASE));
    }

    private static BigInteger floorDivide(BigInteger dividend, BigInteger divisor) {
        BigInteger[] result = dividend.divideAndRemainder(divisor);
        return result[1].signum() < 0 ? result[0].subtract(BigInteger.ONE) : result[0];
    }

    private static void validateNonNegative(BigInteger value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("A Trinity radix value cannot be negative or null");
        }
    }
}
