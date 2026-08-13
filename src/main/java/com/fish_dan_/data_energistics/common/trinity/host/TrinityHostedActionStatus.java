package com.fish_dan_.data_energistics.common.trinity.host;

/** Terminal outcome returned after the server handles one exact hosted business-action ticket. */
public enum TrinityHostedActionStatus {

    /**
     * The validated business entry point completed handling the request.
     *
     * <p>
     * For automatic building this does not promise that blocks were placed; detailed planner failures continue to
     * use the existing player message and server log path.
     * </p>
     */
    COMPLETED(0),
    /** The request was rejected before its business entry point could complete. */
    REJECTED(1),
    /** The action was valid but no matching state remained to return. */
    NO_OP(2),
    /** The host, menu session, catalog, or replay state was no longer current. */
    STALE_STATE(3),
    /** Delivery could not be prepared or completed without risking a partial return. */
    DELIVERY_FAILED(4),
    /** A server-side action or response boundary failed unexpectedly. */
    INTERNAL_ERROR(5),
    /** The request started a bounded server-tick task whose final result will arrive through synchronized state. */
    STARTED(6);

    private final int networkId;

    TrinityHostedActionStatus(int networkId) {
        this.networkId = networkId;
    }

    /** Returns the stable bounded network discriminator. */
    public int networkId() {
        return this.networkId;
    }

    /** Resolves a network discriminator without accepting future values implicitly. */
    public static TrinityHostedActionStatus fromNetworkId(int networkId) {
        for (TrinityHostedActionStatus status : values()) {
            if (status.networkId == networkId) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown Trinity hosted action status: " + networkId);
    }
}
