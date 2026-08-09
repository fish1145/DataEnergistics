package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

public final class DataFlowKey extends DigitalizationKey {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_flow");
    public static final DataFlowKey INSTANCE = new DataFlowKey();
    public static final MapCodec<DataFlowKey> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final Codec<DataFlowKey> CODEC = MAP_CODEC.codec();

    private DataFlowKey() {}

    public static DataFlowKey of() {
        return INSTANCE;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Component computeDisplayName() {
        return Component.translatable("key." + Data_Energistics.MODID + ".data_flow");
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DataFlowKey;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }

    @Override
    public String toString() {
        return "DataFlowKey{}";
    }
}
