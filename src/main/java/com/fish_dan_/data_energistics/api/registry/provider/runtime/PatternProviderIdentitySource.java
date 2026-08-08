package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import org.jetbrains.annotations.NotNull;

/**
 * Supplies the explicit typed identity of an external provider that cannot use a built-in physical identity.
 */
public interface PatternProviderIdentitySource {

    /**
     * Returns the stable live identity used to bind this provider to an external declaration.
     *
     * @return versioned external provider identity
     */
    @NotNull
    ExternalPatternProviderIdentity providerIdentity();
}
