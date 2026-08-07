package com.fish_dan_.data_energistics.api.registry.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;

import org.jetbrains.annotations.Nullable;

/**
 * Creates a counted-dispatch adapter when a declared provider integration is bound to a live provider instance.
 */
@FunctionalInterface
public interface PatternProviderFactory {

    /**
     * Builds the adapter for one provider lifecycle.
     *
     * @param context live provider and immutable declaration context
     * @return adapter for the provider, or {@code null} when the provider is currently not dispatch-capable
     */
    @Nullable
    CountedCraftingProviderAdapter create(PatternProviderFactoryContext context);
}
