package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;

import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Internal identity registry used by the synchronous Trinity dispatcher.
 * <p>
 * Server-thread identity registry with a monotonic mutation revision.
 *
 * <p>
 * The registry deliberately performs no global Minecraft thread lookup. Its owner establishes the server-thread
 * lifecycle contract, keeping the API usable in dedicated-server and direct logic-test environments.
 * </p>
 */
final class CountedCraftingProviderAdapterRegistry {

    private final Map<ICraftingProvider, CountedCraftingProviderAdapter> registrations = new IdentityHashMap<>();
    private long mutationRevision;

    /**
     * Registers one adapter for the exact provider identity and advances the mutation revision once.
     *
     * @param provider provider identity owning the adapter
     * @param adapter  counted dispatch adapter
     */
    public void register(ICraftingProvider provider, CountedCraftingProviderAdapter adapter) {
        if (provider == null) {
            throw new IllegalArgumentException("Counted crafting provider must not be null");
        }
        if (adapter == null) {
            throw new IllegalArgumentException("Counted crafting provider adapter must not be null");
        }
        if (this.registrations.containsKey(provider)) {
            throw new IllegalStateException("Counted crafting provider identity is already registered: " + provider);
        }
        long nextRevision = Math.incrementExact(this.mutationRevision);
        this.registrations.put(provider, adapter);
        this.mutationRevision = nextRevision;
    }

    /**
     * Removes the exact provider and adapter pair and advances the mutation revision once.
     *
     * @param provider provider identity owning the adapter
     * @param adapter  exact current adapter
     */
    public void unregister(ICraftingProvider provider, CountedCraftingProviderAdapter adapter) {
        if (this.registrations.get(provider) != adapter) {
            throw new IllegalStateException("Counted crafting provider adapter is not current: " + provider);
        }
        long nextRevision = Math.incrementExact(this.mutationRevision);
        this.registrations.remove(provider);
        this.mutationRevision = nextRevision;
    }

    /**
     * Removes every live adapter when the owning server stops.
     */
    public void clear() {
        if (this.registrations.isEmpty()) {
            return;
        }
        long nextRevision = Math.incrementExact(this.mutationRevision);
        this.registrations.clear();
        this.mutationRevision = nextRevision;
    }

    /**
     * Finds the adapter registered for the exact provider identity.
     *
     * @param provider provider identity to resolve
     * @return registered adapter, or {@code null} when the provider has no current entry
     */
    @Nullable
    public CountedCraftingProviderAdapter find(ICraftingProvider provider) {
        return this.registrations.get(provider);
    }

    /**
     * Returns the monotonic generation advanced exactly once per successful registration or unregistration.
     *
     * @return current registry mutation revision
     */
    public long mutationRevision() {
        return this.mutationRevision;
    }
}
