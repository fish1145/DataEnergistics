package com.fish_dan_.data_energistics.api.registry.recipe;

import net.minecraft.resources.ResourceLocation;

import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

/**
 * Resolves the stable recipe identity represented by one supported encoded-pattern implementation.
 *
 * <p>
 * Resolvers extend Trinity support for patterns whose recipe identity is not one of AE2's built-in encoded
 * components. They are registered during common setup and invoked only through the frozen resolver snapshot.
 * </p>
 */
public interface TrinityPatternRecipeIdResolver {

    /**
     * @return stable registration identity used to reject duplicate resolvers
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
     * Returns the stable recipe selected by a previously accepted pattern.
     *
     * @param pattern pattern accepted by {@link #supports(IMolecularAssemblerSupportedPattern)}
     * @return non-null stable recipe ID
     */
    ResourceLocation recipeId(IMolecularAssemblerSupportedPattern pattern);
}
