package com.fish_dan_.data_energistics.accessor.condenser;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

public interface CondenserBlockEntityAccessor {

    @Nullable
    ResourceLocation dataEnergistics$getSelectedCondenserRecipeId();

    void dataEnergistics$setSelectedCondenserRecipeId(@Nullable ResourceLocation recipeId);
}
