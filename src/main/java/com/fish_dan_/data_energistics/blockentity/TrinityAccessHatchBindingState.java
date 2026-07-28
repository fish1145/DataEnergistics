package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;

import org.jetbrains.annotations.ApiStatus;

/**
 * Atomic identity and lifecycle state for one Trinity access hatch compartment binding.
 *
 * <p>
 * The hatch keeps this object as its only local binding source so a host and structure name cannot be cleared or
 * assigned independently while a release callback is still running.
 * </p>
 */
@ApiStatus.Internal
final class TrinityAccessHatchBindingState {

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
    private final Phase phase;
    /**
     * Whether the external host removal call is currently on the stack.
     */
    private boolean releaseInProgress;

    private TrinityAccessHatchBindingState(CompartmentHost host, String structureName, Phase phase) {
        this.host = host;
        this.structureName = structureName;
        if (structureName.isBlank()) {
            throw new IllegalArgumentException("Trinity access hatch binding structure name must not be blank");
        }
        this.phase = phase;
    }

    /**
     * Creates a complete active binding identity before external host registration begins.
     */
    static TrinityAccessHatchBindingState active(String structureName, CompartmentHost host) {
        return new TrinityAccessHatchBindingState(host, structureName, Phase.ACTIVE);
    }

    /**
     * Creates the releasing state for this identity while retaining both parts of the identity together.
     */
    TrinityAccessHatchBindingState releasing() {
        return this.phase == Phase.RELEASING ? this : new TrinityAccessHatchBindingState(this.host, this.structureName, Phase.RELEASING);
    }

    /**
     * Returns whether this state identifies the supplied host and structure pair.
     */
    boolean matches(CompartmentHost host, String structureName) {
        return this.host == host && this.structureName.equals(structureName);
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
}
