package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import com.fish_dan_.data_energistics.ae2.DataKey;

import appeng.api.stacks.AEKey;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityPlanByteEstimatorTest {

    private static final AEKey KEY = DataKey.of();
    private final TrinityPlanByteEstimator estimator = TrinityPlanByteEstimator.create();

    @Test
    void matchesAe2ComponentsWithConservativeIntegerCeiling() {
        int amountPerByte = KEY.getAmountPerByte();
        BigInteger requested = BigInteger.valueOf(amountPerByte).add(BigInteger.ONE);
        long stackBytes = requested.multiply(BigInteger.valueOf(8L))
                .add(BigInteger.valueOf(amountPerByte - 1L))
                .divide(BigInteger.valueOf(amountPerByte))
                .longValueExact();

        long bytes = this.estimator.estimate(new TrinityPlanByteEstimateInput(
                Map.of(KEY, requested),
                BigInteger.valueOf(3L),
                BigInteger.valueOf(2L)));

        assertEquals(stackBytes + 3L + 16L, bytes);
    }

    @Test
    void reportsExactAe2LongOverflow() {
        assertThrows(
                ArithmeticException.class,
                () -> this.estimator.estimate(new TrinityPlanByteEstimateInput(
                        Map.of(),
                        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                        BigInteger.ZERO)));
    }
}
