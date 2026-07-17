package com.fish_dan_.data_energistics.client.emi;

import net.minecraft.resources.ResourceLocation;

/**
 * Keeps EMI's slash-prefixed synthetic identity separate from the canonical transfer identity.
 */
final class EmiMultiblockRecipeId {

    private EmiMultiblockRecipeId() {}

    /**
     * Maps one canonical registered id to the synthetic id required by EMI-only recipes.
     */
    static ResourceLocation synthetic(ResourceLocation registeredRecipeId) {
        if (registeredRecipeId == null) {
            throw new IllegalArgumentException("EMI synthetic multiblock recipe id requires a registered recipe id");
        }
        if (registeredRecipeId.getPath().startsWith("/")) {
            throw new IllegalArgumentException("Registered multiblock recipe id must be canonical: " + registeredRecipeId);
        }
        return ResourceLocation.fromNamespaceAndPath(
                registeredRecipeId.getNamespace(),
                "/" + registeredRecipeId.getPath());
    }
}
