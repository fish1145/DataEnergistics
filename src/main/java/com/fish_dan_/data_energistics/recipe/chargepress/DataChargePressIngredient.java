package com.fish_dan_.data_energistics.recipe.chargepress;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.stream.Stream;

/** A charge press item input and the number of items consumed from its matching slot. */
public record DataChargePressIngredient(Ingredient ingredient, int count) {

    private static final Codec<Integer> COUNT_CODEC = Codec.INT.flatXmap(
            count -> count <= 0 ? DataResult.error(() -> "Data charge press ingredient count must be greater than 0") : DataResult.success(count),
            DataResult::success);

    private static final MapCodec<Ingredient> FLAT_INGREDIENT_CODEC = Ingredient.Value.MAP_CODEC.flatXmap(
            value -> DataResult.success(Ingredient.fromValues(Stream.of(value))),
            ingredient -> {
                if (ingredient.isCustom()) {
                    return DataResult.error(() -> "Custom data charge press ingredients must use the ingredient wrapper");
                }
                Ingredient.Value[] values = ingredient.getValues();
                if (values.length != 1) {
                    return DataResult.error(() -> "Compound data charge press ingredients must use the ingredient wrapper");
                }
                return DataResult.success(values[0]);
            });
    private static final MapCodec<DataChargePressIngredient> FLAT_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FLAT_INGREDIENT_CODEC.forGetter(DataChargePressIngredient::ingredient),
            COUNT_CODEC.optionalFieldOf("count", 1).forGetter(DataChargePressIngredient::count))
            .apply(instance, DataChargePressIngredient::new));
    private static final MapCodec<DataChargePressIngredient> WRAPPED_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(DataChargePressIngredient::ingredient),
            COUNT_CODEC.optionalFieldOf("count", 1).forGetter(DataChargePressIngredient::count))
            .apply(instance, DataChargePressIngredient::new));
    public static final Codec<DataChargePressIngredient> CODEC = Codec.either(FLAT_CODEC.codec(), WRAPPED_CODEC.codec())
            .xmap(either -> either.map(input -> input, input -> input), input -> usesWrappedFormat(input.ingredient()) ? Either.right(input) : Either.left(input));
    public static final StreamCodec<RegistryFriendlyByteBuf, DataChargePressIngredient> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            DataChargePressIngredient::ingredient,
            ByteBufCodecs.VAR_INT,
            DataChargePressIngredient::count,
            DataChargePressIngredient::new);

    public DataChargePressIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException("Data charge press ingredient must not be empty");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Data charge press ingredient count must be greater than 0: " + count);
        }
    }

    private static boolean usesWrappedFormat(Ingredient ingredient) {
        return ingredient.isCustom() || ingredient.getValues().length != 1;
    }
}
