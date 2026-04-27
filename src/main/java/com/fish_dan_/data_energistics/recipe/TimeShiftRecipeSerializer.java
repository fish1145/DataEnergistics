package com.fish_dan_.data_energistics.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class TimeShiftRecipeSerializer implements RecipeSerializer<TimeShiftRecipe> {
    private static final Codec<List<Ingredient>> INGREDIENTS_CODEC = Ingredient.CODEC_NONEMPTY.listOf()
            .flatXmap(
                    ingredients -> ingredients.isEmpty()
                            ? DataResult.error(() -> "Time shift recipe must have at least one ingredient")
                            : DataResult.success(ingredients),
                    DataResult::success);
    private static final Codec<List<ItemStack>> RESULTS_CODEC = ItemStack.CODEC.listOf()
            .flatXmap(
                    results -> results.isEmpty()
                            ? DataResult.error(() -> "Time shift recipe must have at least one result")
                            : DataResult.success(results),
                    DataResult::success);
    private static final Codec<Double> MINUTES_CODEC = Codec.DOUBLE
            .flatXmap(
                    minutes -> minutes <= 0.0D
                            ? DataResult.error(() -> "Time shift minutes must be greater than 0")
                            : DataResult.success(minutes),
                    DataResult::success);

    private static final MapCodec<TimeShiftRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            INGREDIENTS_CODEC.fieldOf("ingredients").forGetter(recipe -> recipe.getIngredients()),
            RESULTS_CODEC.fieldOf("results").forGetter(TimeShiftRecipe::getResults),
            MINUTES_CODEC.fieldOf("minutes").forGetter(TimeShiftRecipe::getDurationMinutes),
            TimeShiftTimeCondition.CODEC.optionalFieldOf("time", TimeShiftTimeCondition.ALL).forGetter(TimeShiftRecipe::getTimeCondition)
    ).apply(instance, TimeShiftRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, TimeShiftRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
            recipe -> recipe.getIngredients(),
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
            TimeShiftRecipe::getResults,
            ByteBufCodecs.VAR_INT,
            TimeShiftRecipe::getDurationTicks,
            TimeShiftTimeCondition.STREAM_CODEC,
            TimeShiftRecipe::getTimeCondition,
            TimeShiftRecipe::new);

    @Override
    public MapCodec<TimeShiftRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, TimeShiftRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
