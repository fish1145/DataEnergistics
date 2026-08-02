package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderRegistration;

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

    private final Map<ICraftingProvider, Entry> registrations = new IdentityHashMap<>();
    private long mutationRevision;

    @Override
    public CountedCraftingProviderRegistration register(
                                                        ICraftingProvider provider,
                                                        CountedCraftingProviderAdapter adapter) {
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
        Entry entry = new Entry(adapter);
        this.registrations.put(provider, entry);
        this.mutationRevision = nextRevision;
        return new Registration(provider, entry);
    }

    @Override
    public @Nullable CountedCraftingProviderAdapter find(ICraftingProvider provider) {
        Entry entry = this.registrations.get(provider);
        return entry == null ? null : entry.adapter();
    }

    @Override
    public long mutationRevision() {
        return this.mutationRevision;
    }

    private record Entry(CountedCraftingProviderAdapter adapter) {}

    private final class Registration implements CountedCraftingProviderRegistration {

        private final ICraftingProvider provider;
        private final Entry entry;
        private boolean closed;

        private Registration(ICraftingProvider provider, Entry entry) {
            this.provider = provider;
            this.entry = entry;
        }

        @Override
        public void close() {
            if (this.closed) {
                throw new IllegalStateException("Counted crafting provider registration is already closed");
            }
            if (registrations.get(this.provider) != this.entry) {
                throw new IllegalStateException("Counted crafting provider registration is no longer current");
            }
            long nextRevision = Math.incrementExact(mutationRevision);
            registrations.remove(this.provider);
            mutationRevision = nextRevision;
            this.closed = true;
        }
    }
}
