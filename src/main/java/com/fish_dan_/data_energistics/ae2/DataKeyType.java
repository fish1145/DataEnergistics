package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;

public final class DataKeyType extends AEKeyType {

    public static final DataKeyType TYPE = new DataKeyType();

    private DataKeyType() {
        super(
                ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data"),
                DataKey.class,
                Component.translatable("key." + Data_Energistics.MODID + ".data"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return DataKey.MAP_CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        return DataKey.of();
    }

    @Override
    public int getAmountPerByte() {
        return 1;
    }

    @Override
    public int getAmountPerOperation() {
        return 1;
    }
}
