package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import java.math.BigInteger;
import java.util.List;

/**
 * Exact radix operations shared by model construction and post-solve verification.
 */
public interface TrinityRadixCodec {

    /**
     * @return stateless base-2^15 codec
     */
    static TrinityRadixCodec create() {
        return new TrinityRadixCodecImpl();
    }

    /**
     * Encodes a non-negative value using its minimum non-empty width.
     */
    TrinityRadixDigits encode(BigInteger value);

    /**
     * Encodes a non-negative value using an exact padded width.
     */
    TrinityRadixDigits encode(BigInteger value, int width);

    /**
     * Encodes a signed constant for constant-by-variable convolution.
     */
    TrinitySignedRadixDigits encodeSigned(BigInteger value);

    /**
     * Derives the next signed carry interval for one column. Each coefficient multiplies an unknown digit in
     * {@code [0, BASE - 1]}; the incoming carry and fixed right-hand digit are accounted exactly.
     */
    TrinitySignedCarryBounds nextCarryBounds(
                                             List<Integer> signedDigitCoefficients,
                                             TrinitySignedCarryBounds incomingCarry,
                                             int rightHandDigit);
}
