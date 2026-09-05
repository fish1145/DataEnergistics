package com.fish_dan_.data_energistics.api.registry.machine.upload;

/**
 * One reversible, short-lived machine state transition prepared for a single provider-leaf upload attempt.
 *
 * <p>
 * The runtime invokes {@link #apply()} at most once immediately before provider inventory mutation. It then invokes
 * exactly one terminal method: {@link #complete(long)} after a positive real inventory delta, or {@link #rollback()}
 * when the provider accepted nothing. If a third-party inventory mutates and then prevents the runtime from proving
 * its delta, {@link #completeIndeterminate()} is used because rollback could contradict an already inserted pattern.
 * Implementations must not retain caller context beyond that terminal callback.
 * </p>
 */
public interface PreparedPatternUploadChange {

    /**
     * Applies only state that can still be fully restored by {@link #rollback()}.
     *
     * <p>
     * If this method throws, the runtime still invokes {@code rollback()} because the change may have failed after a
     * partial mutation. Implementations must record enough state before their first mutation for that rollback call to
     * be safe.
     * </p>
     */
    void apply();

    /**
     * Finalizes the applied state after the provider inventory has committed.
     *
     * @param committedPatternCount positive number of patterns confirmed by the real inventory delta
     */
    void complete(long committedPatternCount);

    /**
     * Finalizes the safest applied state after provider mutation when the real committed count cannot be proven.
     *
     * <p>
     * The runtime stops this upload without consuming another encoder pattern. Implementations must persist or
     * synchronize the already applied state and must not restore the pre-upload state.
     * </p>
     */
    void completeIndeterminate();

    /**
     * Restores the exact pre-apply state after the provider accepted no pattern or {@link #apply()} failed partway.
     */
    void rollback();
}
