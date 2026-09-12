package com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle;

import org.jspecify.annotations.Nullable;

/**
 * Transient state of one entity instance's current life, confined to its server thread. A completed life keeps
 * its recovery lock after the synchronous execution context closes; failed executions release that lock.
 */
public final class OrbitalErasureState {

    private Phase phase = Phase.NONE;
    private @Nullable OrbitalErasureContext context;
    private boolean deathCallActive;
    private boolean deathHookReturned;
    private boolean deathBodyEntered;

    public OrbitalErasureState() {}

    public Phase phase() {
        return this.phase;
    }

    public boolean blocksRecovery() {
        return this.phase == Phase.EXECUTING || this.phase == Phase.DEATH_COMMITTED || this.phase == Phase.DEATH_COMPLETED;
    }

    public @Nullable OrbitalErasureContext context() {
        return this.context;
    }

    void begin(OrbitalErasureContext context) {
        if (this.phase != Phase.NONE || this.context != null) {
            throw new IllegalStateException("An erasure execution already owns this life");
        }
        this.context = context;
        this.phase = Phase.EXECUTING;
    }

    void close(OrbitalErasureContext context) {
        if (this.context != context) {
            throw new IllegalStateException("Erasure context ownership changed during execution");
        }
        this.context = null;
    }

    /** Rejects reentrant death of this same life while leaving other entities' calls independent. */
    public boolean enterDeathCall() {
        if (this.context == null || this.deathCallActive || this.phase != Phase.EXECUTING) {
            return false;
        }
        this.deathCallActive = true;
        return true;
    }

    public void exitDeathCall() {
        this.deathCallActive = false;
    }

    /** The first vanilla ENTITY_DIE game event is already a death side effect, before the NeoForge hook. */
    public void beginDeathSideEffects() {
        requireDeathCall();
        this.phase = Phase.DEATH_COMMITTED;
    }

    public void deathHookReturned() {
        requireDeathCall();
        if (this.deathHookReturned) {
            throw new IllegalStateException("The death event was invoked twice for one erasure execution");
        }
        this.deathHookReturned = true;
    }

    /** Observed inside vanilla's non-cancelled death branch, rather than inferred from zero health. */
    public void enterDeathBody() {
        requireDeathCall();
        if (!this.deathHookReturned) {
            throw new IllegalStateException("Death body entered without the original death event");
        }
        this.deathBodyEntered = true;
        this.phase = Phase.DEATH_COMMITTED;
    }

    /** Called only at the original death body's tail, after its final death-location update. */
    public void completeDeathBody() {
        requireDeathCall();
        if (!this.deathBodyEntered) {
            throw new IllegalStateException("Death completed without entering its settlement body");
        }
        this.phase = Phase.DEATH_COMPLETED;
    }

    /** An unknown failure releases equipment protection; it never becomes an indefinite recovery veto. */
    public void fail() {
        this.phase = Phase.FAILED;
    }

    private void requireDeathCall() {
        if (this.context == null || !this.deathCallActive) {
            throw new IllegalStateException("Death observation outside the owning erasure call");
        }
    }

    public enum Phase {
        NONE,
        EXECUTING,
        DEATH_COMMITTED,
        DEATH_COMPLETED,
        FAILED
    }
}
