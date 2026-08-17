package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Server-authoritative status pushed to the LDLib2 orbital HUD while its terminal is held. */
public record OrbitalControlHudSnapshotPayload(
                                               long revision,
                                               boolean visible,
                                               Component status,
                                               @Nullable UUID selectedWeaponId)
        implements CustomPacketPayload {

    public static final Type<OrbitalControlHudSnapshotPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_control_hud_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalControlHudSnapshotPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalControlHudSnapshotPayload::write,
            OrbitalControlHudSnapshotPayload::new);

    public OrbitalControlHudSnapshotPayload {
        if (revision < 0L) {
            throw new IllegalArgumentException("Orbital HUD revision must not be negative: " + revision);
        }
        if (visible && selectedWeaponId == null) {
            throw new IllegalArgumentException("A visible orbital HUD must identify its selected weapon");
        }
    }

    /** Compatibility constructor for callers that only publish text and visibility. */
    public OrbitalControlHudSnapshotPayload(long revision, boolean visible, Component status) {
        this(revision, visible, status, null);
    }

    private OrbitalControlHudSnapshotPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarLong(),
                buffer.readBoolean(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                buffer.readBoolean() ? buffer.readUUID() : null);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(this.revision);
        buffer.writeBoolean(this.visible);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, this.status);
        buffer.writeBoolean(this.selectedWeaponId != null);
        if (this.selectedWeaponId != null) {
            buffer.writeUUID(this.selectedWeaponId);
        }
    }

    @Override
    public Type<OrbitalControlHudSnapshotPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalControlHudSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DataEnergisticsClientBridgeAccess.get().cacheOrbitalControlHud(payload));
    }
}
