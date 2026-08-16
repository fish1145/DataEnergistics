package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class WorkerOperationTrackerTest {

    @Test
    void workersTrackTheirDispatchHistoryIndependently() {
        WorkerOperationTracker first = WorkerOperationTracker.create();
        WorkerOperationTracker second = WorkerOperationTracker.create();

        first.recordTickUsage(10L, 4);

        assertEquals(4L, first.recentOperations(10L));
        assertEquals(0L, second.recentOperations(10L));
    }

    @Test
    void idleTicksExpireOldDispatchHistory() {
        WorkerOperationTracker tracker = WorkerOperationTracker.create();

        tracker.recordTickUsage(30L, 4);

        assertEquals(4L, tracker.recentOperations(31L));
        assertEquals(4L, tracker.recentOperations(32L));
        assertEquals(0L, tracker.recentOperations(33L));
    }

    @Test
    void rejectsInvalidAccountingValues() {
        WorkerOperationTracker tracker = WorkerOperationTracker.create();

        assertThrows(IllegalArgumentException.class, () -> tracker.recordTickUsage(0L, -1));
        tracker.recordTickUsage(10L, 1);
        tracker.recordTickUsage(10L, 2);
        assertEquals(3L, tracker.recentOperations(10L));
        assertThrows(IllegalArgumentException.class, () -> tracker.recentOperations(9L));
    }
}
