package com.fish_dan_.data_energistics.ae2.settings;

import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipe;
import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipeCatalog;

import appeng.api.config.CondenserOutput;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Maps the condenser's three built-in modes and data-driven custom recipes to one stable UI index. */
public final class CondenserOutputMode {

    public static final int TRASH = 0;
    public static final int MATTER_BALLS = 1;
    public static final int SINGULARITY = 2;
    public static final int CUSTOM_START = 3;

    private CondenserOutputMode() {}

    public static int getModeCount(@Nullable Level level) {
        return CUSTOM_START + (level == null ? 0 : CondenserOutputRecipeCatalog.getRecipes(level).size());
    }

    public static int fromState(CondenserOutput output, @Nullable ResourceLocation customRecipeId,
                                @Nullable Level level) {
        List<ResourceLocation> recipeIds = level == null ? List.of() : CondenserOutputRecipeCatalog.getRecipes(level).stream().map(RecipeHolder::id).toList();
        return fromState(output, customRecipeId, recipeIds);
    }

    static int fromState(CondenserOutput output, @Nullable ResourceLocation customRecipeId,
                         List<ResourceLocation> customRecipeIds) {
        int customIndex = customRecipeId == null ? -1 : customRecipeIds.indexOf(customRecipeId);
        if (customIndex >= 0) {
            return CUSTOM_START + customIndex;
        }

        return switch (output) {
            case MATTER_BALLS -> MATTER_BALLS;
            case SINGULARITY -> SINGULARITY;
            default -> TRASH;
        };
    }

    public static Selection resolve(Level level, int modeIndex) {
        if (modeIndex == MATTER_BALLS) {
            return new Selection(CondenserOutput.MATTER_BALLS, null);
        }
        if (modeIndex == SINGULARITY) {
            return new Selection(CondenserOutput.SINGULARITY, null);
        }

        RecipeHolder<CondenserOutputRecipe> customRecipe = getCustomRecipe(level, modeIndex);
        return customRecipe == null ? new Selection(CondenserOutput.TRASH, null) : new Selection(CondenserOutput.SINGULARITY, customRecipe.id());
    }

    @Nullable
    public static RecipeHolder<CondenserOutputRecipe> getCustomRecipe(@Nullable Level level, int modeIndex) {
        if (level == null || modeIndex < CUSTOM_START) {
            return null;
        }
        List<RecipeHolder<CondenserOutputRecipe>> recipes = CondenserOutputRecipeCatalog.getRecipes(level);
        int recipeIndex = modeIndex - CUSTOM_START;
        return recipeIndex < recipes.size() ? recipes.get(recipeIndex) : null;
    }

    @Nullable
    static ResourceLocation getCustomRecipeId(List<ResourceLocation> customRecipeIds, int modeIndex) {
        int recipeIndex = modeIndex - CUSTOM_START;
        return recipeIndex >= 0 && recipeIndex < customRecipeIds.size() ? customRecipeIds.get(recipeIndex) : null;
    }

    /** One validated mode selection; custom recipes use AE2's singularity setting as their underlying trigger. */
    public record Selection(CondenserOutput vanillaOutput, @Nullable ResourceLocation customRecipeId) {}
}
