package com.fish_dan_.data_energistics.api.registry.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;

/**
 * Creates a counted-dispatch adapter when a declared provider integration is bound to a live provider instance.
 */
@FunctionalInterface
public interface PatternProviderFactory {

    /**
     * Builds the adapter for one provider lifecycle.
     *
     * @param context live provider and immutable declaration context
     * @return adapter owned by this provider lifecycle
     */
    CountedCraftingProviderAdapter create(PatternProviderFactoryContext context);
}
