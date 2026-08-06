package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server;

/**
 * Represents one AE Grid in the server-wide Trinity dispatch rotation for a single server tick.
 */
public interface CraftingDispatchParticipant extends CraftingDispatchCompletion {

    /**
     * Skips invalid candidates as needed and performs at most one real provider call.
     *
     * @return immutable progress facts for scheduler admission
     */
    CraftingDispatchStepResult dispatchStep();

    /**
     * Completes per-Grid metrics and Governor observation exactly once after the rotation ends.
     */
    void completeTick();

    /**
     * Isolates an unexpected runtime failure to this Grid and moves its Governor into SAFE mode.
     *
     * @param source  concise failing boundary identity
     * @param failure original unexpected runtime failure
     */
    void recordUnexpectedFailure(String source, RuntimeException failure);
}
