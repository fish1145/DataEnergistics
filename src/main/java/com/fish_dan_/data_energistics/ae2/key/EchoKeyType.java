package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;

/**
 * AE storage-channel definition for the stateless Echo resource.
 */
public final class EchoKeyType extends AEKeyType {

    public static final EchoKeyType TYPE = new EchoKeyType();

    private EchoKeyType() {
        super(
                EchoKey.ID,
                EchoKey.class,
                Component.translatable("key." + Data_Energistics.MODID + ".echo"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return EchoKey.MAP_CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        return EchoKey.of();
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
