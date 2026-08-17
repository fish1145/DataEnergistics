package com.fish_dan_.data_energistics.client.map.orbital;

import com.fish_dan_.data_energistics.network.orbital.map.OrbitalTacticalMapResponsePayload;
import com.fish_dan_.data_energistics.orbital.map.OrbitalMapTile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client LRU-like bounded cache for the latest server tactical-map revision. */
public final class OrbitalTacticalMapClientState {

    private static final int MAX_CACHED_TILES = 256;
    private static final Map<Long, OrbitalMapTile> TILES = new HashMap<>();
    private static long revision = -1L;
    private static UUID sessionToken = new UUID(0L, 0L);
    private static ResourceLocation dimensionId = Level.OVERWORLD.location();
    private static int centerChunkX;
    private static int centerChunkZ;
    private static int radius;

    private OrbitalTacticalMapClientState() {}

    /** Publishes only strictly newer complete revisions, dropping delayed or duplicate network packets. */
    public static void receive(OrbitalTacticalMapResponsePayload payload) {
        if (payload.revision() <= revision) {
            return;
        }
        TILES.clear();
        for (OrbitalMapTile tile : payload.tiles()) {
            TILES.put(ChunkPos.asLong(tile.chunkX(), tile.chunkZ()), tile);
        }
        while (TILES.size() > MAX_CACHED_TILES) {
            TILES.remove(TILES.keySet().iterator().next());
        }
        revision = payload.revision();
        sessionToken = payload.sessionToken();
        dimensionId = payload.dimensionId();
        centerChunkX = payload.centerChunkX();
        centerChunkZ = payload.centerChunkZ();
        radius = payload.radius();
    }

    /** Clears the cache when the client changes world or closes the tactical-map session. */
    public static void clear() {
        TILES.clear();
        revision = -1L;
        sessionToken = new UUID(0L, 0L);
        dimensionId = Level.OVERWORLD.location();
        centerChunkX = 0;
        centerChunkZ = 0;
        radius = 0;
    }

    public static long revision() {
        return revision;
    }

    public static UUID sessionToken() {
        return sessionToken;
    }

    public static ResourceLocation dimensionId() {
        return dimensionId;
    }

    public static Map<Long, OrbitalMapTile> tiles() {
        return Map.copyOf(TILES);
    }

    /** Returns a compact textual viewport used by the LDLib2 HUD until a larger map panel is opened. */
    public static Component summary() {
        if (revision < 0L) {
            return Component.empty();
        }
        StringBuilder result = new StringBuilder("Map ")
                .append(dimensionId)
                .append(" ")
                .append(centerChunkX)
                .append(",")
                .append(centerChunkZ)
                .append('\n');
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                OrbitalMapTile tile = TILES.get(ChunkPos.asLong(centerChunkX + x, centerChunkZ + z));
                result.append(tile == null ? '?' : tileMarker(tile));
            }
            if (z < radius) {
                result.append('\n');
            }
        }
        return Component.literal(result.toString());
    }

    private static char tileMarker(OrbitalMapTile tile) {
        if ((tile.markerFlags() & OrbitalMapTile.MARKER_ACTIVE_PUBLIC_ATTACK) != 0) {
            return 'A';
        }
        if ((tile.markerFlags() & OrbitalMapTile.MARKER_PRIMARY_ANCHOR) != 0) {
            return 'P';
        }
        if ((tile.markerFlags() & OrbitalMapTile.MARKER_UPLINK_BEACON) != 0) {
            return 'B';
        }
        return tile.known() ? '.' : '?';
    }
}
