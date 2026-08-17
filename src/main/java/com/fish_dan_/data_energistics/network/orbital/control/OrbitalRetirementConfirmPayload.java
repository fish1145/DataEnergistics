package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.control.OrbitalOwnershipActionDispatcher;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S second step that consumes the server-generated retirement capability exactly once. */
public record OrbitalRetirementConfirmPayload(UUID weaponId, UUID confirmationToken)
        implements CustomPacketPayload {

    public static final Type<OrbitalRetirementConfirmPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_retirement_confirm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalRetirementConfirmPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalRetirementConfirmPayload::write,
            OrbitalRetirementConfirmPayload::new);

    private OrbitalRetirementConfirmPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.weaponId);
        buffer.writeUUID(this.confirmationToken);
    }

    @Override
    public Type<OrbitalRetirementConfirmPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalRetirementConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            boolean retired;
            try {
                retired = OrbitalOwnershipActionDispatcher.confirmRetirement(
                        player,
                        payload.weaponId(),
                        payload.confirmationToken());
            } catch (RuntimeException exception) {
                retired = false;
            }
            player.displayClientMessage(
                    Component.translatable(
                            retired ? "message.data_energistics.orbital.retirement_completed" : "message.data_energistics.orbital.retirement_rejected"),
                    true);
        });
    }
}
