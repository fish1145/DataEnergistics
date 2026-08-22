package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-owned preview and confirmation-hold sessions for orbital attacks.
 *
 * <p>
 * A session contains only the target intent and the configuration revision that produced it. It does not debit
 * reserves or mutate an attack record; the caller must pass the returned immutable preview to the authoritative attack
 * confirmation after the hold has completed. One player can have at most one session, and a session is scoped to the
 * {@link MinecraftServer} instance that created it.
 * </p>
 */
public final class OrbitalAttackPreviewSessions {

    /** Default lifetime of a target preview. */
    public static final long DEFAULT_SESSION_TICKS = 600L;
    /** Required uninterrupted confirmation hold. */
    public static final long DEFAULT_HOLD_TICKS = 60L;
    /** Maximum accepted preview acquisition rate per player. */
    public static final long MINIMUM_BEGIN_INTERVAL_TICKS = 5L;
    private static final long HOLD_NOT_STARTED = -1L;

    private final Object2ObjectOpenHashMap<UUID, Preview> sessions = new Object2ObjectOpenHashMap<>();
    private final Object2LongOpenHashMap<UUID> lastBeginAt = new Object2LongOpenHashMap<>();
    private @Nullable MinecraftServer trackedServer;

    /**
     * Starts or replaces the calling player's preview session.
     *
     * <p>
     * The caller is expected to have performed the server-side access, target-dimension and geometry checks before
     * creating the preview. This boundary still rejects invalid revisions and directed-energy geometry so a session
     * cannot carry an invalid confirmation intent.
     * </p>
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
                                   long stateRevision,
                                   OrbitalAttackPreviewEstimate estimate) {
        requireServerThread(server);
        trackServer(server);
        if (configurationRevision < 0L || stateRevision < 0L) {
            throw new IllegalArgumentException("Orbital preview revisions must not be negative");
        }
        if (mode == OrbitalAttackMode.DIRECTED_ENERGY) {
            OrbitalDirectedEnergyStrike.validateRadius(
                    directedRadius,
                    DataEnergisticsConfiguration.INSTANCE.orbitalWeapon);
            if (directedDepth == null) {
                return Optional.empty();
            }
        } else {
            if (directedRadius != 0 || directedDepth != null) {
                return Optional.empty();
            }
        }

        long now = server.overworld().getGameTime();
        if (!acceptsBeginAt(playerId, now)) {
            return Optional.empty();
        }
        this.lastBeginAt.put(playerId, now);
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
                estimate,
                now,
                saturatingAdd(now),
                HOLD_NOT_STARTED);
        this.sessions.put(playerId, preview);
        return Optional.of(preview);
    }

    /** Returns whether an expensive server-side preview estimate may be captured for this player now. */
    public boolean acceptsBegin(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        trackServer(server);
        return acceptsBeginAt(playerId, server.overworld().getGameTime());
    }

    /** Starts the confirmation clock for an existing target preview without replacing its captured estimate. */
    public boolean beginHold(MinecraftServer server, UUID playerId, OrbitalAttackMode mode) {
        requireServerThread(server);
        trackServer(server);
        if (!this.sessions.containsKey(playerId)) {
            return false;
        }
        Preview preview = this.sessions.get(playerId);
        long now = server.overworld().getGameTime();
        if (preview.expired(now) || preview.mode() != mode) {
            this.sessions.remove(playerId);
            return false;
        }
        this.sessions.put(playerId, preview.withHoldStartedAt(now));
        return true;
    }

    /** Cancels only the active confirmation clock while retaining the still-valid target preview. */
    public void cancelHold(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        trackServer(server);
        if (!this.sessions.containsKey(playerId)) {
            return;
        }
        Preview preview = this.sessions.get(playerId);
        if (!preview.holdStarted()) {
            return;
        }
        this.sessions.put(playerId, preview.withoutHold());
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
        if (!this.sessions.containsKey(playerId)) {
            return Optional.empty();
        }
        Preview preview = this.sessions.get(playerId);
        if (preview.mode() != mode) {
            this.sessions.remove(playerId);
            return Optional.empty();
        }
        long now = server.overworld().getGameTime();
        if (preview.expired(now)) {
            this.sessions.remove(playerId);
            return Optional.empty();
        }
        if (!preview.holdStarted() || preview.heldTicks(now) < DEFAULT_HOLD_TICKS) {
            this.sessions.put(playerId, preview.withoutHold());
            return Optional.empty();
        }
        this.sessions.remove(playerId);
        return Optional.of(preview);
    }

    /** Cancels and removes any preview belonging to a player. */
    public boolean cancel(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        trackServer(server);
        if (!this.sessions.containsKey(playerId)) {
            return false;
        }
        this.sessions.remove(playerId);
        return true;
    }

    /** Removes all previews that have passed their server-clock expiry. */
    public void expire(MinecraftServer server) {
        requireServerThread(server);
        trackServer(server);
        long now = server.overworld().getGameTime();
        this.sessions.values().removeIf(preview -> preview.expired(now));
        this.lastBeginAt.object2LongEntrySet()
                .removeIf(entry -> now - entry.getLongValue() >= DEFAULT_SESSION_TICKS);
    }

    /** Returns the current unexpired preview for one player without extending its lifetime. */
    public Optional<Preview> current(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        trackServer(server);
        if (!this.sessions.containsKey(playerId)) {
            return Optional.empty();
        }
        Preview preview = this.sessions.get(playerId);
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
        this.lastBeginAt.clear();
    }

    private boolean acceptsBeginAt(UUID playerId, long now) {
        return !this.lastBeginAt.containsKey(playerId) || now - this.lastBeginAt.getLong(playerId) >= MINIMUM_BEGIN_INTERVAL_TICKS;
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
                          OrbitalAttackPreviewEstimate estimate,
                          long createdAt,
                          long expiresAt,
                          long holdStartedAt) {

        public Preview {
            target = target.immutable();
            if (configurationRevision < 0L || stateRevision < 0L || createdAt < 0L || expiresAt <= createdAt || (holdStartedAt != HOLD_NOT_STARTED && holdStartedAt < createdAt)) {
                throw new IllegalArgumentException("Orbital preview timing or revision is invalid");
            }
            if (mode == OrbitalAttackMode.DIRECTED_ENERGY) {
                OrbitalDirectedEnergyStrike.validateSupportedRadius(directedRadius);
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
            return holdStarted() ? Math.max(0L, gameTime - this.holdStartedAt) : 0L;
        }

        public boolean holdStarted() {
            return this.holdStartedAt != HOLD_NOT_STARTED;
        }

        private Preview withHoldStartedAt(long holdStartedAt) {
            return new Preview(
                    this.playerId,
                    this.weaponId,
                    this.mode,
                    this.dimensionId,
                    this.target,
                    this.directedRadius,
                    this.directedDepth,
                    this.nonce,
                    this.configurationRevision,
                    this.stateRevision,
                    this.estimate,
                    this.createdAt,
                    this.expiresAt,
                    holdStartedAt);
        }

        private Preview withoutHold() {
            return withHoldStartedAt(HOLD_NOT_STARTED);
        }
    }
}
