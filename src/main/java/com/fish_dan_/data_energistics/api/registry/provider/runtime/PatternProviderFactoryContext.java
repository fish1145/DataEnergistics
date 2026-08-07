package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternContainer;

/**
 * Runtime context supplied while a declaration creates an adapter for one live provider instance.
 *
 * <p>The context is valid only for the provider lifecycle callback. It must not be retained by a factory or placed in
 * a computation cache.</p>
 *
 * @param provider live AE2 crafting provider instance
 * @param container terminal-visible host represented by the same provider publication
 * @param identity resolved stable identity of that provider instance
 * @param metadata immutable declaration selected for the provider
 */
public record PatternProviderFactoryContext(ICraftingProvider provider,
                                             PatternContainer container,
                                             ProviderIdentity identity,
                                             PatternProviderMetadata metadata) {
}
