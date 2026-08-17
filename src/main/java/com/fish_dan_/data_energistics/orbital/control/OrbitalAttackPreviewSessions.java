package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-owned preview and confirmation-hold sessions for orbital attacks.
 *
 * <p>A session contains only the target intent and the configuration revision that produced it. It does not debit
 * reserves or mutate an attack record; the caller must pass the returned immutable preview to the authoritative attack
 * confirmation after the hold has completed. One player can have at most one session, and a session is scoped to the
 * {@link MinecraftServer} instance that created it.</p>
 */
public final class OrbitalAttackPreviewSessions {

    /** Default lifetime of a target preview. */
    public static final long DEFAULT_SESSION_TICKS = 600L;
    /** Required uninterrupted confirmation hold. */
    public static final long DEFAULT_HOLD_TICKS = 60L;

    private final Map<UUID, Preview> sessions = new HashMap<>();
    private @Nullable MinecraftServer trackedServer;

    /**
     * Starts or replaces the calling player's preview session.
     *
     * <p>The caller is expected to have performed the server-side access, target-dimension and geometry checks before
     * creating the preview. This boundary still rejects invalid revisions and directed-energy geometry so a session
     * cannot carry an invalid confirmation intent.</p>
     */
    public Optional<Preview> begin(
                                MinecraftServer server,
                                UUID playerId,
                                OrbitalAttackMode mode,
                                ResourceLocation dimensionId,
                                BlockPos target,
                                UUID weaponId,
                                int directedRadius,
                                @Nullable OrbitalDirectedEnergyDepth directedDepth,
                                long configurationRevision,
                                long stateRevision) {
        requireServerThread(server);
        trackServer(server);
        if (configurationRevision < 0L || stateRevision < 0L) {
            throw new IllegalArgumentException("Orbital preview revisions must not be negative");
        }
        if (mode == OrbitalAttackMode.DIRECTED_ENERGY) {
            OrbitalDirectedEnergyStrike.validateRadius(directedRadius);
            if (directedDepth == null) {
                return Optional.empty();
            }
        } else {
            if (directedRadius != 0 || directedDepth != null) {
                return Optional.empty();
            }
        }

        long now = server.overworld().getGameTime();
        Preview preview = new Preview(
                playerId,
                weaponId,
                mode,
                dimensionId,
                target,
                mode == OrbitalAttackMode.DIRECTED_ENERGY ? directedRadius : 0,
                mode == OrbitalAttackMode.DIRECTED_ENERGY ? directedDepth : null,
                UUID.randomUUID(),
                configurationRevision,
                stateRevision,
                now,
                saturatingAdd(now),
                now);
        this.sessions.put(playerId, preview);
        return Optional.of(preview);
    }

    /**
     * Completes a preview after the required hold. A failed release is destructive: stale or mismatched sessions are
     * removed rather than left available for replay.
     */
    public Optional<Preview> release(
                                  MinecraftServer server,
                                  UUID playerId,
                                  OrbitalAttackMode mode) {
        requireServerThread(server);
        trackServer(server);
        Preview preview = this.sessions.remove(playerId);
        if (preview == null || preview.mode() != mode) {
            return Optional.empty();
        }
        long now = server.overworld().getGameTime();
        if (preview.expired(now) || preview.heldTicks(now) < DEFAULT_HOLD_TICKS) {
            return Optional.empty();
        }
        return Optional.of(preview);
    }

    /** Cancels and removes any preview belonging to a player. */
    public boolean cancel(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        trackServer(server);
        return this.sessions.remove(playerId) != null;
    }

    /** Removes all previews that have passed their server-clock expiry. */
    public void expire(MinecraftServer server) {
        requireServerThread(server);
        trackServer(server);
        long now = server.overworld().getGameTime();
        this.sessions.values().removeIf(preview -> preview.expired(now));
    }

    /** Returns the current unexpired preview for one player without extending its lifetime. */
    public Optional<Preview> current(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        trackServer(server);
        Preview preview = this.sessions.get(playerId);
        if (preview == null) {
            return Optional.empty();
        }
        if (preview.expired(server.overworld().getGameTime())) {
            this.sessions.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(preview);
    }

    private void trackServer(MinecraftServer server) {
        if (this.trackedServer == server) {
            return;
        }
        this.trackedServer = server;
        this.sessions.clear();
    }

    private static long saturatingAdd(long value) {
        if (Long.MAX_VALUE - value < DEFAULT_SESSION_TICKS) {
            return Long.MAX_VALUE;
        }
        return value + DEFAULT_SESSION_TICKS;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Orbital preview sessions may only be modified on the server thread");
        }
    }

    /** Immutable target intent captured by one preview session. */
    public record Preview(
                         UUID playerId,
                         UUID weaponId,
                         OrbitalAttackMode mode,
                         ResourceLocation dimensionId,
                         BlockPos target,
                         int directedRadius,
                         @Nullable OrbitalDirectedEnergyDepth directedDepth,
                         UUID nonce,
                         long configurationRevision,
                         long stateRevision,
                         long createdAt,
                         long expiresAt,
                         long holdStartedAt) {

        public Preview {
            target = target.immutable();
            if (configurationRevision < 0L || stateRevision < 0L || createdAt < 0L || expiresAt <= createdAt || holdStartedAt < createdAt) {
                throw new IllegalArgumentException("Orbital preview timing or revision is invalid");
            }
            if (mode == OrbitalAttackMode.DIRECTED_ENERGY) {
                OrbitalDirectedEnergyStrike.validateRadius(directedRadius);
                if (directedDepth == null) {
                    throw new IllegalArgumentException("Directed-energy preview depth must be present");
                }
            } else if (directedRadius != 0 || directedDepth != null) {
                throw new IllegalArgumentException("Non-directed preview cannot carry directed-energy geometry");
            }
        }

        public boolean expired(long gameTime) {
            return gameTime >= this.expiresAt;
        }

        public long heldTicks(long gameTime) {
            return Math.max(0L, gameTime - this.holdStartedAt);
        }
    }
}
