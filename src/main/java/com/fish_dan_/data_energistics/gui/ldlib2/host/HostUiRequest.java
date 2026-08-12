package com.fish_dan_.data_energistics.gui.ldlib2.host;

/**
 * Ordered client request for one authoritative hosted-window membership change.
 */
public record HostUiRequest(HostUiOperation operation, HostUiKey key, long sequence) {

    /**
     * Validates all fields before the request can enter a coordinator or network payload.
     */
    public HostUiRequest {
        if (operation == null) {
            throw new IllegalArgumentException("Host UI operation must not be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Host UI key must not be null");
        }
        if (sequence <= 0L) {
            throw new IllegalArgumentException("Host UI sequence must be positive: " + sequence);
        }
    }
}
