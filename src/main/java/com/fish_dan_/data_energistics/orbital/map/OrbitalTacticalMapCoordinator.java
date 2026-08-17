package com.fish_dan_.data_energistics.orbital.map;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointKind;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponAction;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * Server-thread tactical-map coordinator. It never calls a chunk-loading API: a missing chunk becomes an UNKNOWN
 * tile, while loaded chunks expose a deterministic average surface height, biome color and public marker bits.
 */
public final class OrbitalTacticalMapCoordinator {

    public static final OrbitalTacticalMapCoordinator INSTANCE = new OrbitalTacticalMapCoordinator();
    private static final UUID BOOTSTRAP_TOKEN = new UUID(0L, 0L);
    private static final int MAX_REQUESTS_PER_WINDOW = 4;
    private static final long REQUEST_WINDOW_TICKS = 20L;
    private static final long SESSION_TICKS = 20L * 60L;

    private final Map<MinecraftServer, ServerState> serverStates = new WeakHashMap<>();

    private OrbitalTacticalMapCoordinator() {}

    /** Handles one validated viewport intent on the server thread. */
    public Optional<OrbitalTacticalMapSnapshot> request(
                                                         ServerPlayer player,
                                                         UUID weaponId,
                                                         UUID sessionToken,
                                                         ResourceLocation dimensionId,
                                                         int centerChunkX,
                                                         int centerChunkZ,
                                                         int radius,
                                                         long nonce) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            return Optional.empty();
        }
        if (radius < 0 || radius > 3 || nonce <= 0L
                || Math.abs((long) centerChunkX) > 2_000_000L
                || Math.abs((long) centerChunkZ) > 2_000_000L) {
            return Optional.empty();
        }
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalWeaponRecord weapon = weapons.find(weaponId).orElse(null);
        if (weapon == null
                || !weapon.canPerform(player.getUUID(), OrbitalWeaponAction.AIM)
                || !weapons.hasOnlineEndpoint(server, weaponId, dimensionId)) {
            return Optional.empty();
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null || !insideBorder(level, centerChunkX, centerChunkZ, radius)) {
            return Optional.empty();
        }

        long gameTime = server.overworld().getGameTime();
        ServerState state = this.serverStates.computeIfAbsent(server, ignored -> new ServerState());
        Session session = state.sessions.get(player.getUUID());
        if (session == null || session.expiresAt <= gameTime
                || !session.weaponId.equals(weaponId) || !session.token.equals(sessionToken)) {
            if (!BOOTSTRAP_TOKEN.equals(sessionToken)) {
                return Optional.empty();
            }
            session = new Session(weaponId, UUID.randomUUID(), gameTime + SESSION_TICKS, gameTime);
            state.sessions.put(player.getUUID(), session);
        }
        if (session.nonces.contains(nonce)) {
            return Optional.empty();
        }
        if (gameTime - session.windowStart >= REQUEST_WINDOW_TICKS) {
            session.windowStart = gameTime;
            session.requestCount = 0;
            session.nonces.clear();
        }
        if (session.requestCount >= MAX_REQUESTS_PER_WINDOW) {
            return Optional.empty();
        }
        session.requestCount++;
        session.nonces.add(nonce);
        while (session.nonces.size() > MAX_REQUESTS_PER_WINDOW) {
            session.nonces.remove(session.nonces.iterator().next());
        }

        int side = radius * 2 + 1;
        Set<Long> seenChunks = new HashSet<>(side * side);
        Set<Long> publicAttackChunks = publicAttackChunks(level);
        ArrayList<OrbitalMapTile> tiles = new ArrayList<>(side * side);
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                int chunkX = centerChunkX + offsetX;
                int chunkZ = centerChunkZ + offsetZ;
                long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                if (!seenChunks.add(chunkKey)) {
                    throw new IllegalStateException("Tactical-map viewport produced a duplicate chunk");
                }
                int markers = markerFlags(level, weapon, chunkX, chunkZ, publicAttackChunks);
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    tiles.add(OrbitalMapTile.unknown(chunkX, chunkZ, markers));
                } else {
                    int surfaceY = averageSurfaceY(chunk);
                    int biomeColor = biomeColor(level, chunk, surfaceY);
                    tiles.add(new OrbitalMapTile(chunkX, chunkZ, true, surfaceY, biomeColor, markers));
                }
            }
        }
        long revision = ++state.revision;
        return Optional.of(new OrbitalTacticalMapSnapshot(
                session.token,
                revision,
                dimensionId,
                centerChunkX,
                centerChunkZ,
                radius,
                tiles));
    }

    /** Drops all ephemeral sessions when a server instance stops. */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        this.serverStates.remove(event.getServer());
    }

    private static boolean insideBorder(ServerLevel level, int centerChunkX, int centerChunkZ, int radius) {
        long minX = ((long) centerChunkX - radius) * 16L;
        long maxX = ((long) centerChunkX + radius) * 16L + 15L;
        long minZ = ((long) centerChunkZ - radius) * 16L;
        long maxZ = ((long) centerChunkZ + radius) * 16L + 15L;
        int y = level.getMinBuildHeight();
        return level.getWorldBorder().isWithinBounds(new BlockPos((int) minX, y, (int) minZ))
                && level.getWorldBorder().isWithinBounds(new BlockPos((int) maxX, y, (int) maxZ));
    }

    /** Samples sixteen fixed points so a loaded tile reports a stable mean rather than one noisy column. */
    private static int averageSurfaceY(LevelChunk chunk) {
        long sum = 0L;
        for (int sampleX = 2; sampleX < 16; sampleX += 4) {
            for (int sampleZ = 2; sampleZ < 16; sampleZ += 4) {
                sum += chunk.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ);
            }
        }
        return (int) (sum / 16L);
    }

    /** Uses the biome's built-in foliage palette, which is deterministic and does not require client tint data. */
    private static int biomeColor(ServerLevel level, LevelChunk chunk, int surfaceY) {
        int worldX = chunk.getPos().getMinBlockX() + 8;
        int worldZ = chunk.getPos().getMinBlockZ() + 8;
        return level.getBiome(new BlockPos(worldX, surfaceY, worldZ)).value().getFoliageColor() & 0xFFFFFF;
    }

    /** Computes only public, chunk-local marker bits; this path never asks the chunk source to load terrain. */
    private static int markerFlags(
                                   ServerLevel level,
                                   OrbitalWeaponRecord weapon,
                                   int chunkX,
                                   int chunkZ,
                                   Set<Long> publicAttackChunks) {
        int markers = 0;
        for (var endpoint : weapon.endpoints().values()) {
            OrbitalEndpointLocation location = endpoint.location();
            if (!location.dimensionId().equals(level.dimension().location())
                    || location.pos().getX() >> 4 != chunkX
                    || location.pos().getZ() >> 4 != chunkZ) {
                continue;
            }
            if (endpoint.kind() == OrbitalEndpointKind.UPLINK_BEACON) {
                markers |= OrbitalMapTile.MARKER_UPLINK_BEACON;
            }
            if (location.equals(weapon.primaryAnchor())) {
                markers |= OrbitalMapTile.MARKER_PRIMARY_ANCHOR;
            }
        }
        if (publicAttackChunks.contains(ChunkPos.asLong(chunkX, chunkZ))) {
            markers |= OrbitalMapTile.MARKER_ACTIVE_PUBLIC_ATTACK;
        }
        return markers;
    }

    private static Set<Long> publicAttackChunks(ServerLevel level) {
        return OrbitalAttackSavedData.get(level.getServer())
                .publicForDimension(level.dimension().location())
                .stream()
                .map(attack -> ChunkPos.asLong(new ChunkPos(attack.target()).x, new ChunkPos(attack.target()).z))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static final class ServerState {

        private final Map<UUID, Session> sessions = new HashMap<>();
        private long revision;
    }

    private static final class Session {

        private final UUID weaponId;
        private final UUID token;
        private final long expiresAt;
        private final Set<Long> nonces = new HashSet<>();
        private long windowStart;
        private int requestCount;

        private Session(UUID weaponId, UUID token, long expiresAt, long windowStart) {
            this.weaponId = weaponId;
            this.token = token;
            this.expiresAt = expiresAt;
            this.windowStart = windowStart;
        }
    }
}
