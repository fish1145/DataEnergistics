package com.fish_dan_.data_energistics.item.depot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DigitalStorageDepotItemData(int selectedFluidSlot, int selectedKeySlot, int markMode,
                                          boolean bucketMode) {

    public static final DigitalStorageDepotItemData DEFAULT = new DigitalStorageDepotItemData(0, 0, 0, false);

    public static final Codec<DigitalStorageDepotItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("selected_fluid_slot", 0).forGetter(DigitalStorageDepotItemData::selectedFluidSlot),
            Codec.INT.optionalFieldOf("selected_key_slot", 0).forGetter(DigitalStorageDepotItemData::selectedKeySlot),
            Codec.INT.optionalFieldOf("mark_mode", 0).forGetter(DigitalStorageDepotItemData::markMode),
            Codec.BOOL.optionalFieldOf("bucket_mode", false).forGetter(DigitalStorageDepotItemData::bucketMode)).apply(instance, DigitalStorageDepotItemData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DigitalStorageDepotItemData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            DigitalStorageDepotItemData::selectedFluidSlot,
            ByteBufCodecs.VAR_INT,
            DigitalStorageDepotItemData::selectedKeySlot,
            ByteBufCodecs.VAR_INT,
            DigitalStorageDepotItemData::markMode,
            ByteBufCodecs.BOOL,
            DigitalStorageDepotItemData::bucketMode,
            DigitalStorageDepotItemData::new);

    public DigitalStorageDepotItemData withSelectedFluidSlot(int selectedFluidSlot) {
        return new DigitalStorageDepotItemData(selectedFluidSlot, this.selectedKeySlot, this.markMode, this.bucketMode);
    }

    public DigitalStorageDepotItemData withSelectedKeySlot(int selectedKeySlot) {
        return new DigitalStorageDepotItemData(this.selectedFluidSlot, selectedKeySlot, this.markMode, this.bucketMode);
    }

    public DigitalStorageDepotItemData withMarkMode(int markMode) {
        return new DigitalStorageDepotItemData(this.selectedFluidSlot, this.selectedKeySlot, markMode, this.bucketMode);
    }

    public DigitalStorageDepotItemData withBucketMode(boolean bucketMode) {
        return new DigitalStorageDepotItemData(this.selectedFluidSlot, this.selectedKeySlot, this.markMode, bucketMode);
    }
}
