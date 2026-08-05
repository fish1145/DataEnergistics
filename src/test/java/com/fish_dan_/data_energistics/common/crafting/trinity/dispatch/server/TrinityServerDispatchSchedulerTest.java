package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityServerDispatchSchedulerTest {

    @Test
    void alternatesGridsAfterEveryPhysicalAttempt() {
        TrinityServerDispatchScheduler scheduler = TrinityServerDispatchScheduler.create();
        List<String> order = new ArrayList<>();
        RecordingParticipant first = RecordingParticipant.physical("first", 3, order);
        RecordingParticipant second = RecordingParticipant.physical("second", 2, order);

        scheduler.beginTick();
        scheduler.register(first);
        scheduler.register(second);
        scheduler.dispatchTick();

        assertEquals(List.of("first", "second", "first", "second", "first"), order);
        assertEquals(1, first.completions);
        assertEquals(1, second.completions);
    }

    @Test
    void noProgressParticipantDoesNotConsumeLaterOpportunitiesOrBusyLoop() {
        TrinityServerDispatchScheduler scheduler = TrinityServerDispatchScheduler.create();
        List<String> order = new ArrayList<>();
        RecordingParticipant stalled = RecordingParticipant.stalled("stalled");
        RecordingParticipant active = RecordingParticipant.physical("active", 2, order);

        scheduler.beginTick();
        scheduler.register(stalled);
        scheduler.register(active);
        scheduler.dispatchTick();

        assertEquals(1, stalled.steps);
        assertEquals(List.of("active", "active"), order);
    }

    @Test
    void logicalProgressCanRequeueOnceBeforeACompleteNoProgressRoundStops() {
        TrinityServerDispatchScheduler scheduler = TrinityServerDispatchScheduler.create();
        RecordingParticipant participant = RecordingParticipant.oneStateTransition();

        scheduler.beginTick();
        scheduler.register(participant);
        scheduler.dispatchTick();

        assertEquals(2, participant.steps);
        assertEquals(1, participant.completions);
    }

    @Test
    void isolatesOneRuntimeFailureAndContinuesOtherGrids() {
        TrinityServerDispatchScheduler scheduler = TrinityServerDispatchScheduler.create();
        List<String> order = new ArrayList<>();
        RecordingParticipant failed = RecordingParticipant.failed();
        RecordingParticipant active = RecordingParticipant.physical("active", 1, order);

        scheduler.beginTick();
        scheduler.register(failed);
        scheduler.register(active);
        scheduler.dispatchTick();

        assertEquals(1, failed.failures);
        assertEquals(1, failed.completions);
        assertEquals(List.of("active"), order);
        assertEquals(1, active.completions);
    }

    @Test
    void rejectsRegistrationOutsideAnOpenTick() {
        TrinityServerDispatchScheduler scheduler = TrinityServerDispatchScheduler.create();
        RecordingParticipant participant = RecordingParticipant.stalled("participant");

        assertThrows(IllegalStateException.class, () -> scheduler.register(participant));
        scheduler.beginTick();
        assertThrows(IllegalStateException.class, scheduler::beginTick);
        scheduler.reset();
        assertThrows(IllegalStateException.class, scheduler::dispatchTick);
    }

    private static final class RecordingParticipant implements CraftingDispatchParticipant {

        private final String identity;
        private final List<String> physicalOrder;
        private int remainingPhysicalAttempts;
        private Mode mode;
        private int steps;
        private int completions;
        private int failures;

        private RecordingParticipant(String identity,
                                     int remainingPhysicalAttempts,
                                     List<String> physicalOrder,
                                     Mode mode) {
            this.identity = identity;
            this.remainingPhysicalAttempts = remainingPhysicalAttempts;
            this.physicalOrder = physicalOrder;
            this.mode = mode;
        }

        private static RecordingParticipant physical(String identity, int attempts, List<String> order) {
            return new RecordingParticipant(identity, attempts, order, Mode.PHYSICAL);
        }

        private static RecordingParticipant stalled(String identity) {
            return new RecordingParticipant(identity, 0, List.of(), Mode.STALLED);
        }

        private static RecordingParticipant oneStateTransition() {
            return new RecordingParticipant("transition", 0, List.of(), Mode.STATE_TRANSITION);
        }

        private static RecordingParticipant failed() {
            return new RecordingParticipant("failed", 0, List.of(), Mode.FAILURE);
        }

        @Override
        public String diagnosticIdentity() {
            return this.identity;
        }

        @Override
        public CraftingDispatchStepResult dispatchStep() {
            this.steps++;
            return switch (this.mode) {
                case PHYSICAL -> physicalStep();
                case STALLED -> new CraftingDispatchStepResult(false, false, true, false);
                case STATE_TRANSITION -> {
                    this.mode = Mode.STALLED;
                    yield new CraftingDispatchStepResult(false, true, true, false);
                }
                case FAILURE -> throw new IllegalStateException("expected test failure");
            };
        }

        private CraftingDispatchStepResult physicalStep() {
            this.physicalOrder.add(this.identity);
            this.remainingPhysicalAttempts--;
            return new CraftingDispatchStepResult(
                    true,
                    false,
                    this.remainingPhysicalAttempts > 0,
                    false);
        }

        @Override
        public void completeTick() {
            this.completions++;
        }

        @Override
        public void recordUnexpectedFailure(String source, RuntimeException failure) {
            this.failures++;
        }

        private enum Mode {
            PHYSICAL,
            STALLED,
            STATE_TRANSITION,
            FAILURE
        }
    }
}
