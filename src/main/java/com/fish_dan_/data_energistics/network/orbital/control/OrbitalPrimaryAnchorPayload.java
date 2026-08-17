package com.fish_dan_.data_energistics.network.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.control.OrbitalWeaponAdministrationDispatcher;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S owner intent to select one bound uplink beacon as the primary projection anchor. */
public record OrbitalPrimaryAnchorPayload(
                                          UUID weaponId,
                                          ResourceLocation dimensionId,
                                          BlockPos endpointPos)
        implements CustomPacketPayload {

    private static final int HORIZONTAL_COORDINATE_LIMIT = 30_000_000;
    private static final int VERTICAL_COORDINATE_LIMIT = 2_000_000;

    public static final Type<OrbitalPrimaryAnchorPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_primary_anchor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalPrimaryAnchorPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalPrimaryAnchorPayload::write,
            OrbitalPrimaryAnchorPayload::new);

    public OrbitalPrimaryAnchorPayload {
        endpointPos = endpointPos.immutable();
        if (!bounded(endpointPos)) {
            throw new IllegalArgumentException("Orbital primary-anchor intent is outside its bounded range");
        }
    }

    private OrbitalPrimaryAnchorPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readResourceLocation(), buffer.readBlockPos());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.weaponId);
        buffer.writeResourceLocation(this.dimensionId);
        buffer.writeBlockPos(this.endpointPos);
    }

    @Override
    public Type<OrbitalPrimaryAnchorPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalPrimaryAnchorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                boolean selected = OrbitalWeaponAdministrationDispatcher.selectPrimaryAnchor(
                        player,
                        payload.weaponId(),
                        new OrbitalEndpointLocation(payload.dimensionId(), payload.endpointPos()));
                player.displayClientMessage(
                        Component.translatable(selected ? "message.data_energistics.orbital.primary_anchor_updated" : "message.data_energistics.orbital.primary_anchor_rejected"),
                        true);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.warn(
                        "Rejected orbital primary-anchor intent from {} for weapon {}",
                        player.getUUID(),
                        payload.weaponId(),
                        exception);
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.orbital.primary_anchor_rejected"),
                        true);
            }
        });
    }

    private static boolean bounded(BlockPos pos) {
        return Math.abs((long) pos.getX()) <= HORIZONTAL_COORDINATE_LIMIT && Math.abs((long) pos.getZ()) <= HORIZONTAL_COORDINATE_LIMIT && Math.abs((long) pos.getY()) <= VERTICAL_COORDINATE_LIMIT;
    }
}
