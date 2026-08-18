package com.fish_dan_.data_energistics.recipe.chargepress;

import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** Codec for the data integrated charger's one-to-three-item, one-fluid recipe format. */
public final class DataChargePressRecipeSerializer implements RecipeSerializer<DataChargePressRecipe> {

    private static final Codec<List<DataChargePressIngredient>> ITEM_INPUTS_CODEC = DataChargePressIngredient.CODEC.codec().listOf().flatXmap(
            ingredients -> ingredients.size() < DataChargePressRecipe.MIN_ITEM_INPUT_COUNT ||
                    ingredients.size() > DataChargePressRecipe.MAX_ITEM_INPUT_COUNT ?
                            DataResult.error(() -> "Data charge press recipes require between " + DataChargePressRecipe.MIN_ITEM_INPUT_COUNT + " and " +
                                    DataChargePressRecipe.MAX_ITEM_INPUT_COUNT + " item inputs") :
                            DataResult.success(ingredients),
            DataResult::success);
    private static final Codec<GenericStack> FLUID_INPUT_CODEC = GenericStack.CODEC.flatXmap(
            stack -> stack.amount() <= 0 || stack.amount() > DataIntegratedChargerBlockEntity.FLUID_CAPACITY ||
                    !(stack.what() instanceof AEFluidKey) ? DataResult.error(() -> "Data charge press fluid_input must be a positive fluid stack within the tank capacity") : DataResult.success(stack),
            DataResult::success);
    private static final Codec<ItemStack> RESULT_CODEC = ItemStack.CODEC.flatXmap(
            stack -> stack.isEmpty() ? DataResult.error(() -> "Data charge press result must not be empty") : DataResult.success(stack),
            DataResult::success);
    private static final MapCodec<DataChargePressRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ITEM_INPUTS_CODEC.fieldOf("item_inputs").forGetter(DataChargePressRecipe::getItemInputs),
            FLUID_INPUT_CODEC.fieldOf("fluid_input").forGetter(DataChargePressRecipe::getFluidInput),
            Ingredient.CODEC_NONEMPTY.fieldOf("catalyst").forGetter(DataChargePressRecipe::getCatalyst),
            RESULT_CODEC.fieldOf("result").forGetter(DataChargePressRecipe::getResult))
            .apply(instance, DataChargePressRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, DataChargePressRecipe> STREAM_CODEC = StreamCodec.of(
            DataChargePressRecipeSerializer::writeRecipe, DataChargePressRecipeSerializer::readRecipe);

    private static DataChargePressRecipe readRecipe(RegistryFriendlyByteBuf buffer) {
        List<DataChargePressIngredient> itemInputs = DataChargePressIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer);
        GenericStack fluidInput = GenericStack.readBuffer(buffer);
        Ingredient catalyst = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
        return new DataChargePressRecipe(itemInputs, fluidInput, catalyst, result);
    }

    private static void writeRecipe(RegistryFriendlyByteBuf buffer, DataChargePressRecipe recipe) {
        DataChargePressIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, recipe.getItemInputs());
        GenericStack.writeBuffer(recipe.getFluidInput(), buffer);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.getCatalyst());
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
