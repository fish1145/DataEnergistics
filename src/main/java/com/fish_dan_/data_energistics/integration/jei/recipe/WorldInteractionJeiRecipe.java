package com.fish_dan_.data_energistics.integration.jei.recipe;

import com.fish_dan_.data_energistics.recipe.containmentsphere.RadixContainmentSphereRightClickRecipe;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public sealed interface WorldInteractionJeiRecipe permits WorldInteractionJeiRecipe.TimeShiftView,
                                                  WorldInteractionJeiRecipe.RightClickView {

    ResourceLocation id();

    record TimeShiftView(RecipeHolder<TimeShiftRecipe> holder) implements WorldInteractionJeiRecipe {

        @Override
        public ResourceLocation id() {
            return this.holder.id();
        }
    }

    record RightClickView(RecipeHolder<RadixContainmentSphereRightClickRecipe> holder) implements WorldInteractionJeiRecipe {

        @Override
        public ResourceLocation id() {
            return this.holder.id();
        }
    }
}
