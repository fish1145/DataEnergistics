package com.fish_dan_.data_energistics.recipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DataRipperReassemblerIngredient(Ingredient ingredient, int count) {

    private static final Codec<Integer> COUNT_CODEC = Codec.INT.flatXmap(
            count -> count <= 0 ? DataResult.error(() -> "Data rippper reassembler ingredient count must be greater than 0") : DataResult.success(count),
            DataResult::success);

    public static final MapCodec<DataRipperReassemblerIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(DataRipperReassemblerIngredient::ingredient),
            COUNT_CODEC.fieldOf("count").forGetter(DataRipperReassemblerIngredient::count)).apply(instance, DataRipperReassemblerIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DataRipperReassemblerIngredient> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            DataRipperReassemblerIngredient::ingredient,
            ByteBufCodecs.VAR_INT,
            DataRipperReassemblerIngredient::count,
            DataRipperReassemblerIngredient::new);
}
