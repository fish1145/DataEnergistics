package com.fish_dan_.data_energistics.api.crafting.dispatch;

/**
 * Lifecycle handle for one provider-identity counted dispatch adapter registration.
 */
@FunctionalInterface
public interface CountedCraftingProviderRegistration extends AutoCloseable {

    /**
     * Unregisters the adapter.
     *
     * <p>
     * This operation must run on the server thread. Closing a registration more than once fails fast.
     * </p>
     */
    @Override
    void close();
}
