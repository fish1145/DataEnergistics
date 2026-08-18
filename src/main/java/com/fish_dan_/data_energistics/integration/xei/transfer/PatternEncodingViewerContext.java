package com.fish_dan_.data_energistics.integration.xei.transfer;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingViewerRecipeScope;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.parts.encoding.EncodingMode;
import org.jspecify.annotations.Nullable;

/**
 * Builds a viewer context from the stable recipe type supplied by a recipe viewer.
 */
public final class PatternEncodingViewerContext {

    private PatternEncodingViewerContext() {}

    /**
     * Resolves the mode that AE2's viewer transfer will request before the asynchronously synchronized menu field
     * reflects that request.
     */
    public static EncodingMode resolveEncodingMode(@Nullable Recipe<?> recipe, boolean craftingCategory) {
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
     * Captures the viewer recipe type used for learning and its current workstation list used for network matching.
     */
    public static PatternEncodingViewerRecipeScope fromRecipeType(ResourceLocation recipeTypeId,
                                                                  ResourceLocation workstationSourceId) {
        return new PatternEncodingViewerRecipeScope(
                PatternEncodingRankingContext.of(recipeTypeId),
                PatternProviderViewerWorkstations.resolve(workstationSourceId, recipeTypeId));
    }
}
