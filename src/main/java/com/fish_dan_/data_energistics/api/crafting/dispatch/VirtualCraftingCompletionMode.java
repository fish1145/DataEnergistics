package com.fish_dan_.data_energistics.api.crafting.dispatch;

/**
 * Defines how one adapter-claimed pattern output is accounted for after its provider accepts the craft.
 */
public enum VirtualCraftingCompletionMode {

    /**
     * Completes the adapter-resolved target as an ordinary virtual output.
     */
    DELIVER_TARGET,

    /**
     * Completes the declared output counters without creating or delivering any item.
     */
    COMPLETE_WITHOUT_OUTPUT
}
