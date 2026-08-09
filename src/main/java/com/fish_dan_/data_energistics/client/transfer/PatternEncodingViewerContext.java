package com.fish_dan_.data_energistics.client.transfer;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.parts.encoding.EncodingMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a viewer context from the stable recipe type supplied by a recipe viewer.
 */
public final class PatternEncodingViewerContext {

    private PatternEncodingViewerContext() {}

    /**
     * Resolves the mode that AE2's viewer transfer will request before the asynchronously synchronized menu field
     * reflects that request.
     */
    public static @NotNull EncodingMode resolveEncodingMode(@Nullable Recipe<?> recipe, boolean craftingCategory) {
        if (recipe == null || (!craftingCategory && !EncodingHelper.isSupportedCraftingRecipe(recipe))) {
            return EncodingMode.PROCESSING;
        }
        if (recipe.getType() == RecipeType.STONECUTTING) {
            return EncodingMode.STONECUTTING;
        }
        if (recipe.getType() == RecipeType.SMITHING) {
            return EncodingMode.SMITHING_TABLE;
        }
        return EncodingMode.CRAFTING;
    }

    /**
     * Captures a viewer recipe type without trusting viewer workstation or catalyst lists.
     */
    public static @NotNull PatternEncodingRankingContext fromRecipeType(
                                                                        @NotNull ResourceLocation recipeTypeId) {
        return PatternEncodingRankingContext.of(recipeTypeId);
    }
}
