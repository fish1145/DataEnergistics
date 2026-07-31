package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch;

/**
 * Explicit outcome of one provider preparation or physical crafting submission.
 */
public enum CraftingDispatchStatus {
    /**
     * The provider took ownership of the admitted batch.
     */
    ACCEPTED,
    /**
     * AE2 Blocking Mode rejected the selected target for the current tick.
     */
    BLOCKED,
    /**
     * The provider's original crafting lock prevents another submission.
     */
    LOCKED,
    /**
     * The provider is still processing previously accepted inputs.
     */
    BUSY,
    /**
     * The provider or its grid node is offline.
     */
    OFFLINE,
    /**
     * The selected target cannot currently accept one complete craft.
     */
    NO_CAPACITY,
    /**
     * A versioned plan no longer matches live state.
     */
    STALE,
    /**
     * The provider rejected the attempt without establishing a reusable blocking condition.
     */
    REJECTED,
    /**
     * Provider code failed before taking ownership of the admitted inputs.
     */
    FAILED_BEFORE_OWNERSHIP,
    /**
     * Provider code failed after taking ownership, so CPU accounting must commit the batch.
     */
    FAILED_AFTER_OWNERSHIP
}
