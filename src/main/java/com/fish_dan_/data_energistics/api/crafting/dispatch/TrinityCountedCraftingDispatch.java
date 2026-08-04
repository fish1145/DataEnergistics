package com.fish_dan_.data_energistics.api.crafting.dispatch;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;

import appeng.api.networking.crafting.ICraftingProvider;

/**
 * Public registration boundary for optional Trinity counted-dispatch compatibility.
 *
 * <p>
 * Registration and unregistration are provider lifecycle operations and must run on the logical server thread.
 * Adapters are keyed by provider object identity, not {@link Object#equals(Object)}. A provider may have only one
 * registered adapter at a time.
 * </p>
 *
 * <p>
 * Third-party integrations should compile only against the DataEnergistics API and declare DataEnergistics as an
 * optional mod dependency. Keep all imports and registration code in an isolated compatibility bootstrap class, and
 * load that class only after confirming DataEnergistics is present. The provider's base class must not implement or
 * otherwise reference these API types; this prevents JVM class linking to DataEnergistics when the mod is absent.
 * </p>
 */
public final class TrinityCountedCraftingDispatch {

    private TrinityCountedCraftingDispatch() {}

    /**
     * Registers an adapter for one exact provider instance.
     *
     * <p>
     * An adapter cannot replace DataEnergistics' direct counted-provider contract. Duplicate registration of the
     * same provider identity fails fast. Close the returned handle when that provider leaves its server-side
     * lifecycle.
     * </p>
     *
     * @param provider provider instance adapted by this registration
     * @param adapter  server-thread counted admission adapter
     * @return one registration handle whose close operation unregisters the adapter
     */
    public static CountedCraftingProviderRegistration registerAdapter(
                                                                      ICraftingProvider provider,
                                                                      CountedCraftingProviderAdapter adapter) {
        return CountedCraftingProviderAdapters.register(provider, adapter);
    }
}
