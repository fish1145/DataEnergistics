package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server;

import com.fish_dan_.data_energistics.Data_Energistics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-thread-confined scheduler that switches Grid after every real physical provider call.
 */
final class TrinityServerDispatchSchedulerImpl implements TrinityServerDispatchScheduler {

    private static final String STEP_FAILURE_SOURCE = "server dispatch step";
    private static final String COMPLETION_FAILURE_SOURCE = "server dispatch completion";

    private final List<CraftingDispatchParticipant> registeredParticipants = new ArrayList<>();
    private String nextParticipantIdentity;
    private boolean tickOpen;

    @Override
    public void beginTick() {
        if (this.tickOpen) {
            throw new IllegalStateException("Previous Trinity server dispatch tick was not completed");
        }
        this.registeredParticipants.clear();
        this.tickOpen = true;
    }

    @Override
    public void register(CraftingDispatchParticipant participant) {
        if (!this.tickOpen) {
            throw new IllegalStateException("Trinity server dispatch registration is closed");
        }
        if (participant == null) {
            throw new IllegalArgumentException("Crafting dispatch participant is required");
        }
        String diagnosticIdentity = participant.diagnosticIdentity();
        if (diagnosticIdentity == null || diagnosticIdentity.isBlank()) {
            throw new IllegalArgumentException("Crafting dispatch participant identity is required");
        }
        this.registeredParticipants.add(participant);
    }

    @Override
    public void dispatchTick() {
        if (!this.tickOpen) {
            throw new IllegalStateException("Trinity server dispatch tick is not open");
        }
        this.tickOpen = false;
        List<CraftingDispatchParticipant> participants = List.copyOf(this.registeredParticipants);
        this.registeredParticipants.clear();
        if (participants.isEmpty()) {
            return;
        }
        try {
            dispatchParticipants(participantsFromPersistentCursor(participants));
        } finally {
            completeParticipants(participants);
        }
    }

    @Override
    public void reset() {
        this.registeredParticipants.clear();
        this.nextParticipantIdentity = null;
        this.tickOpen = false;
    }

    private List<CraftingDispatchParticipant> participantsFromPersistentCursor(
                                                                               List<CraftingDispatchParticipant> participants) {
        if (participants.size() < 2 || this.nextParticipantIdentity == null) {
            return participants;
        }
        int start = -1;
        for (int index = 0; index < participants.size(); index++) {
            if (this.nextParticipantIdentity.equals(participants.get(index).diagnosticIdentity())) {
                start = index;
                break;
            }
        }
        if (start <= 0) {
            return participants;
        }
        List<CraftingDispatchParticipant> rotated = new ArrayList<>(participants.size());
        rotated.addAll(participants.subList(start, participants.size()));
        rotated.addAll(participants.subList(0, start));
        return List.copyOf(rotated);
    }

    private void dispatchParticipants(List<CraftingDispatchParticipant> participants) {
        Map<CraftingDispatchParticipant, String> successorIdentities = successorIdentities(participants);
        ArrayDeque<CraftingDispatchParticipant> ready = new ArrayDeque<>(participants);
        int remainingInRound = ready.size();
        boolean roundProgressed = false;
        while (!ready.isEmpty()) {
            CraftingDispatchParticipant participant = ready.removeFirst();
            CraftingDispatchStepResult result;
            try {
                result = participant.dispatchStep();
                if (result == null) {
                    throw new IllegalStateException("Crafting dispatch participant returned no step result");
                }
            } catch (RuntimeException failure) {
                isolate(participant, STEP_FAILURE_SOURCE, failure);
                result = CraftingDispatchStepResult.IDLE;
            }

            if (result.physicalAttempted()) {
                this.nextParticipantIdentity = successorIdentities.get(participant);
            }
            roundProgressed |= result.progressed();
            if (result.progressed() && result.hasReadyWork() && !result.windowExhausted()) {
                ready.addLast(participant);
            }

            remainingInRound--;
            if (remainingInRound == 0) {
                if (!roundProgressed) {
                    break;
                }
                remainingInRound = ready.size();
                roundProgressed = false;
            }
        }
    }

    private static Map<CraftingDispatchParticipant, String> successorIdentities(
                                                                                List<CraftingDispatchParticipant> participants) {
        Map<CraftingDispatchParticipant, String> successors = new IdentityHashMap<>(participants.size());
        for (int index = 0; index < participants.size(); index++) {
            int successorIndex = (index + 1) % participants.size();
            successors.put(participants.get(index), participants.get(successorIndex).diagnosticIdentity());
        }
        return successors;
    }

    private static void completeParticipants(List<CraftingDispatchParticipant> participants) {
        for (CraftingDispatchParticipant participant : participants) {
            try {
                participant.completeTick();
            } catch (RuntimeException failure) {
                isolate(participant, COMPLETION_FAILURE_SOURCE, failure);
            }
        }
    }

    private static void isolate(CraftingDispatchParticipant participant,
                                String source,
                                RuntimeException failure) {
        Data_Energistics.LOGGER.error(
                "Trinity isolated Grid {} after an unexpected {} failure",
                participant.diagnosticIdentity(),
                source,
                failure);
        try {
            participant.recordUnexpectedFailure(source, failure);
        } catch (RuntimeException isolationFailure) {
            Data_Energistics.LOGGER.error(
                    "Trinity Grid {} failed while entering SAFE mode after {}",
                    participant.diagnosticIdentity(),
                    source,
                    isolationFailure);
        }
    }
}
