package com.fish_dan_.data_energistics.common.entrypoint.provider;

import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistration;
import com.fish_dan_.data_energistics.common.pattern.ProviderIdentity;

import appeng.helpers.patternprovider.PatternContainer;

/**
 * Exact frozen plugin declaration selected for one live terminal-visible provider identity.
 *
 * @param registration unique declaration selected by the provider descriptor
 * @param container terminal-visible provider host
 * @param identity stable live provider identity
 */
public record ResolvedProviderBinding(PatternProviderRegistration registration,
                                      PatternContainer container,
                                      ProviderIdentity identity) {
}
