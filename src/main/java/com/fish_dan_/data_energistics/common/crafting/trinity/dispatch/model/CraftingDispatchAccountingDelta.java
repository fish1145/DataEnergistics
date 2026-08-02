package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model;

/**
 * One-shot, prevalidated accounting transition paired with a synchronous provider submission.
 *
 * <p>
 * The delta is created and consumed on the server thread. It must never enter an asynchronous proposal or persisted
 * state. The ownership transition is recorded before its callback runs, so a callback failure can never make the
 * dispatcher release resources that the provider already owns.
 * </p>
 */
public final class CraftingDispatchAccountingDelta {

    private final long logicalCrafts;
    private final Runnable applyAfterOwnership;
    private final Runnable releaseBeforeOwnership;
    private State state = State.READY;

    private CraftingDispatchAccountingDelta(
                                            long logicalCrafts,
                                            Runnable applyAfterOwnership,
                                            Runnable releaseBeforeOwnership) {
        if (logicalCrafts <= 0L) {
            throw new IllegalArgumentException("Crafting dispatch accounting amount must be positive");
        }
        if (applyAfterOwnership == null || releaseBeforeOwnership == null) {
            throw new IllegalArgumentException("Crafting dispatch accounting callbacks must not be null");
        }
        this.logicalCrafts = logicalCrafts;
        this.applyAfterOwnership = applyAfterOwnership;
        this.releaseBeforeOwnership = releaseBeforeOwnership;
    }

    /**
     * Creates the one accounting transition prepared before provider ownership changes.
     *
     * @param logicalCrafts          exact admitted logical craft count
     * @param applyAfterOwnership    non-throwing resource and job-accounting application
     * @param releaseBeforeOwnership resource release used only when ownership was not transferred
     * @return one ready delta
     */
    public static CraftingDispatchAccountingDelta create(
                                                         long logicalCrafts,
                                                         Runnable applyAfterOwnership,
                                                         Runnable releaseBeforeOwnership) {
        return new CraftingDispatchAccountingDelta(
                logicalCrafts,
                applyAfterOwnership,
                releaseBeforeOwnership);
    }

    /**
     * Returns the exact count validated into this delta.
     *
     * @return positive logical craft count
     */
    public long logicalCrafts() {
        return this.logicalCrafts;
    }

    /**
     * Irreversibly applies resource ownership and job accounting after the provider owns the inputs.
     */
    public void applyAfterOwnership() {
        requireReady();
        this.state = State.APPLIED;
        this.applyAfterOwnership.run();
    }

    /**
     * Releases resources reserved for an attempt that did not transfer provider ownership.
     */
    public void releaseBeforeOwnership() {
        requireReady();
        this.state = State.RELEASED;
        this.releaseBeforeOwnership.run();
    }

    private void requireReady() {
        if (this.state != State.READY) {
            throw new IllegalStateException("Crafting dispatch accounting delta was already settled as " + this.state);
        }
    }

    private enum State {
        READY,
        APPLIED,
        RELEASED
    }
}
