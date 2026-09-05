package com.fish_dan_.data_energistics.common.crafting.pattern;

import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.ids.AEComponents;
import appeng.parts.encoding.EncodingMode;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * Resolves the persistent recipe identity carried by one encoded pattern.
 */
public record EncodedPatternRecipeReference(Kind kind, ResourceLocation id) {

    private static final ResourceLocation CRAFTING_RECIPE_TYPE = ResourceLocation.withDefaultNamespace("crafting");
    private static final ResourceLocation SMITHING_RECIPE_TYPE = ResourceLocation.withDefaultNamespace("smithing");
    private static final ResourceLocation STONECUTTING_RECIPE_TYPE = ResourceLocation.withDefaultNamespace(
            "stonecutting");

    /** Atomically replaces the optional processing recipe type and stable recipe identity. */
    public static void applyProcessingRecipeMetadata(ItemStack encodedPattern,
                                                     @Nullable ResourceLocation recipeTypeId,
                                                     @Nullable ResourceLocation recipeId) {
        if (encodedPattern.isEmpty()) {
            throw new IllegalArgumentException("Cannot mark an empty encoded pattern");
        }
        if (recipeTypeId == null) {
            encodedPattern.remove(DEDataComponents.PROCESSING_PATTERN_RECIPE_TYPE);
        } else {
            encodedPattern.set(DEDataComponents.PROCESSING_PATTERN_RECIPE_TYPE, recipeTypeId);
        }
        if (recipeId == null) {
            encodedPattern.remove(DEDataComponents.PROCESSING_PATTERN_RECIPE_ID);
        } else {
            encodedPattern.set(DEDataComponents.PROCESSING_PATTERN_RECIPE_ID, recipeId);
        }
    }

    /** Returns the stable processing recipe identity appended by a successful viewer transfer, if present. */
    public static @Nullable ResourceLocation getProcessingRecipeId(ItemStack encodedPattern) {
        return encodedPattern.isEmpty() ? null :
                encodedPattern.get(DEDataComponents.PROCESSING_PATTERN_RECIPE_ID);
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
            return new EncodedPatternRecipeReference(Kind.CRAFTING_RECIPE, crafting.recipeId());
        }
        var smithing = encodedPattern.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null) {
            return new EncodedPatternRecipeReference(Kind.SMITHING_RECIPE, smithing.recipeId());
        }
        var stonecutting = encodedPattern.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stonecutting != null) {
            return new EncodedPatternRecipeReference(Kind.STONECUTTING_RECIPE, stonecutting.recipeId());
        }
        ResourceLocation recipeTypeId = encodedPattern.get(DEDataComponents.PROCESSING_PATTERN_RECIPE_TYPE);
        return recipeTypeId == null ? null :
                new EncodedPatternRecipeReference(Kind.PROCESSING_RECIPE_TYPE, recipeTypeId);
    }

    /** Returns the recipe-viewer type/category used to resolve names and workstations lazily. */
    public ResourceLocation recipeTypeId() {
        return switch (this.kind) {
            case CRAFTING_RECIPE -> CRAFTING_RECIPE_TYPE;
            case SMITHING_RECIPE -> SMITHING_RECIPE_TYPE;
            case STONECUTTING_RECIPE -> STONECUTTING_RECIPE_TYPE;
            case PROCESSING_RECIPE_TYPE -> this.id;
        };
    }

    /**
     * Resolves editor mode from the pattern's actual AE encoding component, independently of DE recipe metadata.
     */
    public static @Nullable EncodingMode encodingMode(ItemStack encodedPattern) {
        if (encodedPattern.has(AEComponents.ENCODED_CRAFTING_PATTERN)) {
            return EncodingMode.CRAFTING;
        }
        if (encodedPattern.has(AEComponents.ENCODED_SMITHING_TABLE_PATTERN)) {
            return EncodingMode.SMITHING_TABLE;
        }
        if (encodedPattern.has(AEComponents.ENCODED_STONECUTTING_PATTERN)) {
            return EncodingMode.STONECUTTING;
        }
        return encodedPattern.has(AEComponents.ENCODED_PROCESSING_PATTERN) ? EncodingMode.PROCESSING : null;
    }

    public enum Kind {
        CRAFTING_RECIPE,
        SMITHING_RECIPE,
        STONECUTTING_RECIPE,
        PROCESSING_RECIPE_TYPE
    }
}
