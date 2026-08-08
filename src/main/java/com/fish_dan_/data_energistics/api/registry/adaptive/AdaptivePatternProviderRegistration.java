package com.fish_dan_.data_energistics.api.registry.adaptive;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

/**
 * One atomic adaptive pattern-provider declaration.
 *
 * @param registrationId stable identity used for duplicate and ambiguity diagnostics
 * @param definition     provider-specific profile resolver
 */
public record AdaptivePatternProviderRegistration(
                                                  @NotNull ResourceLocation registrationId,
                                                  @NotNull AdaptivePatternProviderDefinition definition) {}
