package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server;

import com.fish_dan_.data_energistics.Data_Energistics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the server-level round-robin boundary that interleaves bounded provider passes from independent AE Grids.
 * <p>
 * Server-thread-confined scheduler that switches Grid after every bounded participant pass.
 */
public final class TrinityServerDispatchScheduler {

    /**
     * Creates an empty scheduler with no retained server or Grid ownership.
     *
     * @return independent server dispatch scheduler
     */
    public static TrinityServerDispatchScheduler create() {
        return new TrinityServerDispatchScheduler();
    }

    private static final String STEP_FAILURE_SOURCE = "server dispatch step";
    private static final String COMPLETION_FAILURE_SOURCE = "server dispatch completion";

    private final List<CraftingDispatchParticipant> registeredParticipants = new ArrayList<>();
    private final List<CraftingDispatchCompletion> registeredCompletions = new ArrayList<>();
    private String nextParticipantIdentity;
    private boolean tickOpen;

    /**
     * Opens registration for one server tick and rejects an unfinished previous tick.
     */
    public void beginTick() {
        if (this.tickOpen) {
            throw new IllegalStateException("Previous Trinity server dispatch tick was not completed");
        }
        this.registeredParticipants.clear();
        this.registeredCompletions.clear();
        this.tickOpen = true;
    }

    /**
     * Registers one prepared Grid participant for the current tick.
     *
     * @param participant prepared Grid dispatch boundary
     */
    public void register(CraftingDispatchParticipant participant) {
        if (!this.tickOpen) {
            throw new IllegalStateException("Trinity server dispatch registration is closed");
        }
        validateCompletion(participant);
        this.registeredParticipants.add(participant);
        this.registeredCompletions.add(participant);
    }

    /**
     * Registers a Grid completion boundary without adding it to physical-call rotation.
     *
     * @param completion prepared completion-only Grid boundary
     */
    public void registerCompletion(CraftingDispatchCompletion completion) {
        if (!this.tickOpen) {
            throw new IllegalStateException("Trinity server dispatch registration is closed");
        }
        validateCompletion(completion);
        this.registeredCompletions.add(completion);
    }

    /**
     * Runs every registered Grid in physical-call round-robin order and completes their metrics.
     */
    public void dispatchTick() {
        if (!this.tickOpen) {
            throw new IllegalStateException("Trinity server dispatch tick is not open");
        }
        this.tickOpen = false;
        if (this.registeredParticipants.isEmpty() && this.registeredCompletions.isEmpty()) {
            return;
        }
        List<CraftingDispatchParticipant> participants = List.copyOf(this.registeredParticipants);
        List<CraftingDispatchCompletion> completions = List.copyOf(this.registeredCompletions);
        this.registeredParticipants.clear();
        this.registeredCompletions.clear();
        try {
            if (!participants.isEmpty()) {
                dispatchParticipants(participantsFromPersistentCursor(participants));
            }
        } finally {
            completeCompletions(completions);
        }
    }

    /**
     * Clears an unfinished tick during logical-server shutdown.
     */
    public void reset() {
        this.registeredParticipants.clear();
        this.registeredCompletions.clear();
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

    private static void completeCompletions(List<CraftingDispatchCompletion> completions) {
        for (CraftingDispatchCompletion completion : completions) {
            try {
                completion.completeTick();
            } catch (RuntimeException failure) {
                isolate(completion, COMPLETION_FAILURE_SOURCE, failure);
            }
        }
    }

    private static void validateCompletion(CraftingDispatchCompletion completion) {
        if (completion == null) {
            throw new IllegalArgumentException("Crafting dispatch completion is required");
        }
        String diagnosticIdentity = completion.diagnosticIdentity();
        if (diagnosticIdentity == null || diagnosticIdentity.isBlank()) {
            throw new IllegalArgumentException("Crafting dispatch completion identity is required");
        }
    }

    private static void isolate(CraftingDispatchCompletion completion,
                                String source,
                                RuntimeException failure) {
        Data_Energistics.LOGGER.error(
                "Trinity isolated Grid {} after an unexpected {} failure",
                completion.diagnosticIdentity(),
                source,
                failure);
        try {
            completion.recordUnexpectedFailure(source, failure);
        } catch (RuntimeException isolationFailure) {
            Data_Energistics.LOGGER.error(
                    "Trinity Grid {} failed while entering SAFE mode after {}",
                    completion.diagnosticIdentity(),
                    source,
                    isolationFailure);
        }
    }
}
