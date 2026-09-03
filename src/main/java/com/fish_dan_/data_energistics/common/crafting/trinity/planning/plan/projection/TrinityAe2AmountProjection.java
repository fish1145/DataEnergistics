package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.projection;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import java.math.BigInteger;
import java.util.Map;

/** Owns the single compatibility boundary between exact Trinity quantities and AE2's long-only public views. */
public final class TrinityAe2AmountProjection {

    private static final BigInteger MAX_AE2_AMOUNT = BigInteger.valueOf(Long.MAX_VALUE);

    private TrinityAe2AmountProjection() {}

    /** The returned counter is compatibility data; the source map remains the executable authority. */
    public static KeyCounter toKeyCounter(Map<AEKey, BigInteger> exactAmounts) {
        KeyCounter projected = new KeyCounter();
        exactAmounts.forEach((key, amount) -> projected.add(key, toAe2Amount(amount)));
        return projected;
    }

    /** Projects exact CPU-byte accounting into {@link appeng.api.networking.crafting.ICraftingPlan#bytes()}. */
    public static long toAe2Bytes(BigInteger exactBytes) {
        return toAe2Amount(exactBytes);
    }

    /** Projects one non-negative exact amount at an AE2 long-only API boundary. */
    public static long toAe2Amount(BigInteger exactAmount) {
        if (exactAmount.signum() < 0) {
            throw new IllegalArgumentException("An AE2 amount projection cannot be negative");
        }
        return exactAmount.compareTo(MAX_AE2_AMOUNT) >= 0 ? Long.MAX_VALUE : exactAmount.longValueExact();
    }

    /** Adds an exact amount to a pre-existing AE2 counter without allowing its long representation to wrap. */
    public static void addToKeyCounter(KeyCounter counter, AEKey key, BigInteger exactAmount) {
        long current = counter.get(key);
        if (current < 0L) {
            throw new IllegalArgumentException("An AE2 amount projection cannot extend a negative counter");
        }
        counter.set(key, toAe2Amount(BigInteger.valueOf(current).add(exactAmount)));
    }
}
