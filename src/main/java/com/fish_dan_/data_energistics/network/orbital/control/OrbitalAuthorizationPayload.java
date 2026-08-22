package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlRequestAdmission;
import com.fish_dan_.data_energistics.orbital.control.OrbitalWeaponAdministrationDispatcher;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S owner intent to add or replace one delegated orbital access role. */
public record OrbitalAuthorizationPayload(
                                          UUID weaponId,
                                          UUID playerId,
                                          OrbitalAccessRole role)
        implements CustomPacketPayload {

    public static final Type<OrbitalAuthorizationPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_authorization"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalAuthorizationPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalAuthorizationPayload::write,
            OrbitalAuthorizationPayload::new);

    private OrbitalAuthorizationPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUUID(), readRole(buffer.readVarInt()));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.weaponId);
        buffer.writeUUID(this.playerId);
        buffer.writeVarInt(this.role == OrbitalAccessRole.OPERATOR ? 0 : 1);
    }

    @Override
    public Type<OrbitalAuthorizationPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalAuthorizationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (OrbitalControlRequestAdmission.authorizationRateExceeded(player)) {
                return;
            }
            try {
                boolean updated = OrbitalWeaponAdministrationDispatcher.authorize(
                        player,
                        payload.weaponId(),
                        payload.playerId(),
                        payload.role());
                player.displayClientMessage(
                        Component.translatable(updated ? "message.data_energistics.orbital.authorization_updated" : "message.data_energistics.orbital.authorization_rejected"),
                        true);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.warn(
                        "Rejected orbital authorization intent from {} for weapon {}",
                        player.getUUID(),
                        payload.weaponId(),
                        exception);
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.orbital.authorization_rejected"),
                        true);
            }
        });
    }

    private static OrbitalAccessRole readRole(int code) {
        return switch (code) {
            case 0 -> OrbitalAccessRole.OPERATOR;
            case 1 -> OrbitalAccessRole.OBSERVER;
            default -> throw new IllegalArgumentException("Unknown orbital access-role code: " + code);
        };
    }
}
