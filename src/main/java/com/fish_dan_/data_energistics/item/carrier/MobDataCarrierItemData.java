package com.fish_dan_.data_energistics.item.carrier;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MobDataCarrierItemData(ResourceLocation entityType, float requiredDamage, float collectedDamage) {

    public static final Codec<MobDataCarrierItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity_type").forGetter(MobDataCarrierItemData::entityType),
            Codec.FLOAT.optionalFieldOf("required_damage", 0.0F).forGetter(MobDataCarrierItemData::requiredDamage),
            Codec.FLOAT.optionalFieldOf("collected_damage", 0.0F).forGetter(MobDataCarrierItemData::collectedDamage))
            .apply(instance, MobDataCarrierItemData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MobDataCarrierItemData> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            MobDataCarrierItemData::entityType,
            ByteBufCodecs.FLOAT,
            MobDataCarrierItemData::requiredDamage,
            ByteBufCodecs.FLOAT,
            MobDataCarrierItemData::collectedDamage,
            MobDataCarrierItemData::new);

    public MobDataCarrierItemData withAddedCollectedDamage(float damage) {
        return new MobDataCarrierItemData(this.entityType, this.requiredDamage, Math.min(Math.max(0.0F, this.collectedDamage + damage), Math.max(1.0F, this.requiredDamage)));
    }

    public MobDataCarrierItemData asComplete() {
        return new MobDataCarrierItemData(this.entityType, this.requiredDamage, Math.max(this.collectedDamage, this.requiredDamage));
    }
}
