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

/** C2S first step of the owner-only, double-confirmed retirement flow. */
public record OrbitalRetirementRequestPayload(UUID weaponId) implements CustomPacketPayload {

    public static final Type<OrbitalRetirementRequestPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_retirement_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalRetirementRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalRetirementRequestPayload::write,
            OrbitalRetirementRequestPayload::new);

    private OrbitalRetirementRequestPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.weaponId);
    }

    @Override
    public Type<OrbitalRetirementRequestPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalRetirementRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                OrbitalOwnershipActionDispatcher.beginRetirement(player, payload.weaponId())
                        .ifPresentOrElse(
                                token -> player.displayClientMessage(
                                        Component.translatable(
                                                "message.data_energistics.orbital.retirement_confirmation_required",
                                                payload.weaponId(),
                                                token),
                                        false),
                                () -> player.displayClientMessage(
                                        Component.translatable("message.data_energistics.orbital.retirement_rejected"),
                                        true));
            } catch (RuntimeException exception) {
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.orbital.retirement_rejected"),
                        true);
            }
        });
    }
}
