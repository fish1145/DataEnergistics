package com.fish_dan_.data_energistics.common.trinity.host;

import com.fish_dan_.data_energistics.gui.ldlib2.host.HostUiKey;

/**
 * Consumable terminal result for the exact client ticket that was pending when an ACK arrived.
 *
 * @param key        hosted business window identity
 * @param generation fresh window generation
 * @param sequence   per-generation action sequence
 * @param status     completed or rejected terminal outcome
 */
public record TrinityHostedActionResult(HostUiKey key,
                                        long generation,
                                        long sequence,
                                        TrinityHostedActionStatus status) {

    /** Validates the same bounded identity used by request payloads. */
    public TrinityHostedActionResult {
        new TrinityHostedActionTicket(key, generation, sequence);
        if (status == null) {
            throw new IllegalArgumentException("Trinity hosted action result status cannot be null");
        }
    }

    /** Returns the exact request ticket echoed by this result. */
    public TrinityHostedActionTicket ticket() {
        return new TrinityHostedActionTicket(this.key, this.generation, this.sequence);
    }
}
