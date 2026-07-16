package com.fish_dan_.data_energistics.recipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

import com.mojang.serialization.MapCodec;

public final class DataReassemblerCraftingRecipeSerializer implements RecipeSerializer<DataReassemblerCraftingRecipe> {

    private static final MapCodec<DataReassemblerCraftingRecipe> CODEC = ShapedRecipe.Serializer.CODEC
            .xmap(DataReassemblerCraftingRecipe::new, DataReassemblerCraftingRecipe::wrapped);
    private static final StreamCodec<RegistryFriendlyByteBuf, DataReassemblerCraftingRecipe> STREAM_CODEC = ShapedRecipe.Serializer.STREAM_CODEC
            .map(DataReassemblerCraftingRecipe::new, DataReassemblerCraftingRecipe::wrapped);

    @Override
    public MapCodec<DataReassemblerCraftingRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, DataReassemblerCraftingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
