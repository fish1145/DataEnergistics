package com.fish_dan_.data_energistics.recipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public final class DataChargerRecipeSerializer implements RecipeSerializer<DataChargerRecipe> {

    private static final Codec<ItemStack> RESULT_CODEC = ItemStack.CODEC.flatXmap(
            stack -> stack.isEmpty() ? DataResult.error(() -> "Data charger recipe result must not be empty") : DataResult.success(stack),
            DataResult::success);
    private static final Codec<Long> DATA_FLOW_CODEC = Codec.LONG.flatXmap(
            dataFlow -> dataFlow <= 0L ? DataResult.error(() -> "Data charger recipe data_flow must be greater than 0") : DataResult.success(dataFlow),
            DataResult::success);
    private static final Codec<Double> POWER_CODEC = Codec.DOUBLE.flatXmap(
            power -> power <= 0.0D ? DataResult.error(() -> "Data charger recipe power must be greater than 0") : DataResult.success(power),
            DataResult::success);

    private static final MapCodec<DataChargerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(DataChargerRecipe::getIngredient),
            RESULT_CODEC.fieldOf("result").forGetter(DataChargerRecipe::getResult),
            DATA_FLOW_CODEC.fieldOf("data_flow").forGetter(DataChargerRecipe::getDataFlow),
            POWER_CODEC.fieldOf("power").forGetter(DataChargerRecipe::getPower))
            .apply(instance, DataChargerRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, DataChargerRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            DataChargerRecipe::getIngredient,
            ItemStack.STREAM_CODEC,
            DataChargerRecipe::getResult,
            ByteBufCodecs.VAR_LONG,
            DataChargerRecipe::getDataFlow,
            ByteBufCodecs.DOUBLE,
            DataChargerRecipe::getPower,
            DataChargerRecipe::new);

    @Override
    public MapCodec<DataChargerRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, DataChargerRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
