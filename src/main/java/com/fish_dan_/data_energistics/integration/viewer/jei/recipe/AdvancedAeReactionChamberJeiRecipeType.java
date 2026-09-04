package com.fish_dan_.data_energistics.integration.viewer.jei.recipe;

import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;

import mezz.jei.api.recipe.RecipeType;

/** Delays the optional Advanced AE recipe class until its mod has been confirmed as loaded. */
public final class AdvancedAeReactionChamberJeiRecipeType {

    private AdvancedAeReactionChamberJeiRecipeType() {}

    public static RecipeType<ReactionChamberRecipe> get() {
        return Holder.TYPE;
    }

    private static final class Holder {

        private static final RecipeType<ReactionChamberRecipe> TYPE = RecipeType.create(
                "advanced_ae",
                "reaction_chamber",
                ReactionChamberRecipe.class);

        private Holder() {}
    }
}
