package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlPlayerMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S intent requesting the shared terminal UI; the server resolves and validates its current source. */
public final class OrbitalControlOpenPayload implements CustomPacketPayload {

    public static final OrbitalControlOpenPayload INSTANCE = new OrbitalControlOpenPayload();
    public static final Type<OrbitalControlOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "orbital_control_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalControlOpenPayload> STREAM_CODEC = StreamCodec.unit(
            INSTANCE);

    private OrbitalControlOpenPayload() {}

    @Override
    public Type<OrbitalControlOpenPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalControlOpenPayload ignoredPayload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                OrbitalControlPlayerMenu.open(serverPlayer);
            }
        });
    }
}
