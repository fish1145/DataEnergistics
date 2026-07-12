package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.resources.ResourceLocation;

import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

/**
 * Resolves the stable recipe identity represented by one supported encoded pattern implementation.
 *
 * <p>
 * Resolvers are extensions for pattern implementations whose recipe identity is not one of AE2's built-in
 * encoded components. A resolver first declares support and must then return a non-null recipe ID.
 * </p>
 */
public interface TrinityPatternRecipeIdResolver {

    /**
     * @return stable registration identity used to reject duplicate resolver registrations
     */
    ResourceLocation id();

    /**
     * Reports whether this resolver owns the supplied decoded pattern.
     *
     * @param pattern decoded molecular-assembler-compatible pattern
     * @return whether {@link #recipeId(IMolecularAssemblerSupportedPattern)} may be called
     */
    boolean supports(IMolecularAssemblerSupportedPattern pattern);

    /**
     * Returns the recipe selected by the supplied pattern.
     *
     * @param pattern decoded pattern previously accepted by {@link #supports(IMolecularAssemblerSupportedPattern)}
     * @return stable recipe ID; returning {@code null} is a resolver contract violation
     */
    ResourceLocation recipeId(IMolecularAssemblerSupportedPattern pattern);
}
