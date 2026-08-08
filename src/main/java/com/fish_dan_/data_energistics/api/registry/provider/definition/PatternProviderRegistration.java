package com.fish_dan_.data_energistics.api.registry.provider.definition;

import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderMenuOpenAdapter;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderPostCommitHook;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One atomic provider extension declaration.
 *
 * <p>
 * The metadata and all optional runtime behaviors are staged together, so a plugin cannot publish a provider
 * matcher without its corresponding lifecycle callbacks.
 * </p>
 *
 * @param metadata        immutable provider matching metadata
 * @param factory         optional runtime counted-dispatch adapter factory
 * @param menuOpenAdapter optional provider-group menu handler
 * @param postCommitHook  optional confirmed-commit observer
 */
public record PatternProviderRegistration(@NotNull PatternProviderMetadata metadata,
                                          @Nullable PatternProviderFactory factory,
                                          @Nullable PatternProviderMenuOpenAdapter menuOpenAdapter,
                                          @Nullable PatternProviderPostCommitHook postCommitHook) {

    /**
     * Rejects metadata-only declarations that cannot contribute any provider behavior.
     */
    public PatternProviderRegistration {
        if (factory == null && menuOpenAdapter == null && postCommitHook == null) {
            throw new IllegalArgumentException("Pattern provider registration requires at least one behavior");
        }
    }

    /**
     * Creates a declaration containing only the counted-dispatch factory.
     *
     * @param metadata immutable provider metadata
     * @param factory  runtime adapter factory
     * @return provider declaration
     */
    public static @NotNull PatternProviderRegistration counted(@NotNull PatternProviderMetadata metadata,
                                                               @NotNull PatternProviderFactory factory) {
        return new PatternProviderRegistration(metadata, factory, null, null);
    }
}
