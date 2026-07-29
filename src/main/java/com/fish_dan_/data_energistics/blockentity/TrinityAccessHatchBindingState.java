package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.common.compartment.CompartmentBindingHandle;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockController;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Atomic identity and lifecycle state for one Trinity access hatch compartment binding.
 *
 * <p>
 * The hatch keeps this object as its only local binding source so a host and structure name cannot be cleared or
 * assigned independently while a release callback is still running. The object itself is also the opaque identity
 * returned to lifecycle owners, so an old callback cannot release a newer same-host binding.
 * </p>
 */
@ApiStatus.Internal
final class TrinityAccessHatchBindingState implements CompartmentBindingHandle {

    private static final long NO_VERTICAL_BINDING_EPOCH = -1L;

    /**
     * Lifecycle phase used to distinguish a usable binding from one being released.
     */
    enum Phase {
        ACTIVE,
        RELEASING
    }

    /**
     * Host that owns this binding identity.
     */
    private final CompartmentHost host;
    /**
     * Structure name that owns this binding identity.
     */
    private final String structureName;
    /**
     * Current lifecycle phase for this identity.
     */
    private Phase phase;
    /**
     * Vertical runtime controller that issued this identity, when applicable.
     */
    @Nullable
    private final VerticalMultiBlockController verticalController;
    /**
     * Vertical runtime callback identity, or the explicit non-vertical sentinel.
     */
    private final long verticalBindingEpoch;
    /**
     * Whether the external host removal call is currently on the stack.
     */
    private boolean releaseInProgress;
    /**
     * Whether the old host successfully removed this registration.
     */
    private boolean releaseCompleted;
    /**
     * Whether this releasing identity is withdrawing old publications or committing a queued replacement.
     */
    private boolean releaseCompletionInProgress;
    /**
     * Full replacement identity queued by a re-entrant bind while this identity releases.
     */
    @Nullable
    private TrinityAccessHatchBindingState pendingReplacement;

    private TrinityAccessHatchBindingState(CompartmentHost host,
                                           String structureName,
                                           Phase phase,
                                           @Nullable VerticalMultiBlockController verticalController,
                                           long verticalBindingEpoch) {
        if (host == null) {
            throw new IllegalArgumentException("Trinity access hatch binding host must not be null");
        }
        if (structureName == null || structureName.isBlank()) {
            throw new IllegalArgumentException("Trinity access hatch binding structure name must not be blank");
        }
        if (verticalController == null && verticalBindingEpoch != NO_VERTICAL_BINDING_EPOCH) {
            throw new IllegalArgumentException("Non-vertical Trinity access hatch binding cannot carry a callback epoch");
        }
        if (verticalController != null && verticalBindingEpoch < 0L) {
            throw new IllegalArgumentException("Vertical Trinity access hatch binding epoch must not be negative");
        }
        this.host = host;
        this.structureName = structureName;
        this.phase = phase;
        this.verticalController = verticalController;
        this.verticalBindingEpoch = verticalBindingEpoch;
    }

    /**
     * Creates a complete active non-vertical binding identity before external host registration begins.
     */
    static TrinityAccessHatchBindingState active(String structureName, CompartmentHost host) {
        return new TrinityAccessHatchBindingState(
                host,
                structureName,
                Phase.ACTIVE,
                null,
                NO_VERTICAL_BINDING_EPOCH);
    }

    /**
     * Creates a complete active identity for one specific vertical multiblock formation callback set.
     */
    static TrinityAccessHatchBindingState active(String structureName,
                                                 CompartmentHost host,
                                                 VerticalMultiBlockController verticalController,
                                                 long verticalBindingEpoch) {
        return new TrinityAccessHatchBindingState(
                host,
                structureName,
                Phase.ACTIVE,
                verticalController,
                verticalBindingEpoch);
    }

    /**
     * Marks this exact identity as releasing while retaining the handle captured by lifecycle owners.
     */
    TrinityAccessHatchBindingState releasing() {
        this.phase = Phase.RELEASING;
        return this;
    }

    /**
     * Returns whether this state identifies the supplied host and structure pair.
     */
    boolean matches(CompartmentHost host, String structureName) {
        return this.host == host && this.structureName.equals(structureName);
    }

