package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.orbital.OrbitalControlTerminalItem;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;
import com.fish_dan_.data_energistics.orbital.control.OrbitalAttackPreviewSessions;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlActionDispatcher;
import com.fish_dan_.data_energistics.orbital.control.OrbitalTargetYMode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * C2S map-fire intent. It carries only bounded coordinates and geometry choices; the server resolves height, weapon,
 * endpoint, cost and all revisions before creating a preview session.
 */
public record OrbitalAttackPreviewRequestPayload(
                                                 OrbitalAttackMode mode,
                                                 ResourceLocation dimensionId,
                                                 int targetX,
                                                 int targetZ,
                                                 OrbitalTargetYMode targetYMode,
                                                 int targetYValue,
                                                 int directedRadius,
                                                 @Nullable OrbitalDirectedEnergyDepth directedDepth)
        implements CustomPacketPayload {

    private static final int COORDINATE_LIMIT = 30_000_000;
    private static final int Y_VALUE_LIMIT = 2_000_000;

    public static final Type<OrbitalAttackPreviewRequestPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_attack_preview_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalAttackPreviewRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalAttackPreviewRequestPayload::write,
            OrbitalAttackPreviewRequestPayload::new);

    public OrbitalAttackPreviewRequestPayload {
        if (Math.abs((long) targetX) > COORDINATE_LIMIT || Math.abs((long) targetZ) > COORDINATE_LIMIT || Math.abs((long) targetYValue) > Y_VALUE_LIMIT) {
            throw new IllegalArgumentException("Orbital preview coordinates are outside the bounded map intent");
        }
        if (mode == OrbitalAttackMode.DIRECTED_ENERGY) {
            if (directedDepth == null) {
                throw new IllegalArgumentException("Directed-energy preview depth is required");
            }
            OrbitalDirectedEnergyStrike.validateSupportedRadius(directedRadius);
        } else if (directedRadius != 0 || directedDepth != null) {
            throw new IllegalArgumentException("Non-directed preview cannot carry directed geometry");
        }
    }

    private OrbitalAttackPreviewRequestPayload(RegistryFriendlyByteBuf buffer) {
        this(
                OrbitalAttackMode.fromWireCode(buffer.readVarInt()),
                buffer.readResourceLocation(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                OrbitalTargetYMode.fromWireCode(buffer.readVarInt()),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readDepth(buffer.readVarInt()));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.mode.wireCode());
        buffer.writeResourceLocation(this.dimensionId);
        buffer.writeVarInt(this.targetX);
        buffer.writeVarInt(this.targetZ);
        buffer.writeVarInt(this.targetYMode.wireCode());
        buffer.writeVarInt(this.targetYValue);
        buffer.writeVarInt(this.directedRadius);
        buffer.writeVarInt(this.directedDepth == null ? -1 : this.directedDepth.wireCode());
    }

    @Override
    public Type<OrbitalAttackPreviewRequestPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalAttackPreviewRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                Optional<OrbitalAttackPreviewSessions.Preview> preview = OrbitalControlActionDispatcher.beginFireAtTarget(
                        player,
                        payload.mode(),
                        payload.dimensionId(),
                        payload.targetX(),
                        payload.targetZ(),
                        payload.targetYMode(),
                        payload.targetYValue(),
                        payload.directedRadius(),
                        payload.directedDepth(),
                        () -> OrbitalControlTerminalItem.isHeldBy(player));
                if (preview.isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("message.data_energistics.orbital_control_terminal.action_rejected"),
                            true);
                }
            } catch (RuntimeException exception) {
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.orbital_control_terminal.action_rejected"),
                        true);
            }
        });
    }

    @Nullable
    private static OrbitalDirectedEnergyDepth readDepth(int code) {
        return code == -1 ? null : OrbitalDirectedEnergyDepth.fromWireCode(code);
    }
}
