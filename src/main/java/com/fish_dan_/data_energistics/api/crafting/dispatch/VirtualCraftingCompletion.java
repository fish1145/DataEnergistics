package com.fish_dan_.data_energistics.api.crafting.dispatch;

import appeng.api.stacks.GenericStack;

/**
 * Immutable bookkeeping token produced after a provider accepts one virtual crafting output.
 *
 * @param stack the key and amount whose waiting counter must be completed
 * @param mode  whether the completed counter may materialize an output
 */
public record VirtualCraftingCompletion(GenericStack stack,
                                        VirtualCraftingCompletionMode mode) {

    /**
     * Rejects incomplete completion tokens before they can enter a CPU ledger.
     */
    public VirtualCraftingCompletion {
        if (stack.amount() <= 0L) {
            throw new IllegalArgumentException("A virtual crafting completion requires a positive complete stack");
        }
    }
}
