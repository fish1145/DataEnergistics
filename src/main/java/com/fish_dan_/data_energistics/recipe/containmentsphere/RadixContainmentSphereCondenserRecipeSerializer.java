package com.fish_dan_.data_energistics.recipe.containmentsphere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.mojang.serialization.MapCodec;

public final class RadixContainmentSphereCondenserRecipeSerializer implements RecipeSerializer<RadixContainmentSphereCondenserRecipe> {

    @Override
    public MapCodec<RadixContainmentSphereCondenserRecipe> codec() {
        return RadixContainmentSphereCondenserRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, RadixContainmentSphereCondenserRecipe> streamCodec() {
        return RadixContainmentSphereCondenserRecipe.STREAM_CODEC;
    }
}
