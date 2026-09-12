package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.orbital.OrbitalControlConsoleBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Untrusted C2S request to return from a fullscreen map to one concrete control console. */
public record OrbitalControlConsoleOpenPayload(ResourceLocation dimensionId, BlockPos blockPos)
        implements CustomPacketPayload {

    public static final Type<OrbitalControlConsoleOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "orbital_control_console_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalControlConsoleOpenPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            OrbitalControlConsoleOpenPayload::dimensionId,
            BlockPos.STREAM_CODEC,
            OrbitalControlConsoleOpenPayload::blockPos,
            OrbitalControlConsoleOpenPayload::new);

    public OrbitalControlConsoleOpenPayload {
        blockPos = blockPos.immutable();
    }

    @Override
    public Type<OrbitalControlConsoleOpenPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalControlConsoleOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                OrbitalControlConsoleBlock.openFromMap(serverPlayer, payload.dimensionId(), payload.blockPos());
            }
        });
    }
}
