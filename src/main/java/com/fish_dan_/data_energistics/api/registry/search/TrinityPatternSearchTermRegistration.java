package com.fish_dan_.data_energistics.api.registry.search;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable declaration of one machine-specific Trinity pattern-search contribution.
 *
 * @param registrationId stable public ID used to reject duplicate contributors
 * @param contributor    contribution invoked for each decoded encoded-pattern stack
 */
public record TrinityPatternSearchTermRegistration(
        ResourceLocation registrationId,
        TrinityPatternSearchTermContributor contributor) {
}
