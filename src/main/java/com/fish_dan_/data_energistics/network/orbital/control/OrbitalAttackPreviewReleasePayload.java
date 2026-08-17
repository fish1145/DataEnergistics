package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.orbital.OrbitalControlTerminalItem;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlActionDispatcher;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.server.level.ServerPlayer;

/** C2S release of a map-fire preview after the server-clock confirmation hold. */
public record OrbitalAttackPreviewReleasePayload(OrbitalAttackMode mode) implements CustomPacketPayload {

    public static final Type<OrbitalAttackPreviewReleasePayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_attack_preview_release"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalAttackPreviewReleasePayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalAttackPreviewReleasePayload::write,
            OrbitalAttackPreviewReleasePayload::new);

    private OrbitalAttackPreviewReleasePayload(RegistryFriendlyByteBuf buffer) {
        this(readMode(buffer.readVarInt()));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.mode == OrbitalAttackMode.KINETIC
                ? 0
                : this.mode == OrbitalAttackMode.DIRECTED_ENERGY ? 1 : 2);
    }

    @Override
    public Type<OrbitalAttackPreviewReleasePayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalAttackPreviewReleasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            OrbitalControlActionDispatcher.releaseFireAtTarget(
                    player,
                    payload.mode(),
                    () -> OrbitalControlTerminalItem.isHeldBy(player));
            if (!OrbitalControlTerminalItem.isHeldBy(player)) {
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.orbital_control_terminal.preview_expired"),
                        true);
            }
        });
    }

    private static OrbitalAttackMode readMode(int code) {
        return switch (code) {
            case 0 -> OrbitalAttackMode.KINETIC;
            case 1 -> OrbitalAttackMode.DIRECTED_ENERGY;
            case 2 -> OrbitalAttackMode.DIGITAL_ANNIHILATION;
            default -> throw new IllegalArgumentException("Unknown orbital attack mode code: " + code);
        };
    }
}
