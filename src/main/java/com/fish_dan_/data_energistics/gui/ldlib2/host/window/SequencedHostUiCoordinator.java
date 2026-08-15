package com.fish_dan_.data_energistics.gui.ldlib2.host.window;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiOperation;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiRequest;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiResponse;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiResponseStatus;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Stateful client or server endpoint for the ordered host UI lifecycle protocol.
 */
final class SequencedHostUiCoordinator implements HostUiCoordinator {

    private static final long INITIAL_SEQUENCE = 1L;

    private final Role role;
    private final HostUiExtension hostUi;
    private final OverlayHostUiExtension membership;
    private final List<HostUiKey> registeredKeys;
    @Nullable
    private final Consumer<HostUiRequest> requestSink;
    private final Runnable terminalAction;
    @Nullable
    private HostUiRequest pendingRequest;
    private long nextSequence = INITIAL_SEQUENCE;
    private boolean terminal;
    private boolean terminalActionInvoked;

    private SequencedHostUiCoordinator(Role role,
                                       HostUiExtension hostUi,
                                       OverlayHostUiExtension membership,
                                       @Nullable Consumer<HostUiRequest> requestSink,
                                       Runnable terminalAction) {
        this.role = role;
        this.hostUi = hostUi;
        this.membership = membership;
        this.registeredKeys = membership.registeredKeys();
        this.requestSink = requestSink;
        this.terminalAction = terminalAction;
    }

    /**
     * Creates the client role and seals its provider order before any dynamic tree can open.
     */
    static HostUiCoordinator createClient(HostUiExtension hostUi,
                                          Consumer<HostUiRequest> requestSink,
                                          Runnable terminalAction) {
        OverlayHostUiExtension membership = validateHostUi(hostUi);
        if (requestSink == null) {
            throw violation("client request sink must not be null");
        }
        validateTerminalAction(terminalAction);
        SequencedHostUiCoordinator coordinator = new SequencedHostUiCoordinator(Role.CLIENT, hostUi, membership, requestSink, terminalAction);
        membership.attachCoordinator(coordinator);
        return coordinator;
    }

    /**
     * Creates the server role and seals its provider order before any dynamic tree can open.
     */
    static HostUiCoordinator createServer(HostUiExtension hostUi, Runnable terminalAction) {
        OverlayHostUiExtension membership = validateHostUi(hostUi);
        validateTerminalAction(terminalAction);
        SequencedHostUiCoordinator coordinator = new SequencedHostUiCoordinator(Role.SERVER, hostUi, membership, null, terminalAction);
        membership.attachCoordinator(coordinator);
        return coordinator;
    }

    @Override
    public HostUiExtension hostUi() {
        return this.hostUi;
    }

    @Override
    public boolean requestOpen(HostUiKey key) {
        ensureClient();
        if (!canEmitRequest()) {
            return false;
        }
        validateRegisteredKey(key);
        if (this.membership.isOpen(key)) {
            this.membership.bringToFront(key);
            return false;
        }
        return emitRequest(HostUiOperation.OPEN, key);
    }

    @Override
    public boolean requestClose(HostUiKey key) {
        ensureClient();
        if (!canEmitRequest()) {
            return false;
        }
        validateRegisteredKey(key);
        if (!this.membership.isOpen(key)) {
            return false;
        }
        return emitRequest(HostUiOperation.CLOSE, key);
    }

    @Override
    public boolean requestToggle(HostUiKey key) {
        ensureClient();
        if (!canEmitRequest()) {
            return false;
        }
        validateRegisteredKey(key);
        return this.membership.isOpen(key) ? emitRequest(HostUiOperation.CLOSE, key) :
                emitRequest(HostUiOperation.OPEN, key);
    }

    @Override
    public boolean requestCloseTopmost() {
        ensureClient();
        if (!canEmitRequest()) {
            return false;
        }
        List<HostUiKey> openKeys = this.membership.openKeys();
        if (openKeys.isEmpty()) {
            return false;
        }
        return emitRequest(HostUiOperation.CLOSE, openKeys.getLast());
    }

    @Override
    public boolean handleEscape() {
        ensureClient();
        if (isTerminal()) {
            return false;
        }
        if (this.pendingRequest != null) {
            return true;
        }
        List<HostUiKey> openKeys = this.membership.openKeys();
        if (openKeys.isEmpty()) {
            return false;
        }
        return emitRequest(HostUiOperation.CLOSE, openKeys.getLast()) || isTerminal();
    }

