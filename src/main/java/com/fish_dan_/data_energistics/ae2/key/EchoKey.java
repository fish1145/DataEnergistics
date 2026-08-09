package com.fish_dan_.data_energistics.ae2.key;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * Stateless AE resource produced when a Warden sonic boom crosses an online formation plane.
 */
public final class EchoKey extends DigitalizationKey {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "echo");
    public static final EchoKey INSTANCE = new EchoKey();
    public static final MapCodec<EchoKey> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final Codec<EchoKey> CODEC = MAP_CODEC.codec();

    private EchoKey() {}

    public static EchoKey of() {
        return INSTANCE;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Component computeDisplayName() {
        return Component.translatable("key." + Data_Energistics.MODID + ".echo");
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EchoKey;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }

    @Override
    public String toString() {
        return "EchoKey{}";
    }
}
