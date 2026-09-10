package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.item.crossbow.RailBeamPresentation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChromaticGlowPayload(int entity, int color, long until) implements CustomPacketPayload {

    public static final Type<ChromaticGlowPayload> TYPE = new Type<>(Data_Energistics.id("chromatic_glow"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChromaticGlowPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChromaticGlowPayload::entity, ByteBufCodecs.INT, ChromaticGlowPayload::color,
            ByteBufCodecs.VAR_LONG, ChromaticGlowPayload::until, ChromaticGlowPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChromaticGlowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RailBeamPresentation.glow(payload));
    }
}