    @Override
    public HostUiResponse handleRequest(HostUiRequest request, boolean hostAvailable) {
        ensureServer();
        if (request == null) {
            throw violation("server request must not be null");
        }
        if (isTerminal()) {
            return HostUiResponse.rejected(request, HostUiResponseStatus.HOST_UNAVAILABLE);
        }
        if (request.sequence() < this.nextSequence) {
            return rejectTerminal(request, HostUiResponseStatus.STALE_SEQUENCE);
        }
        if (request.sequence() > this.nextSequence) {
            return rejectTerminal(request, HostUiResponseStatus.OUT_OF_ORDER_SEQUENCE);
        }
        if (this.nextSequence == Long.MAX_VALUE) {
            return rejectTerminal(request, HostUiResponseStatus.APPLY_FAILED);
        }
        if (!hostAvailable || this.membership.isDisposed()) {
            return rejectTerminal(request, HostUiResponseStatus.HOST_UNAVAILABLE);
        }
        if (!this.registeredKeys.contains(request.key())) {
            return rejectTerminal(request, HostUiResponseStatus.UNKNOWN_KEY);
        }
        boolean currentlyOpen = this.membership.isOpen(request.key());
        if (request.operation() == HostUiOperation.OPEN ? currentlyOpen : !currentlyOpen) {
            return rejectTerminal(request, HostUiResponseStatus.MEMBERSHIP_MISMATCH);
        }

        try {
            if (!apply(request)) {
                throw violation("server operation did not change membership for " + request.key().id());
            }
            advanceSequence();
            return HostUiResponse.accepted(request);
        } catch (RuntimeException | Error failure) {
            enterTerminal(
                    false,
                    "Failed to apply authoritative LDLib2 host UI operation " + request.operation() + " for " +
                            request.key().id() + " at sequence " + request.sequence(),
                    failure);
            return HostUiResponse.rejected(request, HostUiResponseStatus.APPLY_FAILED);
        }
    }

    @Override
    public boolean handleResponse(HostUiResponse response) {
        ensureClient();
        if (response == null) {
            throw violation("client response must not be null");
        }
        if (isTerminal()) {
            return false;
        }
        if (this.pendingRequest == null || !this.pendingRequest.equals(response.request())) {
            Data_Energistics.LOGGER.warn(
                    "Ignored stale or unrelated LDLib2 host UI response {} for {} at sequence {}",
                    response.status(),
                    response.request().key().id(),
                    response.request().sequence());
            return false;
        }

        HostUiRequest request = this.pendingRequest;
        if (!response.accepted()) {
            clearPendingRequest();
            enterTerminal(
                    false,
                    "Server rejected LDLib2 host UI operation " + request.operation() + " for " + request.key().id() +
                            " at sequence " + request.sequence() + ": " + response.status(),
                    null);
            return false;
        }

        boolean currentlyOpen = this.membership.isOpen(request.key());
        if (request.operation() == HostUiOperation.OPEN ? currentlyOpen : !currentlyOpen) {
            clearPendingRequest();
            enterTerminal(
                    true,
                    "Client LDLib2 host UI membership diverged before applying " + request.operation() + " for " +
                            request.key().id() + " at sequence " + request.sequence(),
                    null);
            return false;
        }

        try {
            if (!apply(request)) {
                throw violation("client operation did not change membership for " + request.key().id());
            }
            advanceSequence();
            clearPendingRequest();
            return true;
        } catch (RuntimeException | Error failure) {
            clearPendingRequest();
            enterTerminal(
                    true,
                    "Failed to mirror LDLib2 host UI operation " + request.operation() + " for " + request.key().id() +
                            " at sequence " + request.sequence(),
                    failure);
            return false;
        }
    }

    @Override
    public @Nullable HostUiRequest pendingRequest() {
        return this.pendingRequest;
    }

    @Override
    public long nextSequence() {
        return this.nextSequence;
    }

    @Override
    public boolean isTerminal() {
        return this.terminal || this.membership.isDisposed();
    }

    /**
     * Makes an uncoordinated host mutation terminal and closes its owning menu exactly once.
     */
    void hostBecameTerminal(String reason, @Nullable Throwable failure) {
        enterTerminal(true, reason, failure);
    }

    /**
     * Sends one request while retaining its sequence as uncommitted until the matching accepted response.
     */
    private boolean emitRequest(HostUiOperation operation, HostUiKey key) {
        if (this.nextSequence == Long.MAX_VALUE) {
            enterTerminal(true, "LDLib2 host UI client sequence space is exhausted", null);
            return false;
        }
        HostUiRequest request = new HostUiRequest(operation, key, this.nextSequence);
        this.pendingRequest = request;
        this.membership.coordinatorStateChanged();
        try {
            requestSink().accept(request);
            return true;
        } catch (RuntimeException | Error failure) {
            clearPendingRequest();
            enterTerminal(
                    true,
                    "Failed to send LDLib2 host UI operation " + operation + " for " + key.id() + " at sequence " +
                            request.sequence(),
                    failure);
            return false;
        }
    }

