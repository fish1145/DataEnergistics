package com.fish_dan_.data_energistics.network.orbital.map;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.map.orbital.OrbitalTacticalMapClientState;
import com.fish_dan_.data_energistics.orbital.map.OrbitalMapTile;
import com.fish_dan_.data_energistics.orbital.map.OrbitalTacticalMapSnapshot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** S2C revisioned tactical-map viewport; at most 64 cells travel in one packet. */
public record OrbitalTacticalMapResponsePayload(
                                                 UUID sessionToken,
                                                 long revision,
                                                 ResourceLocation dimensionId,
                                                 int centerChunkX,
                                                 int centerChunkZ,
                                                 int radius,
                                                 List<OrbitalMapTile> tiles) implements CustomPacketPayload {

    public static final Type<OrbitalTacticalMapResponsePayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_tactical_map_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalTacticalMapResponsePayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalTacticalMapResponsePayload::write,
            OrbitalTacticalMapResponsePayload::new);
    public static final int MAX_TILES = OrbitalTacticalMapSnapshot.MAX_TILES;

    public OrbitalTacticalMapResponsePayload(OrbitalTacticalMapSnapshot snapshot) {
        this(
                snapshot.sessionToken(),
                snapshot.revision(),
                snapshot.dimensionId(),
                snapshot.centerChunkX(),
                snapshot.centerChunkZ(),
                snapshot.radius(),
                snapshot.tiles());
    }

    public OrbitalTacticalMapResponsePayload {
        tiles = List.copyOf(tiles);
        int expected = (radius * 2 + 1) * (radius * 2 + 1);
        if (revision < 0L || radius < 0 || radius > 3 || tiles.size() != expected || tiles.size() > MAX_TILES) {
            throw new IllegalArgumentException("Orbital tactical-map response is outside its bounded viewport");
        }
    }

    private OrbitalTacticalMapResponsePayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                buffer.readVarLong(),
                buffer.readResourceLocation(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readTiles(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.sessionToken);
        buffer.writeVarLong(this.revision);
        buffer.writeResourceLocation(this.dimensionId);
        buffer.writeVarInt(this.centerChunkX);
        buffer.writeVarInt(this.centerChunkZ);
        buffer.writeVarInt(this.radius);
        buffer.writeVarInt(this.tiles.size());
        for (OrbitalMapTile tile : this.tiles) {
            buffer.writeVarInt(tile.chunkX());
            buffer.writeVarInt(tile.chunkZ());
            buffer.writeBoolean(tile.known());
            if (tile.known()) {
                buffer.writeVarInt(tile.surfaceY());
            }
        }
    }

    @Override
    public Type<OrbitalTacticalMapResponsePayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalTacticalMapResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> OrbitalTacticalMapClientState.receive(payload));
    }

    private static List<OrbitalMapTile> readTiles(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_TILES) {
            throw new IllegalArgumentException("Orbital tactical-map tile count exceeds " + MAX_TILES);
        }
        ArrayList<OrbitalMapTile> tiles = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int chunkX = buffer.readVarInt();
            int chunkZ = buffer.readVarInt();
            boolean known = buffer.readBoolean();
            tiles.add(known
                    ? new OrbitalMapTile(chunkX, chunkZ, true, buffer.readVarInt())
                    : OrbitalMapTile.unknown(chunkX, chunkZ));
        }
        return List.copyOf(tiles);
    }
}
