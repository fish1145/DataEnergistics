package com.fish_dan_.data_energistics.blockentity.machine.mimetic;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MimeticExternalIoBudgetTest {

    @Test
    void acceleratedInvocationsShareOneRealTickOperationLimit() {
        MimeticExternalIoBudget budget = new MimeticExternalIoBudget(2, 10L, () -> 0L);

        budget.begin(40L);
        assertTrue(budget.tryAcquire());
        assertTrue(budget.tryAcquire());
        assertFalse(budget.tryAcquire());

        budget.begin(40L);
        assertFalse(budget.tryAcquire());

        budget.begin(41L);
        assertTrue(budget.tryAcquire());
    }

    @Test
    void timeWindowStartsAtFirstExternalOperation() {
        AtomicLong nanoTime = new AtomicLong(100L);
        MimeticExternalIoBudget budget = new MimeticExternalIoBudget(4, 10L, nanoTime::get);

        budget.begin(60L);
        nanoTime.set(1_000L);
        assertTrue(budget.tryAcquire());

        nanoTime.set(1_009L);
        assertTrue(budget.tryAcquire());
        nanoTime.set(1_010L);
        assertFalse(budget.tryAcquire());

        budget.begin(61L);
        assertTrue(budget.tryAcquire());
    }
}
