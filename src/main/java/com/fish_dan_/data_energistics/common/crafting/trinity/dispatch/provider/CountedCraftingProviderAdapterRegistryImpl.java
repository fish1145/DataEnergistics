package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;

import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Server-thread identity registry with a monotonic mutation revision.
 *
 * <p>
 * The registry deliberately performs no global Minecraft thread lookup. Its owner establishes the server-thread
 * lifecycle contract, keeping the API usable in dedicated-server and direct logic-test environments.
 * </p>
 */
final class CountedCraftingProviderAdapterRegistryImpl implements CountedCraftingProviderAdapterRegistry {

    private final Map<ICraftingProvider, CountedCraftingProviderAdapter> registrations = new IdentityHashMap<>();
    private long mutationRevision;

    @Override
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

    @Override
    public void unregister(ICraftingProvider provider, CountedCraftingProviderAdapter adapter) {
        if (this.registrations.get(provider) != adapter) {
            throw new IllegalStateException("Counted crafting provider adapter is not current: " + provider);
        }
        long nextRevision = Math.incrementExact(this.mutationRevision);
        this.registrations.remove(provider);
        this.mutationRevision = nextRevision;
    }

    @Override
    public void clear() {
        if (this.registrations.isEmpty()) {
            return;
        }
        long nextRevision = Math.incrementExact(this.mutationRevision);
        this.registrations.clear();
        this.mutationRevision = nextRevision;
    }

    @Override
    public @Nullable CountedCraftingProviderAdapter find(ICraftingProvider provider) {
        return this.registrations.get(provider);
    }

    @Override
    public long mutationRevision() {
        return this.mutationRevision;
    }
}
