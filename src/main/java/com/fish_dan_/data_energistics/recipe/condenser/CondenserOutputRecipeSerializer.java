package com.fish_dan_.data_energistics.recipe.condenser;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.mojang.serialization.MapCodec;

public final class CondenserOutputRecipeSerializer implements RecipeSerializer<CondenserOutputRecipe> {

    @Override
    public MapCodec<CondenserOutputRecipe> codec() {
        return CondenserOutputRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, CondenserOutputRecipe> streamCodec() {
        return CondenserOutputRecipe.STREAM_CODEC;
    }
}
