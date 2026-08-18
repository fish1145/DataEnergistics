package com.fish_dan_.data_energistics.recipe.chargepress;

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

/** Codec for the compact inputs/fluid_amount/result data charge press schema. */
public final class DataChargePressRecipeSerializer implements RecipeSerializer<DataChargePressRecipe> {

    private static final Codec<List<DataChargePressIngredient>> INPUTS_CODEC = DataChargePressIngredient.CODEC.listOf().flatXmap(
            ingredients -> ingredients.isEmpty() ||
                    ingredients.size() > DataChargePressRecipe.MAX_ITEM_INPUT_COUNT ?
                            DataResult.error(() -> "Data charge press recipes require between " + DataChargePressRecipe.MIN_ITEM_INPUT_COUNT + " and " +
                                    DataChargePressRecipe.MAX_ITEM_INPUT_COUNT + " inputs") :
                            DataResult.success(ingredients),
            DataResult::success);
    private static final Codec<Integer> FLUID_AMOUNT_CODEC = Codec.INT.flatXmap(
            amount -> amount <= 0 || amount > DataChargePressRecipe.MAX_FLUID_AMOUNT ?
                    DataResult.error(() -> "Data charge press fluid_amount must be between 1 and " +
                            DataChargePressRecipe.MAX_FLUID_AMOUNT) :
                    DataResult.success(amount),
            DataResult::success);
    private static final Codec<ItemStack> RESULT_CODEC = ItemStack.CODEC.flatXmap(
            stack -> stack.isEmpty() ? DataResult.error(() -> "Data charge press result must not be empty") : DataResult.success(stack),
            DataResult::success);
    private static final MapCodec<DataChargePressRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            INPUTS_CODEC.fieldOf("inputs").forGetter(DataChargePressRecipe::getInputs),
            FLUID_AMOUNT_CODEC.fieldOf("fluid_amount").forGetter(DataChargePressRecipe::getFluidAmount),
            RESULT_CODEC.fieldOf("result").forGetter(DataChargePressRecipe::getResult))
            .apply(instance, DataChargePressRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, DataChargePressRecipe> STREAM_CODEC = StreamCodec.of(
            DataChargePressRecipeSerializer::writeRecipe, DataChargePressRecipeSerializer::readRecipe);

    private static DataChargePressRecipe readRecipe(RegistryFriendlyByteBuf buffer) {
        List<DataChargePressIngredient> inputs = DataChargePressIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer);
        int fluidAmount = ByteBufCodecs.VAR_INT.decode(buffer);
        ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
        return new DataChargePressRecipe(inputs, fluidAmount, result);
    }

    private static void writeRecipe(RegistryFriendlyByteBuf buffer, DataChargePressRecipe recipe) {
        DataChargePressIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, recipe.getInputs());
        ByteBufCodecs.VAR_INT.encode(buffer, recipe.getFluidAmount());
        ItemStack.STREAM_CODEC.encode(buffer, recipe.getResult());
    }

    @Override
    public MapCodec<DataChargePressRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, DataChargePressRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
