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

/** C2S intent to consume a recipient-bound ownership transfer capability. */
public record OrbitalOwnershipTransferAcceptPayload(UUID transferId) implements CustomPacketPayload {

    public static final Type<OrbitalOwnershipTransferAcceptPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_ownership_transfer_accept"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalOwnershipTransferAcceptPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalOwnershipTransferAcceptPayload::write,
            OrbitalOwnershipTransferAcceptPayload::new);

    private OrbitalOwnershipTransferAcceptPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.transferId);
    }

    @Override
    public Type<OrbitalOwnershipTransferAcceptPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalOwnershipTransferAcceptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            boolean accepted;
            try {
                accepted = OrbitalOwnershipActionDispatcher.acceptTransfer(player, payload.transferId());
            } catch (RuntimeException exception) {
                accepted = false;
            }
            player.displayClientMessage(
                    Component.translatable(
                            accepted
                                    ? "message.data_energistics.orbital.transfer_accepted"
                                    : "message.data_energistics.orbital.transfer_accept_rejected"),
                    true);
        });
    }
}
