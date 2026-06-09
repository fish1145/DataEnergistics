package com.fish_dan_.data_energistics.item;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DigitalStorageDepotMemoryCardData(int itemOutputSidesMask, int fluidOutputSidesMask, int keyOutputSidesMask) {

    public static final int DEFAULT_MASK = 63;
    public static final DigitalStorageDepotMemoryCardData DEFAULT = new DigitalStorageDepotMemoryCardData(DEFAULT_MASK, DEFAULT_MASK, DEFAULT_MASK);

    public static final Codec<DigitalStorageDepotMemoryCardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("item_output_sides_mask", DEFAULT_MASK).forGetter(DigitalStorageDepotMemoryCardData::itemOutputSidesMask),
            Codec.INT.optionalFieldOf("fluid_output_sides_mask", DEFAULT_MASK).forGetter(DigitalStorageDepotMemoryCardData::fluidOutputSidesMask),
            Codec.INT.optionalFieldOf("key_output_sides_mask", DEFAULT_MASK).forGetter(DigitalStorageDepotMemoryCardData::keyOutputSidesMask))
            .apply(instance, DigitalStorageDepotMemoryCardData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DigitalStorageDepotMemoryCardData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            DigitalStorageDepotMemoryCardData::itemOutputSidesMask,
            ByteBufCodecs.VAR_INT,
            DigitalStorageDepotMemoryCardData::fluidOutputSidesMask,
            ByteBufCodecs.VAR_INT,
            DigitalStorageDepotMemoryCardData::keyOutputSidesMask,
            DigitalStorageDepotMemoryCardData::new);
}