    /**
     * Returns whether a bind request is the same active identity, including a vertical callback identity when supplied.
     */
    boolean matchesRequestedBinding(CompartmentHost host,
                                    String structureName,
                                    @Nullable VerticalMultiBlockController verticalController,
                                    long verticalBindingEpoch) {
        return matches(host, structureName) && (verticalController == null ||
                this.verticalController == verticalController && this.verticalBindingEpoch == verticalBindingEpoch);
    }

    /**
     * Returns whether a vertical removal callback belongs to this exact formation rather than a later rebind.
     */
    boolean matchesVerticalRemoval(VerticalMultiBlockController controller,
                                   String structureName,
                                   long bindingEpoch) {
        return this.host == controller && this.verticalController == controller &&
                this.structureName.equals(structureName) && this.verticalBindingEpoch == bindingEpoch;
    }

    /**
     * Queues one complete replacement while the old identity releases.
     */
    void queueReplacement(TrinityAccessHatchBindingState replacement) {
        if (!isReleasing()) {
            throw new IllegalStateException("Only a releasing Trinity access hatch binding can queue a replacement");
        }
        if (this.pendingReplacement == null) {
            this.pendingReplacement = replacement;
            return;
        }
        if (this.pendingReplacement.sameIdentity(replacement)) {
            return;
        }
        throw new IllegalStateException("Trinity access hatch binding received conflicting replacements");
    }

    /**
     * Returns the complete replacement identity queued during release, if any.
     */
    @Nullable
    TrinityAccessHatchBindingState pendingReplacement() {
        return this.pendingReplacement;
    }

    /**
     * Returns whether the same host and structure can retry a failed release without overriding a queued replacement.
     */
    boolean requiresBindingRetry(CompartmentHost host, String structureName) {
        return isReleasing() && this.pendingReplacement == null && matches(host, structureName);
    }

    /**
     * Marks the external removal operation as active.
     */
    void beginReleaseAttempt() {
        if (this.releaseInProgress) {
            throw new IllegalStateException("Trinity access hatch binding release is already in progress");
        }
        this.releaseInProgress = true;
    }

    /**
     * Marks the external removal operation as finished while retaining a failed releasing state for retry.
     */
    void finishReleaseAttempt() {
        this.releaseInProgress = false;
    }

    /**
     * Records that the old host has released this exact registration.
     */
    void markReleaseCompleted() {
        if (!isReleasing()) {
            throw new IllegalStateException("Only a releasing Trinity access hatch binding can complete release");
        }
        this.releaseCompleted = true;
    }

    /**
     * Marks withdrawal of the released identity and replacement publication as in progress.
     */
    void beginReleaseCompletion() {
        if (this.releaseCompletionInProgress) {
            throw new IllegalStateException("Trinity access hatch binding release completion is already in progress");
        }
        this.releaseCompletionInProgress = true;
    }

    /**
     * Marks withdrawal of the released identity and replacement publication as finished.
     */
    void finishReleaseCompletion() {
        this.releaseCompletionInProgress = false;
    }

    /**
     * Returns the host that owns this state.
     */
    CompartmentHost host() {
        return this.host;
    }

    /**
     * Returns the structure name that owns this state.
     */
    String structureName() {
        return this.structureName;
    }

    /**
     * Returns this state's lifecycle phase.
     */
    Phase phase() {
        return this.phase;
    }

    /**
     * Returns whether this state can publish normal access-hatch services.
     */
    boolean isActive() {
        return this.phase == Phase.ACTIVE;
    }

    /**
     * Returns whether this state retains identity while its host registration is being released.
     */
    boolean isReleasing() {
        return this.phase == Phase.RELEASING;
    }

    /**
     * Returns whether the external release callback is currently executing.
     */
    boolean isReleaseInProgress() {
        return this.releaseInProgress;
    }

    /**
     * Returns whether the old host has already released this state.
     */
    boolean isReleaseCompleted() {
        return this.releaseCompleted;
    }

    /**
     * Returns whether completion of this release currently owns the transition.
     */
    boolean isReleaseCompletionInProgress() {
        return this.releaseCompletionInProgress;
    }

    private boolean sameIdentity(TrinityAccessHatchBindingState other) {
        return this.host == other.host && this.structureName.equals(other.structureName) &&
                this.verticalController == other.verticalController &&
                this.verticalBindingEpoch == other.verticalBindingEpoch;
    }
}
