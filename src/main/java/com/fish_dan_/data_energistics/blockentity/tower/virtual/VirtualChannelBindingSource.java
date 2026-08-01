package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Identifies how a tower binding was created and therefore which allocation queue owns it.
 */
public enum VirtualChannelBindingSource {

    /** A player-created binding that must be considered before discovered bindings. */
    MANUAL(0),

    /** A scope-discovered binding considered after every manual binding. */
    AUTOMATIC(1);

    private final int allocationPriority;

    VirtualChannelBindingSource(int allocationPriority) {
        this.allocationPriority = allocationPriority;
    }

    /**
     * Returns the explicit queue priority used by the ledger.
     *
     * @return lower value for an earlier allocation queue
     */
    public int allocationPriority() {
        return this.allocationPriority;
    }
}
