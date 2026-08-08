package com.fish_dan_.data_energistics.common.entrypoint.provider;

import com.fish_dan_.data_energistics.accessor.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderFactory;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderFactoryContext;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentitySource;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderRuntimeLink;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentityResolver;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public void bind(@NotNull CraftingProviderId publicationId, @NotNull ICraftingProvider provider) {
        if (this.publications.containsKey(publicationId)) {
            throw new IllegalStateException("Pattern provider publication is already bound: " + publicationId);
        }

        Optional<ResolvedProviderBinding> resolved = this.resolve(provider);
        if (resolved.isEmpty()) {
            this.publications.put(publicationId, provider);
            return;
        }
        ResolvedProviderBinding binding = resolved.get();
        PatternProviderFactory factory = binding.registration().factory();
        if (factory == null) {
            this.publications.put(publicationId, provider);
            return;
        }

        ProviderAdapterState current = this.adaptersByProvider.get(provider);
        if (current != null) {
            current.retain(binding);
            try {
                this.adaptersByPublication.put(publicationId, current);
                this.publications.put(publicationId, provider);
            } catch (RuntimeException exception) {
                this.adaptersByPublication.remove(publicationId);
                this.publications.remove(publicationId);
                current.release();
                throw exception;
            }
            return;
        }

        PatternProviderFactoryContext context = new PatternProviderFactoryContext(
                provider,
                binding.container(),
                binding.identity(),
                binding.registration().metadata());
        CountedCraftingProviderAdapter adapter;
        try {
            adapter = requireFactoryResult(factory.create(context));
        } catch (RuntimeException exception) {
            throw factoryFailure(binding, exception);
        }
        ProviderAdapterState created = new ProviderAdapterState(binding, adapter);
        try {
            CountedCraftingProviderAdapters.register(provider, adapter);
            this.adaptersByProvider.put(provider, created);
            this.adaptersByPublication.put(publicationId, created);
            this.publications.put(publicationId, provider);
        } catch (RuntimeException exception) {
            this.publications.remove(publicationId);
            this.adaptersByPublication.remove(publicationId);
            this.adaptersByProvider.remove(provider, created);
            try {
                CountedCraftingProviderAdapters.unregister(provider, adapter);
            } catch (RuntimeException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
    }

    @Override
    public void unbind(@NotNull CraftingProviderId publicationId) {
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
    public @NotNull Optional<ResolvedProviderBinding> resolve(@NotNull PatternContainer container) {
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
            identity = Objects.requireNonNull(source.providerIdentity(), "External crafting provider identity");
        } else if (container instanceof PatternProviderIdentitySource source) {
            identity = Objects.requireNonNull(source.providerIdentity(), "External pattern provider identity");
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
                "Pattern provider plugin '" + binding.registration().metadata().registrationId() + "' failed to create an adapter for " + binding.identity(),
                cause);
    }

    /**
     * Enforces the public non-null factory contract at the untrusted plugin callback boundary.
     */
    private static @NotNull CountedCraftingProviderAdapter requireFactoryResult(
                                                                                @UnknownNullability CountedCraftingProviderAdapter adapter) {
        return Objects.requireNonNull(adapter, "Provider adapter factory returned null");
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
