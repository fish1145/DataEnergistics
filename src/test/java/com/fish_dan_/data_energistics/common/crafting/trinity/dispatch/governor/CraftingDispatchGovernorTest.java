package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CraftingDispatchGovernorTest {

    @Test
    void fakeMetricsDriveTheObservationWindowWithoutChangingPhysicalBudgets() {
        CraftingDispatchBudget hard = budget(256, 16, 30L, 256, 16, 1024, 1, true);
        CraftingDispatchBudget safe = budget(16, 2, 2L, 1, 2, 1, 1, false);
        CraftingDispatchGovernor governor = CraftingDispatchGovernor.create(
                CraftingDispatchGovernorSettings.defaults(hard, safe, 4, 2, 0.25D, 3, 60, 200));

        governor.observe(metrics(40L, 2, 8, 1, 0, 0, 0.25D));
        governor.observe(metrics(50L, 6, 8, 1, 1, 1, 0.75D));

        CraftingDispatchGovernorSnapshot snapshot = governor.snapshot();
        assertEquals(CraftingDispatchGovernorState.OBSERVING, snapshot.state());
        assertSame(hard, snapshot.budget());
        assertEquals(2L, snapshot.observedTicks());
        assertEquals(1L, snapshot.completedWindows());
        assertEquals(TimeUnit.MILLISECONDS.toNanos(42L) + TimeUnit.MICROSECONDS.toNanos(500L),
                snapshot.tickEwmaNanos());
        assertEquals(0.5D, snapshot.lastQueueRatio());
        assertEquals(0.25D, snapshot.lastStaleRatio());
        assertEquals(2.0D / 3.0D, snapshot.lastAcceptanceRatio());
        assertEquals(0.75D, snapshot.lastBusiestWorkerShare());
        assertEquals(0, snapshot.lastProposalFailures());
    }

    @Test
    void fakeMetricsDriveAdaptiveDownshiftRecoveryAndSafeFallback() {
        CraftingDispatchBudget hard = budget(64, 8, 12L, 8, 8, 32, 1, true);
        CraftingDispatchBudget safe = budget(16, 2, 2L, 1, 2, 1, 4, false);
        CraftingDispatchGovernor governor = CraftingDispatchGovernor.create(
                CraftingDispatchGovernorSettings.defaults(hard, safe, 2, 1, 1.0D, 2, 2, 3));

        governor.observe(metrics(30L, 0, 8, 8, 0, 0, 0.25D));
        governor.observe(metrics(30L, 0, 8, 8, 0, 0, 0.25D));
        assertEquals(CraftingDispatchGovernorState.ADAPTIVE, governor.snapshot().state());

        governor.observe(metrics(50L, 8, 8, 1, 0, 0, 0.25D));
        governor.observe(metrics(50L, 8, 8, 1, 0, 0, 0.25D));
        CraftingDispatchBudget firstDownshift = governor.budget();
        assertEquals(48, firstDownshift.dispatchLimits().maxAttemptsPerGrid());
        assertEquals(6, firstDownshift.dispatchLimits().maxAttemptsPerProvider());
        assertEquals(TimeUnit.MILLISECONDS.toNanos(9L), firstDownshift.dispatchLimits().maxServerSubmissionNanos());
        assertEquals(1, firstDownshift.actorPermits());
        assertEquals(6, firstDownshift.providerQuantum());
        assertEquals(24, firstDownshift.proposalHighWater());
        assertEquals(2, firstDownshift.retryBackoffTicks());
        assertTrue(firstDownshift.asynchronousEnabled());

        governor.observe(metrics(50L, 8, 8, 1, 0, 0, 0.25D));
        assertSame(firstDownshift, governor.budget());
        governor.observe(metrics(50L, 8, 8, 1, 0, 0, 0.25D));
        assertEquals(36, governor.budget().dispatchLimits().maxAttemptsPerGrid());
        assertEquals(3, governor.budget().retryBackoffTicks());

        governor.observe(metrics(30L, 0, 8, 8, 0, 0, 0.25D));
        governor.observe(metrics(30L, 0, 8, 8, 0, 0, 0.25D));
        assertEquals(44, governor.budget().dispatchLimits().maxAttemptsPerGrid());
        assertEquals(2, governor.budget().retryBackoffTicks());

        governor.observe(metrics(101L, 0, 8, 8, 0, 0, 0.25D));
        governor.observe(metrics(101L, 0, 8, 8, 0, 0, 0.25D));
        assertEquals(CraftingDispatchGovernorState.SAFE, governor.snapshot().state());
        assertSame(safe, governor.budget());

        governor.observe(metrics(30L, 0, 8, 8, 0, 0, 0.25D));
        governor.observe(metrics(30L, 0, 8, 8, 0, 0, 0.25D));
        assertEquals(CraftingDispatchGovernorState.SAFE, governor.snapshot().state());
        governor.observe(metrics(30L, 0, 8, 8, 0, 0, 0.25D));
        assertEquals(CraftingDispatchGovernorState.OBSERVING, governor.snapshot().state());
        assertSame(hard, governor.budget());

        governor.recordUnexpectedFailure("test Actor", new IllegalStateException("expected test failure"));
        assertEquals(CraftingDispatchGovernorState.SAFE, governor.snapshot().state());
        assertSame(safe, governor.budget());
    }

    @Test
    void serverBudgetSharesHeadroomAndRetainsOneMillisecondWhenAlreadyOverloaded() {
        AtomicLong nanoClock = new AtomicLong();
        CraftingServerDispatchBudgetImpl budget = new CraftingServerDispatchBudgetImpl(
                nanoClock::get,
                TimeUnit.MILLISECONDS.toNanos(50L),
                TimeUnit.MILLISECONDS.toNanos(1L));

        budget.beginTick();
        nanoClock.set(TimeUnit.MILLISECONDS.toNanos(10L));
        budget.completeTick(TimeUnit.MILLISECONDS.toNanos(10L));

        budget.beginTick();
        budget.record(TimeUnit.MILLISECONDS.toNanos(25L));
        assertTrue(budget.canStart(TimeUnit.MILLISECONDS.toNanos(14L)));
        assertFalse(budget.canStart(TimeUnit.MILLISECONDS.toNanos(15L)));
        budget.completeTick(TimeUnit.MILLISECONDS.toNanos(50L));
        assertEquals(TimeUnit.MILLISECONDS.toNanos(25L), budget.lastCompletedDispatchNanos());

        budget.beginTick();
        budget.completeTick(TimeUnit.MILLISECONDS.toNanos(60L));
        budget.beginTick();
        assertTrue(budget.canStart(TimeUnit.MICROSECONDS.toNanos(999L)));
        assertFalse(budget.canStart(TimeUnit.MILLISECONDS.toNanos(1L)));
    }

    @Test
    void actorPermitsScaleWithTheCurrentGridAttemptWindow() {
        assertEquals(1, CraftingDispatchBudget.actorPermitsFor(1));
        assertEquals(1, CraftingDispatchBudget.actorPermitsFor(256));
        assertEquals(2, CraftingDispatchBudget.actorPermitsFor(257));
        assertEquals(128, CraftingDispatchBudget.actorPermitsFor(32_768));
        assertEquals(256, CraftingDispatchBudget.actorPermitsFor(Integer.MAX_VALUE));
    }

    private static CraftingDispatchBudget budget(
                                                 int gridAttempts,
                                                 int providerAttempts,
                                                 long commitMillis,
                                                 int actorPermits,
                                                 int providerQuantum,
                                                 int proposalHighWater,
                                                 int retryBackoffTicks,
                                                 boolean asynchronous) {
        return new CraftingDispatchBudget(
                new CraftingDispatchLimits(
                        gridAttempts,
                        providerAttempts,
                        TimeUnit.MILLISECONDS.toNanos(commitMillis)),
                actorPermits,
                providerQuantum,
                proposalHighWater,
                retryBackoffTicks,
                asynchronous);
    }

    private static CraftingDispatchMetrics metrics(
                                                   long tickMillis,
                                                   int queueDepth,
                                                   int queueCapacity,
                                                   int accepted,
                                                   int rejected,
                                                   int stale,
                                                   double workerShare) {
        return new CraftingDispatchMetrics(
                TimeUnit.MILLISECONDS.toNanos(tickMillis),
                0L,
                0L,
                0L,
                0L,
                0,
                0,
                0,
                0,
                accepted,
                rejected,
                stale,
                BigInteger.valueOf(accepted),
                Math.addExact(Math.addExact(accepted, rejected), stale),
                queueDepth,
                queueCapacity,
                0,
                workerShare);
    }
}
