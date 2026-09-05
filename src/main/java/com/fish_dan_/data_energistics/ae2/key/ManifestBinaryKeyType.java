package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import com.mojang.serialization.MapCodec;

/**
 * AE key type that exposes Data as the independent Manifest Binary visibility group.
 */
public final class ManifestBinaryKeyType extends AEKeyType {

    public static final ManifestBinaryKeyType TYPE = new ManifestBinaryKeyType();

    private ManifestBinaryKeyType() {
        super(
                Data_Energistics.id("manifest_binary"),
                DataKey.class,
                Component.translatable("key_type." + Data_Energistics.MODID + ".manifest_binary"));
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
        return 8;
    }
}
