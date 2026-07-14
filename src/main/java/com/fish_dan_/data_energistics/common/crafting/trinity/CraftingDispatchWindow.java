package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.networking.crafting.ICraftingProvider;

/**
 * Bounds physical crafting submissions for one AE grid tick while sharing provider state across Trinity runtimes.
 *
 * <p>
 * Accounting is based on provider object identity. Preparing capacity does not consume quota; a successful
 * {@link #tryAcquire(ICraftingProvider)} call records one real submission attempt regardless of that attempt's result.
 * </p>
 */
public interface CraftingDispatchWindow {

    /** Maximum number of physical submission attempts permitted for one provider in a window. */
    int MAX_ATTEMPTS_PER_PROVIDER = 16;

    /**
     * Creates an empty dispatch window for one AE grid tick.
     *
     * @return independent dispatch window
     */
    static CraftingDispatchWindow create() {
        return new CraftingDispatchWindowImpl();
    }

    /**
     * Checks whether a provider is available and still has physical submission quota.
     *
     * @param provider provider instance to inspect
     * @return whether a caller may prepare another submission for this provider
     */
    boolean canAttempt(ICraftingProvider provider);

    /**
     * Atomically acquires and records one physical submission attempt when quota remains.
     *
     * @param provider provider instance about to receive a real submission
     * @return {@code true} when the attempt was recorded, or {@code false} when unavailable or exhausted
     */
    boolean tryAcquire(ICraftingProvider provider);

    /**
     * Marks a provider unavailable for the remainder of this window after capacity preparation accepts no crafts.
     *
     * @param provider provider instance that reported no capacity
     */
    void markUnavailable(ICraftingProvider provider);
}