    /**
     * Applies one already validated membership change to the package-private authoritative target.
     */
    private boolean apply(HostUiRequest request) {
        return switch (request.operation()) {
            case OPEN -> this.membership.openFresh(request.key(), request.sequence());
            case CLOSE -> this.membership.closeAuthoritatively(request.key());
        };
    }

    /**
     * Rejects a protocol or topology violation without committing its sequence or mutating membership.
     */
    private HostUiResponse rejectTerminal(HostUiRequest request, HostUiResponseStatus status) {
        enterTerminal(
                false,
                "Rejected LDLib2 host UI operation " + request.operation() + " for " + request.key().id() +
                        " at sequence " + request.sequence() + ": " + status + " (expected sequence " +
                        this.nextSequence + ")",
                null);
        return HostUiResponse.rejected(request, status);
    }

    /**
     * Commits one successful membership mutation without allowing signed overflow.
     */
    private void advanceSequence() {
        this.nextSequence = Math.incrementExact(this.nextSequence);
    }

    /**
     * Clears the global pending guard only after a rejection or completed client mutation.
     */
    private void clearPendingRequest() {
        this.pendingRequest = null;
        this.membership.coordinatorStateChanged();
    }

    /**
     * Allows UI input to become a request only while the client endpoint remains synchronized.
     */
    private boolean canEmitRequest() {
        return !isTerminal() && this.pendingRequest == null;
    }

    /**
     * Rejects caller bugs before an unknown key can enter the client transport.
     */
    private void validateRegisteredKey(HostUiKey key) {
        if (key == null) {
            throw violation("requested key must not be null");
        }
        if (!this.registeredKeys.contains(key)) {
            throw violation("requested key is not registered: " + key.id());
        }
    }

    /**
     * Returns the role-specific client transport after construction has proven it exists.
     */
    private Consumer<HostUiRequest> requestSink() {
        if (this.requestSink == null) {
            throw violation("client request sink is missing");
        }
        return this.requestSink;
    }

    /**
     * Marks this endpoint unusable, logs context, and optionally closes its owner exactly once.
     */
    private void enterTerminal(boolean notifyOwner, String reason, @Nullable Throwable failure) {
        if (!this.terminal) {
            this.terminal = true;
            if (failure == null) {
                Data_Energistics.LOGGER.error(reason);
            } else {
                Data_Energistics.LOGGER.error(reason, failure);
            }
            this.membership.coordinatorStateChanged();
        }
        if (notifyOwner && !this.terminalActionInvoked) {
            this.terminalActionInvoked = true;
            try {
                this.terminalAction.run();
            } catch (RuntimeException | Error terminalFailure) {
                Data_Energistics.LOGGER.error("Failed to close a terminal LDLib2 host UI owner", terminalFailure);
            }
        }
    }

    /**
     * Enforces endpoint-specific method use while keeping one holder interface on both menu sides.
     */
    private void ensureClient() {
        if (this.role != Role.CLIENT) {
            throw violation("client lifecycle method invoked on the server coordinator");
        }
    }

    /**
     * Enforces endpoint-specific method use while keeping one holder interface on both menu sides.
     */
    private void ensureServer() {
        if (this.role != Role.SERVER) {
            throw violation("server lifecycle method invoked on the client coordinator");
        }
    }

    /**
     * Validates and narrows the sole production HostUiExtension implementation.
     */
    private static OverlayHostUiExtension validateHostUi(HostUiExtension hostUi) {
        if (!(hostUi instanceof OverlayHostUiExtension membership)) {
            throw violation("coordinator requires the host extension created by HostUiExtension.create");
        }
        if (membership.isDisposed()) {
            throw violation("host extension is already disposed");
        }
        return membership;
    }

    /**
     * Validates the owner callback needed for failures outside payload handling.
     */
    private static void validateTerminalAction(Runnable terminalAction) {
        if (terminalAction == null) {
            throw violation("terminal action must not be null");
        }
    }

    /**
     * Logs each coordinator invariant failure before returning its fail-fast exception.
     */
    private static IllegalStateException violation(String message) {
        Data_Energistics.LOGGER.error("LDLib2 host UI coordinator invariant failed: {}", message);
        return new IllegalStateException(message);
    }

    /**
     * Construction-side role for one endpoint of the lifecycle protocol.
     */
    private enum Role {
        CLIENT,
        SERVER
    }
}
