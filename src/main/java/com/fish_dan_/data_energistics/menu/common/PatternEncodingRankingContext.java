package com.fish_dan_.data_energistics.menu.common;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/**
 * Identifies the exact viewer recipe type used for provider matching and ranking.
 *
 * <p>
 * The snapshot contains only the canonical recipe-type identifier. Workstation candidates are resolved exclusively
 * from provider metadata exposed by the current server grid.
 * </p>
 */
public record PatternEncodingRankingContext(@NotNull ResourceLocation recipeTypeId) {

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
    public static @NotNull PatternEncodingRankingContext of(@NotNull ResourceLocation recipeTypeId) {
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
