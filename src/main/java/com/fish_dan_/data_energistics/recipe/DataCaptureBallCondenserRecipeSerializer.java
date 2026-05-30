package com.fish_dan_.data_energistics.recipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.mojang.serialization.MapCodec;

public final class DataCaptureBallCondenserRecipeSerializer implements RecipeSerializer<DataCaptureBallCondenserRecipe> {

    @Override
    public MapCodec<DataCaptureBallCondenserRecipe> codec() {
        return DataCaptureBallCondenserRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, DataCaptureBallCondenserRecipe> streamCodec() {
        return DataCaptureBallCondenserRecipe.STREAM_CODEC;
    }
}
