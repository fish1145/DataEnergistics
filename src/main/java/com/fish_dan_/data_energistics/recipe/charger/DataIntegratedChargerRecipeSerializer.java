package com.fish_dan_.data_energistics.recipe.charger;

import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressIngredient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** Codec for the multi-input data integrated charger inscribing recipe schema. */
public final class DataIntegratedChargerRecipeSerializer implements RecipeSerializer<DataIntegratedChargerRecipe> {

    private static final Codec<List<DataChargePressIngredient>> INPUTS_CODEC = DataChargePressIngredient.CODEC.listOf().flatXmap(
            ingredients -> ingredients.isEmpty() || ingredients.size() > DataIntegratedChargerRecipe.MAX_ITEM_INPUT_COUNT ?
                    DataResult.error(() -> "Data integrated charger recipes require between " +
                            DataIntegratedChargerRecipe.MIN_ITEM_INPUT_COUNT + " and " +
                            DataIntegratedChargerRecipe.MAX_ITEM_INPUT_COUNT + " inputs") :
                    DataResult.success(ingredients),
            DataResult::success);
    private static final Codec<ItemStack> RESULT_CODEC = ItemStack.CODEC.flatXmap(
            stack -> stack.isEmpty() ? DataResult.error(() -> "Data integrated charger result must not be empty") :
                    DataResult.success(stack),
            DataResult::success);
    private static final MapCodec<DataIntegratedChargerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            INPUTS_CODEC.fieldOf("inputs").forGetter(DataIntegratedChargerRecipe::getInputs),
            RESULT_CODEC.fieldOf("result").forGetter(DataIntegratedChargerRecipe::getResult))
            .apply(instance, DataIntegratedChargerRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, DataIntegratedChargerRecipe> STREAM_CODEC = StreamCodec.of(
            DataIntegratedChargerRecipeSerializer::writeRecipe,
            DataIntegratedChargerRecipeSerializer::readRecipe);

    private static DataIntegratedChargerRecipe readRecipe(RegistryFriendlyByteBuf buffer) {
        List<DataChargePressIngredient> inputs = DataChargePressIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer);
        ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
        return new DataIntegratedChargerRecipe(inputs, result);
    }

    private static void writeRecipe(RegistryFriendlyByteBuf buffer, DataIntegratedChargerRecipe recipe) {
        DataChargePressIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, recipe.getInputs());
        ItemStack.STREAM_CODEC.encode(buffer, recipe.getResult());
    }

    @Override
    public MapCodec<DataIntegratedChargerRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, DataIntegratedChargerRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
