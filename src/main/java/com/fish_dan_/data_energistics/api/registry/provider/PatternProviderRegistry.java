package com.fish_dan_.data_energistics.api.registry.provider;

import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderFactory;

import org.jetbrains.annotations.NotNull;

/**
 * Declaration facet for provider metadata and provider-lifecycle integrations.
 */
public interface PatternProviderRegistry {

    /**
     * Registers one atomic provider declaration.
     *
     * @param registration metadata and lifecycle callbacks to stage
     */
    void register(@NotNull PatternProviderRegistration registration);

    /**
     * Registers a provider factory using the concise mapping of the former counted-dispatch API.
     *
     * @param metadata immutable provider matching metadata
     * @param factory  runtime adapter factory
     */
    default void registerFactory(@NotNull PatternProviderMetadata metadata,
                                 @NotNull PatternProviderFactory factory) {
        this.register(PatternProviderRegistration.counted(metadata, factory));
    }
}
