package com.fish_dan_.data_energistics.client.map.orbital;

import com.fish_dan_.data_energistics.network.orbital.map.OrbitalTacticalMapResponsePayload;
import com.fish_dan_.data_energistics.orbital.map.OrbitalMapTile;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Client LRU-like bounded cache for the latest server tactical-map revision. */
public final class OrbitalTacticalMapClientState {

    private static final int MAX_CACHED_TILES = 256;
    private static final UUID BOOTSTRAP_TOKEN = new UUID(0L, 0L);
    private static final Long2ObjectOpenHashMap<OrbitalMapTile> TILES = new Long2ObjectOpenHashMap<>();
    private static long revision = -1L;
    private static UUID sessionToken = new UUID(0L, 0L);
    private static ResourceLocation dimensionId = Level.OVERWORLD.location();
    private static int centerChunkX;
    private static int centerChunkZ;
    private static int radius;
    private static long requestNonce;
    private static @Nullable UUID requestedWeaponId;
    private static @Nullable ResourceLocation requestedDimension;

    private OrbitalTacticalMapClientState() {}

    /** Publishes only strictly newer complete revisions, dropping delayed or duplicate network packets. */
    public static void receive(OrbitalTacticalMapResponsePayload payload) {
        if (payload.revision() <= revision) {
            return;
        }
        if (requestedDimension != null && !requestedDimension.equals(payload.dimensionId())) {
            return;
        }
        TILES.clear();
        for (OrbitalMapTile tile : payload.tiles()) {
            TILES.put(ChunkPos.asLong(tile.chunkX(), tile.chunkZ()), tile);
        }
        while (TILES.size() > MAX_CACHED_TILES) {
            TILES.remove(TILES.keySet().iterator().nextLong());
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
        requestedWeaponId = null;
        requestedDimension = null;
    }

    public static long revision() {
        return revision;
    }

    /** Selects the local request context and returns its current session token or the bootstrap token. */
    public static UUID sessionTokenFor(UUID weaponId, ResourceLocation dimensionId) {
        boolean sameContext = weaponId.equals(requestedWeaponId) && dimensionId.equals(requestedDimension) && dimensionId.equals(OrbitalTacticalMapClientState.dimensionId);
        requestedWeaponId = weaponId;
        requestedDimension = dimensionId;
        return sameContext ? sessionToken : BOOTSTRAP_TOKEN;
    }

    public static ResourceLocation dimensionId() {
        return dimensionId;
    }

    /** Returns the current viewport center on the client thread. */
    public static int centerChunkX() {
        return centerChunkX;
    }

    /** Returns the current viewport center on the client thread. */
    public static int centerChunkZ() {
        return centerChunkZ;
    }

    /** Returns the current viewport radius on the client thread. */
    public static int radius() {
        return radius;
    }

    /** Allocates a process-local positive nonce for a fresh map request. */
    public static long nextRequestNonce() {
        requestNonce++;
        if (requestNonce <= 0L) {
            requestNonce = 1L;
        }
        return requestNonce;
    }

    /** Returns one cached cell without allocating a copy of the complete viewport. */
    public static @Nullable OrbitalMapTile tileAt(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        return TILES.containsKey(key) ? TILES.get(key) : null;
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
                OrbitalMapTile tile = tileAt(centerChunkX + x, centerChunkZ + z);
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
