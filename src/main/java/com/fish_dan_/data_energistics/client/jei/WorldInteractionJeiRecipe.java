package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.recipe.captureball.DataCaptureBallRightClickRecipe;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

sealed interface WorldInteractionJeiRecipe permits WorldInteractionJeiRecipe.TimeShiftView,
                                           WorldInteractionJeiRecipe.RightClickView {

    ResourceLocation id();

    record TimeShiftView(RecipeHolder<TimeShiftRecipe> holder) implements WorldInteractionJeiRecipe {

        @Override
        public ResourceLocation id() {
            return this.holder.id();
        }
    }

    record RightClickView(RecipeHolder<DataCaptureBallRightClickRecipe> holder) implements WorldInteractionJeiRecipe {

        @Override
        public ResourceLocation id() {
            return this.holder.id();
        }
    }
}
