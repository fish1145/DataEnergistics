package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Explains why an ownership candidate is selected or waiting.
 */
public enum VirtualGridCandidateState {

    /** This candidate is the sole current owner of its target grid. */
    OWNER,

    /** An earlier eligible FIFO candidate already owns the target grid. */
    WAITING_OWNER,

    /** The candidate tower is temporarily unavailable. */
    TOWER_UNAVAILABLE,

    /** The target-specific candidate has been disabled. */
    DISABLED,

    /** Selecting the candidate would introduce an ownership cycle. */
    BLOCKED_CYCLE
}
