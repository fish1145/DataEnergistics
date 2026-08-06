package com.fish_dan_.data_energistics.menu.common;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * Identifies the recipe and workstation pair for which provider upload history is relevant.
 *
 * <p>
 * The pair is intentionally exact: history from a different recipe type or workstation must not influence
 * provider ordering.
 * </p>
 */
public record PatternEncodingRankingContext(String recipeScope, ResourceLocation workstation) {

    public static final int MAX_RECIPE_SCOPE_BYTES = 256;
    public static final int MAX_WORKSTATION_BYTES = 256;
    private static final String TYPE_PREFIX = "type:";
    private static final String RECIPE_PREFIX = "recipe:";

    /** Validates the bounded, version-independent values accepted from a client menu session. */
    public PatternEncodingRankingContext {
        if (recipeScope == null || recipeScope.isBlank()) {
            throw new IllegalArgumentException("Pattern ranking recipe scope must not be blank");
        }
        if (recipeScope.getBytes(StandardCharsets.UTF_8).length > MAX_RECIPE_SCOPE_BYTES) {
            throw new IllegalArgumentException("Pattern ranking recipe scope exceeds " + MAX_RECIPE_SCOPE_BYTES + " UTF-8 bytes");
        }
        String recipeId = recipeScope.startsWith(TYPE_PREFIX) ? recipeScope.substring(TYPE_PREFIX.length()) : recipeScope.startsWith(RECIPE_PREFIX) ? recipeScope.substring(RECIPE_PREFIX.length()) : null;
        if (recipeId == null || ResourceLocation.tryParse(recipeId) == null) {
            throw new IllegalArgumentException("Invalid pattern ranking recipe scope: " + recipeScope);
        }
        if (workstation == null) {
            throw new IllegalArgumentException("Pattern ranking workstation must not be null");
        }
        if (workstation.toString().getBytes(StandardCharsets.UTF_8).length > MAX_WORKSTATION_BYTES) {
            throw new IllegalArgumentException("Pattern ranking workstation exceeds " + MAX_WORKSTATION_BYTES + " UTF-8 bytes");
        }
    }

    /** Builds the preferred recipe-type scope. */
    public static PatternEncodingRankingContext forRecipeType(ResourceLocation recipeTypeId,
                                                              ResourceLocation workstation) {
        if (recipeTypeId == null) {
            throw new IllegalArgumentException("Recipe type id must not be null");
        }
        return new PatternEncodingRankingContext(TYPE_PREFIX + recipeTypeId, workstation);
    }

    /** Builds the recipe-id fallback scope used when no registered recipe type can be resolved. */
    public static PatternEncodingRankingContext forRecipe(ResourceLocation recipeId, ResourceLocation workstation) {
        if (recipeId == null) {
            throw new IllegalArgumentException("Recipe id must not be null");
        }
        return new PatternEncodingRankingContext(RECIPE_PREFIX + recipeId, workstation);
    }
}
