package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlActionDispatcher;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S cancellation of an uncommitted orbital target preview. */
public record OrbitalAttackPreviewCancelPayload() implements CustomPacketPayload {

    public static final Type<OrbitalAttackPreviewCancelPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_attack_preview_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalAttackPreviewCancelPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalAttackPreviewCancelPayload::write,
            OrbitalAttackPreviewCancelPayload::new);

    private OrbitalAttackPreviewCancelPayload(RegistryFriendlyByteBuf buffer) {
        this(readMarker(buffer));
    }

    private OrbitalAttackPreviewCancelPayload(byte marker) {
        this();
        if (marker != 0) {
            throw new IllegalArgumentException("Invalid orbital preview-cancel marker");
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(0);
    }

    private static byte readMarker(RegistryFriendlyByteBuf buffer) {
        return buffer.readByte();
    }

    @Override
    public Type<OrbitalAttackPreviewCancelPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalAttackPreviewCancelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                OrbitalControlActionDispatcher.cancelFirePreview(player);
            }
        });
    }
}
