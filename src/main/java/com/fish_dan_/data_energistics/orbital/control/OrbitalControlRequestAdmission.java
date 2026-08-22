package com.fish_dan_.data_energistics.orbital.control;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Server-clock admission boundary for C2S orbital actions that are otherwise valid to repeat.
 *
 * <p>
 * Preview acquisition and tactical-map requests own their richer session-specific limits. This tracker covers the
 * remaining confirmation and delegated-authorization mutation channels without affecting operator commands.
 * </p>
 */
public final class OrbitalControlRequestAdmission {

    private static final long MINIMUM_CONFIRMATION_INTERVAL_TICKS = 5L;
    private static final long MINIMUM_AUTHORIZATION_INTERVAL_TICKS = 5L;
    private static final long RETENTION_TICKS = 600L;
    private static final Object2LongOpenHashMap<UUID> LAST_CONFIRMATION_AT = new Object2LongOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> LAST_AUTHORIZATION_AT = new Object2LongOpenHashMap<>();
    private static @Nullable MinecraftServer trackedServer;

    private OrbitalControlRequestAdmission() {}

    /** Returns whether a confirmation intent must be rejected without touching its preview session. */
    public static boolean confirmationRateExceeded(ServerPlayer player) {
        return rateExceeded(player, LAST_CONFIRMATION_AT, MINIMUM_CONFIRMATION_INTERVAL_TICKS);
    }

    /** Returns whether an authorization mutation must be rejected before SavedData is touched. */
    public static boolean authorizationRateExceeded(ServerPlayer player) {
        return rateExceeded(player, LAST_AUTHORIZATION_AT, MINIMUM_AUTHORIZATION_INTERVAL_TICKS);
    }

    /** Evicts disconnected or idle player entries while observing the same authoritative server clock. */
    public static void expire(MinecraftServer server) {
        requireServerThread(server);
        trackServer(server);
        long now = server.overworld().getGameTime();
        LAST_CONFIRMATION_AT.object2LongEntrySet()
                .removeIf(entry -> now - entry.getLongValue() >= RETENTION_TICKS);
        LAST_AUTHORIZATION_AT.object2LongEntrySet()
                .removeIf(entry -> now - entry.getLongValue() >= RETENTION_TICKS);
    }

    /** Clears all transient request history when the tracked server stops. */
    public static void clear(MinecraftServer server) {
        if (trackedServer != server) {
            return;
        }
        trackedServer = null;
        LAST_CONFIRMATION_AT.clear();
        LAST_AUTHORIZATION_AT.clear();
    }

    private static boolean rateExceeded(
                                        ServerPlayer player,
                                        Object2LongOpenHashMap<UUID> lastAcceptedAt,
                                        long minimumIntervalTicks) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return true;
        }
        requireServerThread(server);
        trackServer(server);
        UUID playerId = player.getUUID();
        long now = server.overworld().getGameTime();
        if (lastAcceptedAt.containsKey(playerId)) {
            long lastAccepted = lastAcceptedAt.getLong(playerId);
            if (now >= lastAccepted && now - lastAccepted < minimumIntervalTicks) {
                return true;
            }
        }
        lastAcceptedAt.put(playerId, now);
        return false;
    }

    private static void trackServer(MinecraftServer server) {
        if (trackedServer == server) {
            return;
        }
        trackedServer = server;
        LAST_CONFIRMATION_AT.clear();
        LAST_AUTHORIZATION_AT.clear();
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Orbital request admission may only run on the server thread");
        }
    }
}
