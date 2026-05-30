package com.fish_dan_.data_energistics.recipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TimeShiftIngredient(Ingredient ingredient, int count) {

    private static final Codec<Integer> COUNT_CODEC = Codec.INT.flatXmap(
            count -> count <= 0 ? DataResult.error(() -> "Time shift ingredient count must be greater than 0") : DataResult.success(count),
            DataResult::success);

    public static final MapCodec<TimeShiftIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(TimeShiftIngredient::ingredient),
            COUNT_CODEC.optionalFieldOf("count", 1).forGetter(TimeShiftIngredient::count)).apply(instance, TimeShiftIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TimeShiftIngredient> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            TimeShiftIngredient::ingredient,
            ByteBufCodecs.VAR_INT,
            TimeShiftIngredient::count,
            TimeShiftIngredient::new);
}
