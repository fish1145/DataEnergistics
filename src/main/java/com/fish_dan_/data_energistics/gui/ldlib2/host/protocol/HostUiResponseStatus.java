package com.fish_dan_.data_energistics.gui.ldlib2.host.protocol;

/**
 * Server decision for one exact hosted-window lifecycle request.
 */
public enum HostUiResponseStatus {

    /**
     * The server changed its tree and the client may now mirror the operation.
     */
    ACCEPTED(0),

    /**
     * The current menu host is absent, invalid, or already disposed.
     */
    HOST_UNAVAILABLE(1),

    /**
     * The request addressed a provider that the current host did not register.
     */
    UNKNOWN_KEY(2),

    /**
     * The requested open or close did not match the server's current membership.
     */
    MEMBERSHIP_MISMATCH(3),

    /**
     * The request sequence was lower than the next server sequence.
     */
    STALE_SEQUENCE(4),

    /**
     * The request skipped one or more server sequences.
     */
    OUT_OF_ORDER_SEQUENCE(5),

    /**
     * The authoritative LDLib2 tree mutation failed and the menu must terminate.
     */
    APPLY_FAILED(6);

    private final int networkId;

    HostUiResponseStatus(int networkId) {
        this.networkId = networkId;
    }

    /**
     * Returns the stable wire id used by the lifecycle payload codec.
     *
     * @return non-negative status id
     */
    public int networkId() {
        return this.networkId;
    }

    /**
     * Resolves a validated response status received from the network.
     *
     * @param networkId encoded status id
     * @return matching status
     */
    public static HostUiResponseStatus fromNetworkId(int networkId) {
        return switch (networkId) {
            case 0 -> ACCEPTED;
            case 1 -> HOST_UNAVAILABLE;
            case 2 -> UNKNOWN_KEY;
            case 3 -> MEMBERSHIP_MISMATCH;
            case 4 -> STALE_SEQUENCE;
            case 5 -> OUT_OF_ORDER_SEQUENCE;
            case 6 -> APPLY_FAILED;
            default -> throw new IllegalArgumentException("Unknown host UI response status id: " + networkId);
        };
    }
}
