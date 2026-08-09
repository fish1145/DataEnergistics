package com.fish_dan_.data_energistics.recipe.captureball;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.mojang.serialization.MapCodec;

public final class DataCaptureBallRightClickRecipeSerializer
                                                             implements RecipeSerializer<DataCaptureBallRightClickRecipe> {

    @Override
    public MapCodec<DataCaptureBallRightClickRecipe> codec() {
        return DataCaptureBallRightClickRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, DataCaptureBallRightClickRecipe> streamCodec() {
        return DataCaptureBallRightClickRecipe.STREAM_CODEC;
    }
}
