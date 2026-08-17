package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.control.OrbitalWeaponAdministrationDispatcher;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S owner intent to revoke one delegated orbital access role. */
public record OrbitalAuthorizationRevokePayload(UUID weaponId, UUID playerId)
        implements CustomPacketPayload {

    public static final Type<OrbitalAuthorizationRevokePayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_authorization_revoke"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalAuthorizationRevokePayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalAuthorizationRevokePayload::write,
            OrbitalAuthorizationRevokePayload::new);

    private OrbitalAuthorizationRevokePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.weaponId);
        buffer.writeUUID(this.playerId);
    }

    @Override
    public Type<OrbitalAuthorizationRevokePayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalAuthorizationRevokePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                boolean revoked = OrbitalWeaponAdministrationDispatcher.revoke(
                        player,
                        payload.weaponId(),
                        payload.playerId());
                player.displayClientMessage(
                        Component.translatable(revoked
                                ? "message.data_energistics.orbital.authorization_revoked"
                                : "message.data_energistics.orbital.authorization_rejected"),
                        true);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.warn(
                        "Rejected orbital authorization-revoke intent from {} for weapon {}",
                        player.getUUID(),
                        payload.weaponId(),
                        exception);
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.orbital.authorization_rejected"),
                        true);
            }
        });
    }
}
