package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server;

/**
 * Describes one bounded server-thread dispatch step without exposing mutable Grid or worker state.
 *
 * @param physicalAttempted whether exactly one real provider call was attempted
 * @param stateChanged      whether non-physical scheduling or crafting state advanced
 * @param hasReadyWork      whether this participant can make more progress in the current tick
 * @param windowExhausted   whether this participant's Grid window forbids another physical attempt
 */
public record CraftingDispatchStepResult(
                                         boolean physicalAttempted,
                                         boolean stateChanged,
                                         boolean hasReadyWork,
                                         boolean windowExhausted) {

    /** Result for a participant that has no current-tick work. */
    public static final CraftingDispatchStepResult IDLE = new CraftingDispatchStepResult(
            false,
            false,
            false,
            false);

    /**
     * @return whether the step made physical or logical progress
     */
    public boolean progressed() {
        return this.physicalAttempted || this.stateChanged;
    }
}
