package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchExhaustion;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongSupplier;

/**
 * Bounds physical crafting submissions for one AE grid tick while sharing provider state across Trinity runtimes.
 *
 * <p>
 * Accounting is based on provider and pattern object identity. A {@link SubmissionScope} measures the complete
 * server-thread provider path, while each successful {@link SubmissionScope#tryAcquire(CraftingDispatchTarget)} call
 * records one real physical attempt regardless of its result. Explicit result facts drive provider, pattern and target
 * scoped exclusions that expire with this window.
 * </p>
 */
public interface CraftingDispatchWindow {

    /**
     * Creates an empty dispatch window for one AE grid tick.
     *
     * @return independent dispatch window
     */
    static CraftingDispatchWindow create() {
        return new CraftingDispatchWindowImpl();
    }

    /**
     * Creates an empty dispatch window with an explicit immutable hard-limit snapshot.
     *
     * @param limits physical call and server submission time limits
     * @return independent dispatch window
     */
    static CraftingDispatchWindow create(CraftingDispatchLimits limits) {
        return new CraftingDispatchWindowImpl(limits);
    }

    /**
     * Creates a dispatch window with an explicit monotonic clock for deterministic budget verification.
     *
     * @param limits    physical call and server submission time limits
     * @param nanoClock monotonic nanosecond source
     * @return independent dispatch window
     */
    static CraftingDispatchWindow create(CraftingDispatchLimits limits, LongSupplier nanoClock) {
        return new CraftingDispatchWindowImpl(limits, nanoClock);
    }

    /**
     * Checks whether one provider-pattern pair is available and still has physical submission quota.
     *
     * @param provider provider instance to inspect
     * @param pattern  pattern about to be prepared
     * @return whether a caller may prepare another submission for this provider
     */
    boolean canAttempt(ICraftingProvider provider, IPatternDetails pattern);

    /**
     * Checks whether one exact provider-pattern-target route remains available in this window.
     *
     * <p>
     * During an active submission scope this method deliberately ignores that scope's still-running elapsed time so a
     * provider can filter targets consistently. The scope must still acquire the final physical call immediately
     * before commit.
     * </p>
     *
     * @param provider provider instance to inspect
     * @param pattern  pattern about to be prepared
     * @param target   stable provider-local target
     * @return whether the target remains eligible and physical quota remains
     */
    boolean canAttempt(
                       ICraftingProvider provider,
                       IPatternDetails pattern,
                       CraftingDispatchTarget target);

    /**
     * Starts measuring one provider's complete server-thread preparation and submission path.
     *
     * <p>
     * Callers must first verify {@link #canAttempt(ICraftingProvider, IPatternDetails)} and must close the returned
     * scope. Dispatch windows are server-thread confined and reject nested submission scopes.
     * </p>
     *
     * @param provider provider instance about to be prepared
     * @param pattern  exact pattern being prepared
     * @return closeable scope that owns physical attempt acquisition for this provider path
     */
    SubmissionScope beginSubmission(ICraftingProvider provider, IPatternDetails pattern);

    /**
     * Records one explicit preparation or submission result and updates only the status-defined negative-cache scope.
     *
     * @param provider provider that produced the result
     * @param pattern  exact pattern being dispatched
     * @param target   exact target involved, or {@code null} for a provider/pattern scoped result
     * @param status   explicit result status
     */
    void recordResult(
                      ICraftingProvider provider,
                      IPatternDetails pattern,
                      @Nullable CraftingDispatchTarget target,
                      CraftingDispatchStatus status);

    /**
     * Checks the independent server-thread budget for another provider-capacity snapshot.
     *
     * @return whether a caller may begin one more read-only capacity capture
     */
    boolean canCaptureProviderCapacity();

    /**
     * Starts measuring one read-only provider-capacity capture.
     *
     * @return closeable scope that charges elapsed time only to the capacity-capture budget
     */
    CapacityCaptureScope beginProviderCapacityCapture();

    /**
     * Returns how many provider-capacity captures completed in this grid window.
     *
     * @return completed capture count
     */
    int capacityCaptureCount();

    /**
     * Returns accumulated monotonic time spent capturing provider capacity.
     *
     * @return measured capture nanoseconds
     */
    long capacityCaptureNanos();

    /**
     * Returns the number of real physical submissions acquired across the complete grid window.
     *
     * @return total acquired physical attempts
     */
    int attemptCount();

    /**
     * Returns how many times one explicit result was observed in this window.
     *
     * @param status result status to inspect
     * @return number of recorded occurrences
     */
    int resultCount(CraftingDispatchStatus status);

    /**
     * Returns the grid-wide hard budget that currently prevents more submission work.
     *
     * @return exhaustion reason, or {@link CraftingDispatchExhaustion#NONE}
     */
    CraftingDispatchExhaustion exhaustion();

    /**
     * Checks whether a grid-wide hard budget has been exhausted.
     *
     * @return whether later workers and runtimes must defer their work
     */
    default boolean isExhausted() {
        return exhaustion() != CraftingDispatchExhaustion.NONE;
    }

    /**
     * Returns how many measured provider submission paths completed in this window.
     *
     * @return completed server submission scopes
     */
    int serverSubmissionCount();

    /**
     * Returns the accumulated monotonic time spent in completed provider submission paths.
     *
     * @return measured server submission nanoseconds
     */
    long serverSubmissionNanos();

    /** Measures one provider path and controls every physical call made from that path. */
    interface SubmissionScope extends AutoCloseable {

        /**
         * Atomically acquires and records one physical submission attempt when quota, time and route state permit it.
         *
         * @param target stable provider-local target fixed during preparation
         * @return {@code true} when the attempt was recorded, or {@code false} when unavailable or exhausted
         */
        boolean tryAcquire(CraftingDispatchTarget target);

        /** Completes timing for this provider path without declaring checked cleanup failures. */
        @Override
        void close();
    }

    /** Measures one server-thread-confined read-only capacity capture. */
    interface CapacityCaptureScope extends AutoCloseable {

        /** Completes capture timing without declaring checked cleanup failures. */
        @Override
        void close();
    }
}
