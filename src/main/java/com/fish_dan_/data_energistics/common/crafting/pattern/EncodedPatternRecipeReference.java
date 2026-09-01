package com.fish_dan_.data_energistics.common.crafting.pattern;

import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.ids.AEComponents;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the persistent recipe identity carried by one encoded pattern.
 */
public record EncodedPatternRecipeReference(Kind kind, ResourceLocation id) {

    /** Writes or removes the processing recipe type beside the pattern's output-match metadata. */
    public static void applyProcessingRecipeType(
                                                 ItemStack encodedPattern,
                                                 @Nullable ResourceLocation recipeTypeId) {
        if (encodedPattern.isEmpty()) {
            throw new IllegalArgumentException("Cannot mark an empty encoded pattern");
        }
        if (recipeTypeId == null) {
            encodedPattern.remove(DEDataComponents.PROCESSING_PATTERN_RECIPE_TYPE);
        } else {
            encodedPattern.set(DEDataComponents.PROCESSING_PATTERN_RECIPE_TYPE, recipeTypeId);
        }
    }

    /**
     * Returns the concrete recipe ID already encoded by AE2, or the recorded processing recipe-type ID.
     */
    public static @Nullable EncodedPatternRecipeReference get(ItemStack encodedPattern) {
        if (encodedPattern.isEmpty()) {
            return null;
        }
        var crafting = encodedPattern.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) {
            return new EncodedPatternRecipeReference(Kind.RECIPE, crafting.recipeId());
        }
        var smithing = encodedPattern.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null) {
            return new EncodedPatternRecipeReference(Kind.RECIPE, smithing.recipeId());
        }
        var stonecutting = encodedPattern.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stonecutting != null) {
            return new EncodedPatternRecipeReference(Kind.RECIPE, stonecutting.recipeId());
        }
        ResourceLocation recipeTypeId = encodedPattern.get(DEDataComponents.PROCESSING_PATTERN_RECIPE_TYPE);
        return recipeTypeId == null ? null : new EncodedPatternRecipeReference(Kind.RECIPE_TYPE, recipeTypeId);
    }

    public enum Kind {
        RECIPE,
        RECIPE_TYPE
    }
}
