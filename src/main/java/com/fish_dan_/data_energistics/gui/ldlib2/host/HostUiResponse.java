package com.fish_dan_.data_energistics.gui.ldlib2.host;

/**
 * Server response that echoes the exact operation identity needed to reject stale acknowledgements.
 */
public record HostUiResponse(HostUiRequest request, HostUiResponseStatus status) {

    /**
     * Validates the echoed request and decision before client-side matching.
     */
    public HostUiResponse {
        if (request == null) {
            throw new IllegalArgumentException("Host UI response request must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Host UI response status must not be null");
        }
    }

    /**
     * Creates the success response emitted only after the server changed its tree.
     *
     * @param request applied request
     * @return accepted response
     */
    public static HostUiResponse accepted(HostUiRequest request) {
        return new HostUiResponse(request, HostUiResponseStatus.ACCEPTED);
    }

    /**
     * Creates a response that leaves the client tree unchanged.
     *
     * @param request rejected request
     * @param status  concrete rejection reason
     * @return rejected response
     */
    public static HostUiResponse rejected(HostUiRequest request, HostUiResponseStatus status) {
        if (status == HostUiResponseStatus.ACCEPTED) {
            throw new IllegalArgumentException("Rejected host UI response cannot use ACCEPTED status");
        }
        return new HostUiResponse(request, status);
    }

    /**
     * Reports whether the client may mirror the echoed request.
     *
     * @return true only for an accepted server mutation
     */
    public boolean accepted() {
        return this.status == HostUiResponseStatus.ACCEPTED;
    }
}
