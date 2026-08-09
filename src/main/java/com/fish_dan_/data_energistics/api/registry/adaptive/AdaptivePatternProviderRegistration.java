package com.fish_dan_.data_energistics.api.registry.adaptive;

import net.minecraft.resources.ResourceLocation;

/**
 * One atomic adaptive pattern-provider declaration.
 *
 * @param registrationId stable identity used for duplicate and ambiguity diagnostics
 * @param definition     provider-specific profile resolver
 */
public record AdaptivePatternProviderRegistration(
                                                  ResourceLocation registrationId,
                                                  AdaptivePatternProviderDefinition definition) {}
