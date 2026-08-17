package com.fish_dan_.data_energistics.network.orbital.map;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.map.OrbitalTacticalMapCoordinator;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S tactical-map intent; the server rechecks weapon access, endpoint reachability and all viewport bounds. */
public record OrbitalTacticalMapRequestPayload(
                                               UUID weaponId,
                                               UUID sessionToken,
                                               ResourceLocation dimensionId,
                                               int centerChunkX,
                                               int centerChunkZ,
                                               int radius,
                                               long nonce)
        implements CustomPacketPayload {

    public static final Type<OrbitalTacticalMapRequestPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_tactical_map_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalTacticalMapRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalTacticalMapRequestPayload::write,
            OrbitalTacticalMapRequestPayload::new);

    public OrbitalTacticalMapRequestPayload {
        if (radius < 0 || radius > 3 || nonce <= 0L || Math.abs((long) centerChunkX) > 2_000_000L || Math.abs((long) centerChunkZ) > 2_000_000L) {
            throw new IllegalArgumentException("Orbital tactical-map request is outside its bounded viewport");
        }
    }

    private OrbitalTacticalMapRequestPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readResourceLocation(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.weaponId);
        buffer.writeUUID(this.sessionToken);
        buffer.writeResourceLocation(this.dimensionId);
        buffer.writeVarInt(this.centerChunkX);
        buffer.writeVarInt(this.centerChunkZ);
        buffer.writeVarInt(this.radius);
        buffer.writeVarLong(this.nonce);
    }

    @Override
    public Type<OrbitalTacticalMapRequestPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalTacticalMapRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            OrbitalTacticalMapCoordinator.INSTANCE.request(
                    player,
                    payload.weaponId(),
                    payload.sessionToken(),
                    payload.dimensionId(),
                    payload.centerChunkX(),
                    payload.centerChunkZ(),
                    payload.radius(),
                    payload.nonce())
                    .ifPresent(snapshot -> PacketDistributor.sendToPlayer(
                            player,
                            new OrbitalTacticalMapResponsePayload(snapshot)));
        });
    }
}
