package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.control.OrbitalOwnershipActionDispatcher;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalOwnershipTransfer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S intent to issue a server-owned, one-shot ownership transfer offer. */
public record OrbitalOwnershipTransferRequestPayload(UUID weaponId, UUID recipientId)
        implements CustomPacketPayload {

    public static final Type<OrbitalOwnershipTransferRequestPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_ownership_transfer_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalOwnershipTransferRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalOwnershipTransferRequestPayload::write,
            OrbitalOwnershipTransferRequestPayload::new);

    private OrbitalOwnershipTransferRequestPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.weaponId);
        buffer.writeUUID(this.recipientId);
    }

    @Override
    public Type<OrbitalOwnershipTransferRequestPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalOwnershipTransferRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                OrbitalOwnershipActionDispatcher.requestTransfer(player, payload.weaponId(), payload.recipientId())
                        .ifPresentOrElse(
                                offer -> notifyTransferCreated(player, offer),
                                () -> player.displayClientMessage(
                                        Component.translatable("message.data_energistics.orbital.transfer_rejected"),
                                        true));
            } catch (RuntimeException exception) {
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.orbital.transfer_rejected"),
                        true);
            }
        });
    }

    private static void notifyTransferCreated(ServerPlayer owner, OrbitalOwnershipTransfer offer) {
        owner.displayClientMessage(
                Component.translatable(
                        "message.data_energistics.orbital.transfer_created",
                        offer.transferId(),
                        offer.recipientId()),
                false);
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return;
        }
        ServerPlayer recipient = server.getPlayerList().getPlayer(offer.recipientId());
        if (recipient != null) {
            recipient.displayClientMessage(
                    Component.translatable(
                            "message.data_energistics.orbital.transfer_received",
                            offer.transferId(),
                            offer.currentOwnerId()),
                    false);
        }
    }
}
