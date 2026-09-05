package com.fish_dan_.data_energistics.api.registry.provider.definition;

import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderWorkstationSource;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Immutable declaration that supplies custom workstation topology for one provider identity family.
 *
 * @param registrationId   globally unique plugin-owned source registration ID
 * @param providerIdentity exact provider identity family whose live leaves use the source
 * @param source           custom, remote or non-adjacent workstation resolver
 */
public record PatternProviderWorkstationSourceRegistration(ResourceLocation registrationId,
                                                           ProviderIdentityDescriptor providerIdentity,
                                                           PatternProviderWorkstationSource source) {

    public PatternProviderWorkstationSourceRegistration {
        Objects.requireNonNull(registrationId, "Pattern provider workstation source registration ID");
        Objects.requireNonNull(providerIdentity, "Pattern provider workstation source identity");
        Objects.requireNonNull(source, "Pattern provider workstation source");
    }
}
