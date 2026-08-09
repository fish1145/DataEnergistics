package com.fish_dan_.data_energistics.ae2.key;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class SaturatingKeyCounterTest {

    private static final AEKey DATA_FLOW = DataFlowKey.of();

    @Test
    void mergesOrdinaryContributionsExactly() {
        KeyCounter total = counter(12L);

        SaturatingKeyCounter.merge(total, counter(30L));

        assertEquals(42L, total.get(DATA_FLOW));
    }

    @Test
    void saturatesContributionsThatWouldOverflowLong() {
        KeyCounter total = counter(Long.MAX_VALUE - 5L);

        SaturatingKeyCounter.merge(total, counter(10L));

        assertEquals(Long.MAX_VALUE, total.get(DATA_FLOW));
    }

    @Test
    void keepsSaturatedTotalsAtLongMax() {
        KeyCounter total = counter(Long.MAX_VALUE);

        SaturatingKeyCounter.merge(total, counter(1L));

        assertEquals(Long.MAX_VALUE, total.get(DATA_FLOW));
    }

    private static KeyCounter counter(long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(DATA_FLOW, amount);
        return counter;
    }
}
