package com.fish_dan_.data_energistics.common.trinity.host;

import com.fish_dan_.data_energistics.gui.ldlib2.host.HostUiKey;

/**
 * Exact identity of one action issued by a fresh hosted-window generation.
 *
 * @param key        business window identity
 * @param generation accepted OPEN sequence of the still-mounted window
 * @param sequence   positive sequence monotonic within that key and generation
 */
public record TrinityHostedActionTicket(HostUiKey key, long generation, long sequence) {

    /** Largest generation accepted by the bounded wire protocol. */
    public static final long MAX_GENERATION = 1_000_000_000L;
    /** Largest per-generation action sequence accepted by the bounded wire protocol. */
    public static final long MAX_SEQUENCE = 1_000_000_000L;

    /** Rejects identities that cannot be represented by the hosted action codec. */
    public TrinityHostedActionTicket {
        if (key == null) {
            throw new IllegalArgumentException("Trinity hosted action key cannot be null");
        }
        if (generation < 1L || generation > MAX_GENERATION) {
            throw new IllegalArgumentException("Trinity hosted action generation is outside [1, " +
                    MAX_GENERATION + "]: " + generation);
        }
        if (sequence < 1L || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("Trinity hosted action sequence is outside [1, " +
                    MAX_SEQUENCE + "]: " + sequence);
        }
    }
}
