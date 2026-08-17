package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative status pushed to the LDLib2 orbital HUD while its terminal is held. */
public record OrbitalControlHudSnapshotPayload(long revision, boolean visible, Component status)
        implements CustomPacketPayload {

    public static final Type<OrbitalControlHudSnapshotPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_control_hud_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalControlHudSnapshotPayload> STREAM_CODEC =
            CustomPacketPayload.codec(
                    OrbitalControlHudSnapshotPayload::write,
                    OrbitalControlHudSnapshotPayload::new);

    public OrbitalControlHudSnapshotPayload {
        if (revision < 0L) {
            throw new IllegalArgumentException("Orbital HUD revision must not be negative: " + revision);
        }
        if (status == null) {
            throw new IllegalArgumentException("Orbital HUD status must not be null");
        }
    }

    private OrbitalControlHudSnapshotPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarLong(),
                buffer.readBoolean(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(this.revision);
        buffer.writeBoolean(this.visible);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.status);
    }

    @Override
    public Type<OrbitalControlHudSnapshotPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalControlHudSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DataEnergisticsClientBridgeAccess.get().cacheOrbitalControlHud(payload));
    }
}
