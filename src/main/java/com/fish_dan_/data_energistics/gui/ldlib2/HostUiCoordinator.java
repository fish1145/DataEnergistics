package com.fish_dan_.data_energistics.gui.ldlib2;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Coordinates authoritative dynamic-window membership between the client and server copies of one host UI.
 *
 * <p>
 * A client coordinator emits one pending request without changing its element tree. The server coordinator validates
 * the sequence and membership, changes its own tree first, and returns a response. Only the matching accepted response
 * lets the client apply the same operation. View-only position and z-order changes remain local to
 * {@link HostUiExtension}.
 * </p>
 */
public interface HostUiCoordinator {

    /**
     * Creates and attaches the client endpoint for one host lifetime.
     *
     * @param hostUi         host membership to mirror after accepted responses
     * @param requestSink    transport that sends requests to the current server menu
     * @param terminalAction closes the owning menu after a local terminal failure
     * @return attached client coordinator
     */
    static HostUiCoordinator createClient(HostUiExtension hostUi,
                                          Consumer<HostUiRequest> requestSink,
                                          Runnable terminalAction) {
        return HostUiCoordinatorImpl.createClient(hostUi, requestSink, terminalAction);
    }

    /**
     * Creates and attaches the server endpoint for one host lifetime.
     *
     * @param hostUi         authoritative server host membership
     * @param terminalAction closes the owning menu after a local terminal failure
     * @return attached server coordinator
     */
    static HostUiCoordinator createServer(HostUiExtension hostUi, Runnable terminalAction) {
        return HostUiCoordinatorImpl.createServer(hostUi, terminalAction);
    }

    /**
     * Returns the exact host extension whose dynamic membership this endpoint owns.
     *
     * @return attached host extension
     */
    HostUiExtension hostUi();

    /**
     * Requests that a closed provider be opened by the server before the client mirrors it.
     *
     * @param key registered provider identity
     * @return whether a new request was emitted
     */
    boolean requestOpen(HostUiKey key);

    /**
     * Requests that an open provider be closed by the server before the client mirrors it.
     *
     * @param key registered provider identity
     * @return whether a new request was emitted
     */
    boolean requestClose(HostUiKey key);

    /**
     * Chooses an explicit open or close request from the client's acknowledged membership.
     *
     * @param key registered provider identity
     * @return whether a new request was emitted
     */
    boolean requestToggle(HostUiKey key);

    /**
     * Requests closure of the client's current topmost window without removing it optimistically.
     *
     * @return whether a close request was emitted
     */
    boolean requestCloseTopmost();

    /**
     * Consumes Escape while any lifecycle request or hosted window exists, emitting at most one close request.
     *
     * @return whether the containing Screen must keep the menu open
     */
    boolean handleEscape();

    /**
     * Validates and applies one request on the authoritative server endpoint.
     *
     * @param request       ordered request from the current client menu
     * @param hostAvailable whether the current player, menu, and business host remain valid
     * @return response to send before terminating an invalid coordinator, when applicable
     */
    HostUiResponse handleRequest(HostUiRequest request, boolean hostAvailable);

    /**
     * Applies one exact accepted server response on the client endpoint.
     *
     * @param response response received for the current client menu
     * @return whether the client membership changed
     */
    boolean handleResponse(HostUiResponse response);

    /**
     * Returns the request that currently blocks further client operations.
     *
     * @return pending request, or null when the client may emit another request
     */
    @Nullable
    HostUiRequest pendingRequest();

    /**
     * Returns the next sequence expected or emitted by this endpoint.
     *
     * @return positive monotonic sequence
     */
    long nextSequence();

    /**
     * Reports an unrecoverable topology or transport failure that requires the current menu to close.
     *
     * @return terminal endpoint state
     */
    boolean isTerminal();
}
