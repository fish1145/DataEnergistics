package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

/**
 * Conservatively maps compact graph accounting to AE2 crafting CPU byte capacity.
 * <p>
 * Exact integer implementation that rounds each stack contribution upward, making it no smaller than AE2's final
 * aggregate ceiling.
 */
public final class TrinityPlanByteEstimator {

    /**
     * @return stateless exact estimator
     */
    public static TrinityPlanByteEstimator create() {
        return new TrinityPlanByteEstimator();
    }

    private static final BigInteger CRAFTING_STORAGE_MULTIPLIER = BigInteger.valueOf(8L);

    /**
     * Uses AE2's eight-times stack accounting, logical firing bytes and eight bytes per tree node.
     *
     * @param input complete planner accounting
     * @return conservative capacity bytes
     */
    public BigInteger estimate(TrinityPlanByteEstimateInput input) {
        if (input == null) {
            throw new IllegalArgumentException("A Trinity byte estimate requires accounting input");
        }

        BigInteger bytes = input.patternFirings()
                .add(input.logicalNodeCount().multiply(CRAFTING_STORAGE_MULTIPLIER));
        for (Map.Entry<AEKey, BigInteger> entry : input.stackRequestAmounts().entrySet()) {
            int amountPerByte = entry.getKey().getAmountPerByte();
            if (amountPerByte <= 0) {
                throw new IllegalArgumentException("An AE key type must store a positive amount per byte");
            }
            BigInteger numerator = entry.getValue().multiply(CRAFTING_STORAGE_MULTIPLIER);
            BigInteger divisor = BigInteger.valueOf(amountPerByte);
            BigInteger[] division = numerator.divideAndRemainder(divisor);
            bytes = bytes.add(division[0]);
            if (division[1].signum() != 0) {
                bytes = bytes.add(BigInteger.ONE);
            }
        }
        return bytes;
    }
}
