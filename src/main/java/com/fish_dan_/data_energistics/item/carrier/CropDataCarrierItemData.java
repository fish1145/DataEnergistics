package com.fish_dan_.data_energistics.item.carrier;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record CropDataCarrierItemData(ResourceLocation cropItem, Optional<ResourceLocation> sourceBlock,
                                      Optional<ResourceLocation> lootTable, float requiredAmount,
                                      float collectedAmount) {

    public static final Codec<CropDataCarrierItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("crop_item").forGetter(CropDataCarrierItemData::cropItem),
            ResourceLocation.CODEC.optionalFieldOf("source_block").forGetter(CropDataCarrierItemData::sourceBlock),
            ResourceLocation.CODEC.optionalFieldOf("loot_table").forGetter(CropDataCarrierItemData::lootTable),
            Codec.FLOAT.optionalFieldOf("required_amount", 0.0F).forGetter(CropDataCarrierItemData::requiredAmount),
            Codec.FLOAT.optionalFieldOf("collected_amount", 0.0F).forGetter(CropDataCarrierItemData::collectedAmount))
            .apply(instance, CropDataCarrierItemData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CropDataCarrierItemData> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            CropDataCarrierItemData::cropItem,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
            CropDataCarrierItemData::sourceBlock,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
            CropDataCarrierItemData::lootTable,
            ByteBufCodecs.FLOAT,
            CropDataCarrierItemData::requiredAmount,
            ByteBufCodecs.FLOAT,
            CropDataCarrierItemData::collectedAmount,
            CropDataCarrierItemData::new);

    public CropDataCarrierItemData withRequiredAmount(float requiredAmount) {
        float clampedRequired = Math.max(1.0F, requiredAmount);
        return new CropDataCarrierItemData(this.cropItem, this.sourceBlock, this.lootTable, clampedRequired, Math.min(Math.max(0.0F, this.collectedAmount), clampedRequired));
    }

    public CropDataCarrierItemData withAddedCollectedAmount(float amount) {
        return new CropDataCarrierItemData(this.cropItem, this.sourceBlock, this.lootTable, this.requiredAmount, Math.min(Math.max(0.0F, this.collectedAmount + amount), Math.max(1.0F, this.requiredAmount)));
    }

    public CropDataCarrierItemData asComplete() {
        return new CropDataCarrierItemData(this.cropItem, this.sourceBlock, this.lootTable, this.requiredAmount, Math.max(this.collectedAmount, this.requiredAmount));
    }
}
