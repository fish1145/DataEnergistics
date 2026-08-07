package com.fish_dan_.data_energistics.common.entrypoint.provider;

import com.fish_dan_.data_energistics.accessor.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderFactory;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderFactoryContext;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentitySource;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderRuntimeLink;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentityResolver;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternContainer;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Identity-preserving runtime registry backed by the immutable common-setup declaration snapshot.
 */
final class PatternProviderRuntimeRegistryImpl implements PatternProviderRuntimeRegistry {

    private final Map<ProviderIdentityDescriptor, PatternProviderRegistration> registrationsByIdentity;
    private final ProviderIdentityResolver identityResolver;
    private final Map<CraftingProviderId, ICraftingProvider> publications = new HashMap<>();
    private final Map<CraftingProviderId, ProviderAdapterState> adaptersByPublication = new HashMap<>();
    private final Map<ICraftingProvider, ProviderAdapterState> adaptersByProvider = new IdentityHashMap<>();

    PatternProviderRuntimeRegistryImpl(List<PatternProviderRegistration> registrations) {
        this(registrations, ProviderIdentityResolver.create());
    }

    PatternProviderRuntimeRegistryImpl(List<PatternProviderRegistration> registrations,
                                       ProviderIdentityResolver identityResolver) {
        LinkedHashMap<ProviderIdentityDescriptor, PatternProviderRegistration> indexed = new LinkedHashMap<>();
        for (PatternProviderRegistration registration : registrations) {
            ProviderIdentityDescriptor descriptor = registration.metadata().providerIdentity();
            PatternProviderRegistration existing = indexed.putIfAbsent(descriptor, registration);
            if (existing != null) {
                throw new IllegalStateException("Duplicate frozen pattern provider identity: " + descriptor);
            }
        }
        this.registrationsByIdentity = Map.copyOf(indexed);
        this.identityResolver = identityResolver;
    }

    @Override
    public void bind(CraftingProviderId publicationId, ICraftingProvider provider) {
        if (this.publications.putIfAbsent(publicationId, provider) != null) {
            throw new IllegalStateException("Pattern provider publication is already bound: " + publicationId);
        }

        Optional<ResolvedProviderBinding> resolved = this.resolve(provider);
        if (resolved.isEmpty()) {
            return;
        }
        ResolvedProviderBinding binding = resolved.get();
        PatternProviderFactory factory = binding.registration().factory();
        if (factory == null) {
            return;
        }

        ProviderAdapterState current = this.adaptersByProvider.get(provider);
        if (current != null) {
            current.retain(binding);
            this.adaptersByPublication.put(publicationId, current);
            return;
        }

        PatternProviderFactoryContext context = new PatternProviderFactoryContext(
                provider,
                binding.container(),
                binding.identity(),
                binding.registration().metadata());
        CountedCraftingProviderAdapter adapter;
        try {
            adapter = factory.create(context);
        } catch (RuntimeException exception) {
            throw factoryFailure(binding, exception);
        }
        if (adapter == null) {
            throw factoryFailure(
                    binding,
                    new IllegalStateException("Provider adapter factory returned null"));
        }
        CountedCraftingProviderAdapters.register(provider, adapter);
        ProviderAdapterState created = new ProviderAdapterState(binding, adapter);
        this.adaptersByProvider.put(provider, created);
        this.adaptersByPublication.put(publicationId, created);
    }

    @Override
    public void unbind(CraftingProviderId publicationId) {
        ICraftingProvider provider = this.publications.get(publicationId);
        if (provider == null) {
            throw new IllegalStateException("Pattern provider publication is not bound: " + publicationId);
        }
        ProviderAdapterState state = this.adaptersByPublication.get(publicationId);
        if (state == null) {
            this.publications.remove(publicationId);
            return;
        }
        if (state.publicationCount() == 1) {
            CountedCraftingProviderAdapters.unregister(provider, state.adapter());
            this.adaptersByProvider.remove(provider);
        } else {
            state.release();
        }
        this.adaptersByPublication.remove(publicationId);
        this.publications.remove(publicationId);
    }

    @Override
    public Optional<ResolvedProviderBinding> resolve(PatternContainer container) {
        return this.resolveIdentity(null, container);
    }

    @Override
    public void clear() {
        CountedCraftingProviderAdapters.clear();
        this.adaptersByProvider.clear();
        this.adaptersByPublication.clear();
        this.publications.clear();
    }

    private Optional<ResolvedProviderBinding> resolve(ICraftingProvider provider) {
        PatternContainer container = this.resolveContainer(provider);
        return container == null ? Optional.empty() : this.resolveIdentity(provider, container);
    }

    private Optional<ResolvedProviderBinding> resolveIdentity(ICraftingProvider provider,
                                                               PatternContainer container) {
        ProviderIdentity identity;
        if (provider instanceof PatternProviderIdentitySource source) {
            identity = source.providerIdentity();
        } else if (container instanceof PatternProviderIdentitySource source) {
            identity = source.providerIdentity();
        } else {
            identity = this.identityResolver.resolve(container);
        }
        Optional<ProviderIdentityDescriptor> descriptor = ProviderIdentityDescriptor.from(identity);
        if (descriptor.isEmpty()) {
            return Optional.empty();
        }
        PatternProviderRegistration registration = this.registrationsByIdentity.get(descriptor.get());
        return registration == null ?
                Optional.empty() : Optional.of(new ResolvedProviderBinding(registration, container, identity));
    }

    private PatternContainer resolveContainer(ICraftingProvider provider) {
        if (provider instanceof PatternProviderRuntimeLink link) {
            return link.patternContainer();
        }
        if (provider instanceof PatternContainer container) {
            return container;
        }
        if (provider instanceof PatternProviderBatchAccess access) {
            return access.dataEnergistics$getHost();
        }
        return null;
    }

    private static IllegalStateException factoryFailure(ResolvedProviderBinding binding,
                                                        RuntimeException cause) {
        return new IllegalStateException(
                "Pattern provider plugin '" + binding.registration().metadata().registrationId()
                        + "' failed to create an adapter for " + binding.identity(),
                cause);
    }

    private static final class ProviderAdapterState {

        private final ResolvedProviderBinding binding;
        private final CountedCraftingProviderAdapter adapter;
        private int publicationCount = 1;

        private ProviderAdapterState(ResolvedProviderBinding binding,
                                     CountedCraftingProviderAdapter adapter) {
            this.binding = binding;
            this.adapter = adapter;
        }

        private CountedCraftingProviderAdapter adapter() {
            return this.adapter;
        }

        private int publicationCount() {
            return this.publicationCount;
        }

        private void retain(ResolvedProviderBinding current) {
            if (this.binding.registration() != current.registration() ||
                    !this.binding.identity().equals(current.identity())) {
                throw new IllegalStateException("One provider instance resolved to different plugin bindings");
            }
            this.publicationCount = Math.incrementExact(this.publicationCount);
        }

        private void release() {
            this.publicationCount--;
        }
    }
}
