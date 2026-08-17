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

/** C2S owner intent to move one bound endpoint to a new dense failover rank. */
public record OrbitalEndpointPriorityPayload(
                                             UUID weaponId,
                                             ResourceLocation dimensionId,
                                             BlockPos endpointPos,
                                             int priority)
        implements CustomPacketPayload {

    private static final int MAX_ENDPOINT_PRIORITY = 31;
    private static final int HORIZONTAL_COORDINATE_LIMIT = 30_000_000;
    private static final int VERTICAL_COORDINATE_LIMIT = 2_000_000;

    public static final Type<OrbitalEndpointPriorityPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_endpoint_priority"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalEndpointPriorityPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalEndpointPriorityPayload::write,
            OrbitalEndpointPriorityPayload::new);

    public OrbitalEndpointPriorityPayload {
        endpointPos = endpointPos.immutable();
        if (priority < 0 || priority > MAX_ENDPOINT_PRIORITY || !bounded(endpointPos)) {
            throw new IllegalArgumentException("Orbital endpoint-priority intent is outside its bounded range");
        }
    }

    private OrbitalEndpointPriorityPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                buffer.readResourceLocation(),
                buffer.readBlockPos(),
                buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.weaponId);
        buffer.writeResourceLocation(this.dimensionId);
        buffer.writeBlockPos(this.endpointPos);
        buffer.writeVarInt(this.priority);
    }

    @Override
    public Type<OrbitalEndpointPriorityPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalEndpointPriorityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                boolean changed = OrbitalWeaponAdministrationDispatcher.setEndpointPriority(
                        player,
                        payload.weaponId(),
                        new OrbitalEndpointLocation(payload.dimensionId(), payload.endpointPos()),
                        payload.priority());
                player.displayClientMessage(
                        Component.translatable(changed ? "message.data_energistics.orbital.endpoint_priority_updated" : "message.data_energistics.orbital.endpoint_priority_rejected"),
                        true);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.warn(
                        "Rejected orbital endpoint priority intent from {} for weapon {}",
                        player.getUUID(),
                        payload.weaponId(),
                        exception);
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.orbital.endpoint_priority_rejected"),
                        true);
            }
        });
    }

    private static boolean bounded(BlockPos pos) {
        return Math.abs((long) pos.getX()) <= HORIZONTAL_COORDINATE_LIMIT && Math.abs((long) pos.getZ()) <= HORIZONTAL_COORDINATE_LIMIT && Math.abs((long) pos.getY()) <= VERTICAL_COORDINATE_LIMIT;
    }
}
