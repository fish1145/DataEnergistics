package com.fish_dan_.data_energistics.api.crafting.dispatch;

/**
 * Lifecycle handle for one identity-bound virtual crafting output adapter registration.
 */
public interface VirtualCraftingOutputRegistration extends AutoCloseable {

    /**
     * Removes the exact adapter registered by this handle. Repeated calls are harmless.
     */
    @Override
    void close();
}
