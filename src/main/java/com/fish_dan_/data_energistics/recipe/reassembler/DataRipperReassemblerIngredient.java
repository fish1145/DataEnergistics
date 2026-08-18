package com.fish_dan_.data_energistics.recipe.reassembler;

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

public record DataRipperReassemblerIngredient(Ingredient ingredient, int count) {

    private static final Codec<Integer> COUNT_CODEC = Codec.INT.flatXmap(
            count -> count <= 0 ? DataResult.error(() -> "Data rippper reassembler ingredient count must be greater than 0") : DataResult.success(count),
            DataResult::success);

    private static final MapCodec<Ingredient> FLAT_INGREDIENT_CODEC = Ingredient.Value.MAP_CODEC.flatXmap(
            value -> DataResult.success(Ingredient.fromValues(Stream.of(value))),
            ingredient -> {
                if (ingredient.isCustom()) {
                    return DataResult.error(() -> "Custom data reassembler ingredients must use the ingredient wrapper");
                }
                Ingredient.Value[] values = ingredient.getValues();
                if (values.length != 1) {
                    return DataResult.error(() -> "Compound data reassembler ingredients must use the ingredient wrapper");
                }
                return DataResult.success(values[0]);
            });
    private static final MapCodec<DataRipperReassemblerIngredient> FLAT_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FLAT_INGREDIENT_CODEC.forGetter(DataRipperReassemblerIngredient::ingredient),
            COUNT_CODEC.optionalFieldOf("count", 1).forGetter(DataRipperReassemblerIngredient::count))
            .apply(instance, DataRipperReassemblerIngredient::new));
    private static final MapCodec<DataRipperReassemblerIngredient> WRAPPED_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(DataRipperReassemblerIngredient::ingredient),
            COUNT_CODEC.optionalFieldOf("count", 1).forGetter(DataRipperReassemblerIngredient::count))
            .apply(instance, DataRipperReassemblerIngredient::new));
    public static final Codec<DataRipperReassemblerIngredient> CODEC = Codec.either(FLAT_CODEC.codec(), WRAPPED_CODEC.codec())
            .xmap(either -> either.map(input -> input, input -> input), input -> usesWrappedFormat(input.ingredient()) ? Either.right(input) : Either.left(input));

    public static final StreamCodec<RegistryFriendlyByteBuf, DataRipperReassemblerIngredient> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            DataRipperReassemblerIngredient::ingredient,
            ByteBufCodecs.VAR_INT,
            DataRipperReassemblerIngredient::count,
            DataRipperReassemblerIngredient::new);

    public DataRipperReassemblerIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException("Data reassembler ingredient must not be empty");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Data reassembler ingredient count must be greater than 0: " + count);
        }
    }

    private static boolean usesWrappedFormat(Ingredient ingredient) {
        return ingredient.isCustom() || ingredient.getValues().length != 1;
    }
}
