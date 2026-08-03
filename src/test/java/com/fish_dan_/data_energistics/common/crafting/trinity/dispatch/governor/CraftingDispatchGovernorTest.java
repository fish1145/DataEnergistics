package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public final class CraftingDispatchGovernorTest {

    @Test
    void fakeMetricsDriveTheObservationWindowWithoutChangingPhysicalBudgets() {
        CraftingDispatchBudget hard = budget(256, 16, 30L, 256, 16, 1024, true);
        CraftingDispatchBudget safe = budget(16, 2, 2L, 1, 2, 1, false);
        CraftingDispatchGovernor governor = CraftingDispatchGovernor.create(
                CraftingDispatchGovernorSettings.defaults(hard, safe, 2, 2, 0.25D, 3, 60, 200));

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

    private static CraftingDispatchBudget budget(
            int gridAttempts,
            int providerAttempts,
            long commitMillis,
            int actorPermits,
            int providerQuantum,
            int proposalHighWater,
            boolean asynchronous) {
        return new CraftingDispatchBudget(
                new CraftingDispatchLimits(
                        gridAttempts,
                        providerAttempts,
                        TimeUnit.MILLISECONDS.toNanos(commitMillis)),
                actorPermits,
                providerQuantum,
                proposalHighWater,
                1,
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
