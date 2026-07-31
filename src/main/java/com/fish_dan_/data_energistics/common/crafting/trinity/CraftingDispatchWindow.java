package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Bounds physical crafting submissions for one AE grid tick while sharing provider state across Trinity runtimes.
 *
 * <p>
 * Accounting is based on provider and pattern object identity. Preparing capacity does not consume quota; a successful
 * {@link #tryAcquire(ICraftingProvider, IPatternDetails, CraftingDispatchTarget)} call records one real submission
 * attempt regardless of that attempt's result. Explicit result facts drive provider, pattern and target scoped
 * exclusions that expire with this window.
 * </p>
 */
public interface CraftingDispatchWindow {

    /** Maximum number of physical submission attempts permitted for one provider in a window. */
    int MAX_ATTEMPTS_PER_PROVIDER = 16;

    /** Maximum number of physical submission attempts permitted across the complete grid in one window. */
    int MAX_ATTEMPTS_PER_GRID = MAX_ATTEMPTS_PER_PROVIDER * 16;

    /**
     * Creates an empty dispatch window for one AE grid tick.
     *
     * @return independent dispatch window
     */
    static CraftingDispatchWindow create() {
        return new CraftingDispatchWindowImpl();
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
     * Atomically acquires and records one physical submission attempt when quota and route state permit it.
     *
     * @param provider provider instance about to receive a real submission
     * @param pattern  pattern about to be submitted
     * @param target   stable provider-local target fixed during preparation
     * @return {@code true} when the attempt was recorded, or {@code false} when unavailable or exhausted
     */
    boolean tryAcquire(
                       ICraftingProvider provider,
                       IPatternDetails pattern,
                       CraftingDispatchTarget target);

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
}
