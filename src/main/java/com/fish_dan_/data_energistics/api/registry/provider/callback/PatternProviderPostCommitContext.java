package com.fish_dan_.data_energistics.api.registry.provider.callback;

import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;

import net.minecraft.world.item.ItemStack;

import appeng.helpers.patternprovider.PatternContainer;

/**
 * Immutable facts supplied after a provider inventory delta has been confirmed.
 *
 * <p>
 * A hook observes a completed commit. It cannot veto or rewrite the transaction; failures are isolated and logged by
 * the runtime registry.
 * </p>
 *
 * @param provider       provider whose pattern inventory changed
 * @param identity       stable identity of the provider
 * @param encodedPattern defensive copy of the encoded pattern stack
 * @param committedCount number of patterns committed by the transaction
 */
public record PatternProviderPostCommitContext(PatternContainer provider,
                                               PatternProviderIdentity identity,
                                               ItemStack encodedPattern,
                                               long committedCount) {

    /**
     * Validates the post-commit facts and prevents retention of a mutable caller-owned stack.
     */
    public PatternProviderPostCommitContext {
        encodedPattern = encodedPattern.copy();
        if (committedCount <= 0L) {
            throw new IllegalArgumentException("A post-commit context requires a positive committed count");
        }
    }

    /**
     * Returns a defensive copy so a hook cannot mutate the transaction snapshot retained by this context.
     */
    @Override
    public ItemStack encodedPattern() {
        return this.encodedPattern.copy();
    }
}
