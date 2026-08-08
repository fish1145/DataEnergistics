package com.fish_dan_.data_energistics.api.crafting.dispatch;

import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.NotNull;

/**
 * One-shot Trinity admission for committing a fixed number of identical logical crafts.
 *
 * <p>
 * An admission is created by {@link CountedCraftingProviderAdapter#prepareBatch} on the server thread. The
 * dispatcher commits it at most once, on that same thread and for the same prototype. Implementations must not retain
 * world, grid or mutable prototype references after commit returns.
 * </p>
 */
public interface CountedCraftingAdmission {

    /**
     * Returns the fixed positive number of logical crafts accepted by this admission.
     *
     * @return admitted logical craft count
     */
    long count();

    /**
     * Reports whether commit processing crossed an irreversible provider boundary without mutating the prototype.
     *
     * <p>
     * Implementations that dispatch from a copied prototype must set this state immediately before their first
     * external mutation. Once {@code true}, it must remain {@code true}, including when {@link #commit(KeyCounter[])}
     * later returns {@code false} or throws.
     * </p>
     *
     * @return whether the provider has taken ownership of the admitted logical batch
     */
    default boolean hasTransferredInputOwnership() {
        return false;
    }

    /**
     * Attempts the single physical submission represented by this admission.
     *
     * <p>
     * Returning {@code true} transfers ownership of the prototype and all admitted logical copies to the provider.
     * Returning {@code false}, or throwing before ownership transfer, must leave every prototype counter unchanged.
     * Once a provider mutates any prototype counter, the caller conservatively treats the complete admission as
     * transferred even if the provider subsequently returns {@code false} or throws.
     * </p>
     *
     * @param prototype one exact per-craft input prototype for every pattern input slot
     * @return whether the complete admitted group was accepted
     */
    boolean commit(@NotNull KeyCounter @NotNull [] prototype);
}
