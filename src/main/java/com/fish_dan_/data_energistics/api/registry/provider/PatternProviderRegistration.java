package com.fish_dan_.data_energistics.api.registry.provider;

import org.jetbrains.annotations.Nullable;

/**
 * One atomic provider extension declaration.
 *
 * <p>The metadata and all optional runtime behaviors are staged together, so a plugin cannot publish a provider
 * matcher without its corresponding lifecycle callbacks.</p>
 *
 * @param metadata       immutable provider matching metadata
 * @param factory        runtime counted-dispatch adapter factory
 * @param menuOpenAdapter optional provider-group menu handler
 * @param postCommitHook optional confirmed-commit observer
 */
public record PatternProviderRegistration(PatternProviderMetadata metadata,
                                          PatternProviderFactory factory,
                                          @Nullable PatternProviderMenuOpenAdapter menuOpenAdapter,
                                          @Nullable PatternProviderPostCommitHook postCommitHook) {

    /**
     * Creates a declaration containing only the counted-dispatch factory.
     *
     * @param metadata immutable provider metadata
     * @param factory runtime adapter factory
     * @return provider declaration
     */
    public static PatternProviderRegistration counted(PatternProviderMetadata metadata,
                                                       PatternProviderFactory factory) {
        return new PatternProviderRegistration(metadata, factory, null, null);
    }
}
