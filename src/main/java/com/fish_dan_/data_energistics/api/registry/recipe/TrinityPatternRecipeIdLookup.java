package com.fish_dan_.data_energistics.api.registry.recipe;

import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Frozen runtime view used to resolve stable Trinity recipe identities.
 */
public interface TrinityPatternRecipeIdLookup {

    /**
     * Resolves exactly one registered recipe identity.
     *
     * @param pattern decoded pattern to resolve
     * @return sole matching resolution, or empty when no resolver owns the pattern
     * @throws IllegalStateException when multiple resolvers match the same pattern
     */
    @NotNull
    Optional<@NotNull TrinityPatternRecipeIdResolution> resolve(
            @NotNull IMolecularAssemblerSupportedPattern pattern);
}
