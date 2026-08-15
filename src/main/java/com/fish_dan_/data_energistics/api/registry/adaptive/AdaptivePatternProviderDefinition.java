package com.fish_dan_.data_energistics.api.registry.adaptive;

import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * Resolves one adaptive pattern-provider profile from an installed provider item.
 *
 * <p>
 * The definition owns all item- or mod-specific recognition. Returning {@code null} means that this definition
 * does not recognize the supplied stack; once it returns a profile, every profile field is required.
 * </p>
 */
@FunctionalInterface
public interface AdaptivePatternProviderDefinition {

    /**
     * Resolves this definition for one non-empty candidate stack.
     *
     * @param providerStack candidate provider item; implementations must not retain or mutate it
     * @return the complete profile, or {@code null} when this definition does not recognize the stack
     */
    @Nullable
    AdaptivePatternProviderProfile resolve(ItemStack providerStack);
}
