package com.fish_dan_.data_energistics.recipe.chargepress;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A charge press item input and the number of items consumed from its matching slot. */
public record DataChargePressIngredient(Ingredient ingredient, int count) {

    private static final Codec<Integer> COUNT_CODEC = Codec.INT.flatXmap(
            count -> count <= 0 ? DataResult.error(() -> "Data charge press ingredient count must be greater than 0") : DataResult.success(count),
            DataResult::success);

    public static final MapCodec<DataChargePressIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(DataChargePressIngredient::ingredient),
            COUNT_CODEC.optionalFieldOf("count", 1).forGetter(DataChargePressIngredient::count))
            .apply(instance, DataChargePressIngredient::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, DataChargePressIngredient> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            DataChargePressIngredient::ingredient,
            ByteBufCodecs.VAR_INT,
            DataChargePressIngredient::count,
            DataChargePressIngredient::new);
}
