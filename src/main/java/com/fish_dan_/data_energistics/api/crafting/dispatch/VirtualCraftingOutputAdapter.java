package com.fish_dan_.data_energistics.api.crafting.dispatch;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Resolves one pattern-declared placeholder output into a dispatch-time virtual target.
 *
 * <p>
 * Adapters inspect the complete declared {@link AEKey} identity while deliberately leaving quantity accounting to the
 * crafting dispatcher. They must be stateless and must not inspect a CPU, provider, grid, world, or crafting job.
 * </p>
 */
@FunctionalInterface
public interface VirtualCraftingOutputAdapter {

    /**
     * Resolves the target represented by one complete pattern output.
     *
     * <p>
     * Returning a target suppresses the declared placeholder from physical completion. The dispatcher creates the
     * target only after a provider has accepted the corresponding logical craft. Returning empty leaves the output's
     * ordinary physical semantics unchanged.
     * </p>
     *
     * @param declaredOutput complete declared output identity and per-craft amount
     * @return represented target identity, or empty when this adapter does not own the output
     */
    @NotNull
    Optional<@NotNull AEKey> resolveTarget(@NotNull GenericStack declaredOutput);

    /**
     * Selects the completion accounting policy for an adapter-claimed output.
     *
     * <p>
     * The default preserves the original virtual-target behavior. An adapter that represents a control token rather
     * than a physical result may return {@link VirtualCraftingCompletionMode#COMPLETE_WITHOUT_OUTPUT}; its declared
     * key remains the logical and accounting key, and no CPU, requester, or network inventory receives an item.
     * </p>
     *
     * @param declaredOutput complete declared output identity and per-craft amount
     * @return completion accounting mode
     */
    default @NotNull VirtualCraftingCompletionMode completionMode(@NotNull GenericStack declaredOutput) {
        return VirtualCraftingCompletionMode.DELIVER_TARGET;
    }
}
