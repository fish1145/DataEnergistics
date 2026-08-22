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

/** Client cache for one fully identified, server-authoritative tactical-map viewport. */
public final class OrbitalTacticalMapClientState {

    private static final UUID BOOTSTRAP_TOKEN = new UUID(0L, 0L);
    private static final Long2ObjectOpenHashMap<OrbitalMapTile> TILES = new Long2ObjectOpenHashMap<>();
    private static long revision = -1L;
    private static UUID sessionToken = BOOTSTRAP_TOKEN;
    private static ResourceLocation dimensionId = Level.OVERWORLD.location();
    private static int centerChunkX;
    private static int centerChunkZ;
    private static int radius;
    private static long requestNonce;
    private static long expectedResponseNonce;
    private static @Nullable UUID requestedWeaponId;
    private static @Nullable ResourceLocation requestedDimension;

    private OrbitalTacticalMapClientState() {}

    /** Publishes only strictly newer complete revisions, dropping delayed or duplicate network packets. */
    public static void receive(OrbitalTacticalMapResponsePayload payload) {
        if (payload.revision() <= revision) {
            return;
        }
        if (requestedWeaponId == null || requestedDimension == null ||
                !requestedWeaponId.equals(payload.weaponId()) ||
                !requestedDimension.equals(payload.dimensionId()) ||
                payload.requestNonce() != expectedResponseNonce) {
            return;
        }
        TILES.clear();
        for (OrbitalMapTile tile : payload.tiles()) {
            TILES.put(ChunkPos.asLong(tile.chunkX(), tile.chunkZ()), tile);
        }
        revision = payload.revision();
        sessionToken = payload.sessionToken();
        dimensionId = payload.dimensionId();
        centerChunkX = payload.centerChunkX();
        centerChunkZ = payload.centerChunkZ();
        radius = payload.radius();
        expectedResponseNonce = 0L;
    }

    /** Clears the cache when the client changes world or closes the tactical-map session. */
    public static void clear() {
        TILES.clear();
        revision = -1L;
        sessionToken = BOOTSTRAP_TOKEN;
        dimensionId = Level.OVERWORLD.location();
        centerChunkX = 0;
        centerChunkZ = 0;
        radius = 0;
        expectedResponseNonce = 0L;
        requestedWeaponId = null;
        requestedDimension = null;
    }

    public static long revision() {
        return revision;
    }

    /** Selects the local request context and returns its current session token or the bootstrap token. */
    public static UUID sessionTokenFor(UUID weaponId, ResourceLocation dimensionId) {
        boolean sameContext = weaponId.equals(requestedWeaponId) &&
                dimensionId.equals(requestedDimension) &&
                dimensionId.equals(OrbitalTacticalMapClientState.dimensionId);
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

    /** Records the exact weapon, dimension and nonce required for the next accepted response. */
    public static void expectResponse(UUID weaponId, ResourceLocation dimensionId, long nonce) {
        if (nonce <= 0L) {
            throw new IllegalArgumentException("Orbital tactical-map response nonce must be positive");
        }
        requestedWeaponId = weaponId;
        requestedDimension = dimensionId;
        expectedResponseNonce = nonce;
    }

    /** Returns one cached cell without allocating a copy of the complete viewport. */
    public static @Nullable OrbitalMapTile tileAt(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        return TILES.containsKey(key) ? TILES.get(key) : null;
    }

    /** Creates the one canonical LDLib2 cell marker used by the tactical-map panel. */
    public static Component cellComponent(@Nullable OrbitalMapTile tile) {
        if (tile == null) {
            return Component.literal("?").withStyle(style -> style.withColor(0x777777));
        }
        char marker;
        if ((tile.markerFlags() & OrbitalMapTile.MARKER_ACTIVE_PUBLIC_ATTACK) != 0) {
            marker = 'A';
        } else if ((tile.markerFlags() & OrbitalMapTile.MARKER_PRIMARY_ANCHOR) != 0) {
            marker = 'P';
        } else if ((tile.markerFlags() & OrbitalMapTile.MARKER_UPLINK_BEACON) != 0) {
            marker = 'B';
        } else {
            marker = tile.known() ? '.' : '?';
        }
        int color = tile.known() ? tile.biomeColor() : 0x777777;
        return Component.literal(Character.toString(marker)).withStyle(style -> style.withColor(color));
    }
}
