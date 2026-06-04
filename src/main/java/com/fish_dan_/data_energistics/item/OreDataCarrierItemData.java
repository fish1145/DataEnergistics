package com.fish_dan_.data_energistics.item;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record OreDataCarrierItemData(ResourceLocation oreItem, float requiredAmount, float collectedAmount) {

    public static final Codec<OreDataCarrierItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("ore_item").forGetter(OreDataCarrierItemData::oreItem),
            Codec.FLOAT.optionalFieldOf("required_amount", 0.0F).forGetter(OreDataCarrierItemData::requiredAmount),
            Codec.FLOAT.optionalFieldOf("collected_amount", 0.0F).forGetter(OreDataCarrierItemData::collectedAmount))
            .apply(instance, OreDataCarrierItemData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, OreDataCarrierItemData> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            OreDataCarrierItemData::oreItem,
            ByteBufCodecs.FLOAT,
            OreDataCarrierItemData::requiredAmount,
            ByteBufCodecs.FLOAT,
            OreDataCarrierItemData::collectedAmount,
            OreDataCarrierItemData::new);

    public OreDataCarrierItemData withRequiredAmount(float requiredAmount) {
        float clampedRequired = Math.max(1.0F, requiredAmount);
        return new OreDataCarrierItemData(this.oreItem, clampedRequired, Math.min(Math.max(0.0F, this.collectedAmount), clampedRequired));
    }

    public OreDataCarrierItemData withAddedCollectedAmount(float amount) {
        return new OreDataCarrierItemData(this.oreItem, this.requiredAmount, Math.min(Math.max(0.0F, this.collectedAmount + amount), Math.max(1.0F, this.requiredAmount)));
    }

    public OreDataCarrierItemData asComplete() {
        return new OreDataCarrierItemData(this.oreItem, this.requiredAmount, Math.max(this.collectedAmount, this.requiredAmount));
    }
}
