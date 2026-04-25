package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

public final class DataFlowKeyType extends AEKeyType {
    public static final DataFlowKeyType TYPE = new DataFlowKeyType();

    private DataFlowKeyType() {
        super(
                ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_flow"),
                DataFlowKey.class,
                Component.translatable("key." + Data_Energistics.MODID + ".data_flow"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return DataFlowKey.MAP_CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        return DataFlowKey.of();
    }

    @Override
    public int getAmountPerByte() {
        return 8;
    }

    @Override
    public int getAmountPerOperation() {
        return 1;
    }
}
