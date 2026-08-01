package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Reports the current availability of one node in a virtual channel snapshot.
 */
public enum VirtualChannelNodeState {

    /** The node owns one charged virtual channel. */
    LEASED,

    /** The node requires a channel but the finite budget is exhausted. */
    WAITING_CHANNEL,

    /** The node is available and explicitly does not consume a channel. */
    AVAILABLE_WITHOUT_CHANNEL,

    /** The owning device binding is disabled. */
    DISABLED
}
