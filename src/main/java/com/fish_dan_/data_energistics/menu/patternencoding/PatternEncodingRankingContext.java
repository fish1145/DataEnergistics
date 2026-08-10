package com.fish_dan_.data_energistics.menu.patternencoding;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * Identifies the exact viewer recipe type used for provider matching and ranking.
 *
 * <p>
 * The snapshot contains only the canonical recipe-type identifier used as the learning key. A viewer transfer supplies
 * ephemeral workstation candidates separately; the server matches them against registered provider metadata or AE2
 * terminal groups exposed by the current grid.
 * </p>
 */
public record PatternEncodingRankingContext(ResourceLocation recipeTypeId) {

    /**
     * Maximum UTF-8 bytes accepted for one registry identifier on the wire and in persistence.
     */
    public static final int MAX_RESOURCE_LOCATION_BYTES = 256;

    /**
     * Validates the canonical viewer recipe-type identifier.
     */
    public PatternEncodingRankingContext {
        validateResourceLocation(recipeTypeId, "recipe type id");
    }

    /**
     * Creates a validated context from the stable viewer recipe-type identifier.
     */
    public static PatternEncodingRankingContext of(ResourceLocation recipeTypeId) {
        return new PatternEncodingRankingContext(recipeTypeId);
    }

    private static int utf8Length(ResourceLocation id) {
        return id.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static void validateResourceLocation(ResourceLocation id, String label) {
        if (utf8Length(id) > MAX_RESOURCE_LOCATION_BYTES) {
            throw new IllegalArgumentException(
                    "Pattern ranking " + label + " exceeds " + MAX_RESOURCE_LOCATION_BYTES + " UTF-8 bytes");
        }
    }
}
