package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderRegistration;

import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Internal identity registry used by the synchronous Trinity dispatcher.
 */
interface CountedCraftingProviderAdapterRegistry {

    /**
     * Registers one adapter for the exact provider identity and advances the mutation revision once.
     *
     * @param provider provider identity owning the adapter
     * @param adapter  counted dispatch adapter
     * @return lifecycle handle for unregistering the exact entry
     */
    CountedCraftingProviderRegistration register(
            ICraftingProvider provider,
            CountedCraftingProviderAdapter adapter);

    /**
     * Finds the adapter registered for the exact provider identity.
     *
     * @param provider provider identity to resolve
     * @return registered adapter, or {@code null} when the provider has no current entry
     */
    @Nullable
    CountedCraftingProviderAdapter find(ICraftingProvider provider);

    /**
     * Returns the monotonic generation advanced exactly once per successful registration or unregistration.
     *
     * @return current registry mutation revision
     */
    long mutationRevision();
}
