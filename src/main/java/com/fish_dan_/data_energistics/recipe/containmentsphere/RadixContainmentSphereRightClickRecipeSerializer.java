package com.fish_dan_.data_energistics.recipe.containmentsphere;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.mojang.serialization.MapCodec;

public final class RadixContainmentSphereRightClickRecipeSerializer
                                                                    implements RecipeSerializer<RadixContainmentSphereRightClickRecipe> {

    @Override
    public MapCodec<RadixContainmentSphereRightClickRecipe> codec() {
        return RadixContainmentSphereRightClickRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, RadixContainmentSphereRightClickRecipe> streamCodec() {
        return RadixContainmentSphereRightClickRecipe.STREAM_CODEC;
    }
}
