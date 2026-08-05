package com.fish_dan_.data_energistics.common.pattern;

import appeng.helpers.patternprovider.PatternContainer;

/**
 * Resolves stable provider identities from AE2 terminal containers without reflection or menu-session state.
 */
public interface ProviderIdentityResolver {

    /**
     * Creates the resolver backed by live Minecraft and AE2 registry metadata.
     *
     * @return production resolver
     */
    static ProviderIdentityResolver create() {
        return new ProviderIdentityResolverImpl();
    }

    /**
     * Resolves one provider according to physical, Trinity and virtual identity precedence.
     *
     * @param provider discovered AE2 terminal container
     * @return stable provider identity
     * @throws IllegalStateException when a purported physical provider has incomplete or ambiguous world metadata
     */
    ProviderIdentity resolve(PatternContainer provider);
}
