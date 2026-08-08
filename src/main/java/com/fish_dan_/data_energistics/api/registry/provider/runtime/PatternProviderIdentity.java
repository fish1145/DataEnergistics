package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Public, implementation-independent view of one stable live pattern-provider identity.
 *
 * <p>
 * The common runtime may retain richer location data, but plugin callbacks only need a durable digest and the
 * declaration family used to select their registration. Keeping this projection in the API prevents callbacks from
 * depending on the internal identity implementation.
 * </p>
 */
public interface PatternProviderIdentity {

    /**
     * Returns the schema version included in the canonical identity digest.
     *
     * @return positive identity schema version
     */
    int version();

    /**
     * Returns the stable, algorithm-qualified digest of this provider instance.
     *
     * @return canonical provider digest
     */
    @NotNull
    String digest();

    /**
     * Projects this live identity onto the family declared during plugin registration.
     *
     * @return declaration family, or empty when the provider has only a display-derived fallback identity
     */
    @NotNull
    Optional<@NotNull ProviderIdentityDescriptor> descriptor();
}
