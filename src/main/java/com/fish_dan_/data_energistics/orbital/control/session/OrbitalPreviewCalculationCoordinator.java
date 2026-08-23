package com.fish_dan_.data_energistics.orbital.control.session;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.control.OrbitalAttackPreviewEstimate;
import com.fish_dan_.data_energistics.orbital.control.OrbitalAttackPreviewSessions;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlSessionSnapshot;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlSessionSnapshot.PreviewDetails;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Fair server-tick scheduler for menu-scoped orbital preview calculations and ready/hold sessions. */
public final class OrbitalPreviewCalculationCoordinator {

    private static final long MINIMUM_REQUEST_INTERVAL_TICKS = 5L;
    private static final long REQUEST_HISTORY_TICKS = OrbitalAttackPreviewSessions.DEFAULT_SESSION_TICKS;

    private final OrbitalAttackPreviewSessions previews = new OrbitalAttackPreviewSessions();
    private final Object2ObjectLinkedOpenHashMap<UUID, PendingPreview> calculations = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectOpenHashMap<UUID, BooleanSupplier> readyValidity = new Object2ObjectOpenHashMap<>();
    private final Object2LongOpenHashMap<UUID> lastRequestedAt = new Object2LongOpenHashMap<>();
    private final ObjectOpenHashSet<UUID> rejected = new ObjectOpenHashSet<>();
    private @Nullable MinecraftServer trackedServer;

    /** Replaces the player's old state with a fresh, unadvanced calculation when admission succeeds. */
    public boolean begin(
                         MinecraftServer server,
                         UUID playerId,
                         UUID weaponId,
                         OrbitalAttackMode mode,
                         ResourceLocation dimensionId,
                         BlockPos target,
                         int directedRadius,
                         @Nullable OrbitalDirectedEnergyDepth directedDepth,
                         long configurationRevision,
                         long stateRevision,
                         OrbitalAttackPreviewCalculation calculation,
                         BooleanSupplier stateValid) {
        requireServerThread(server);
        trackServer(server);
        discard(server, playerId);
        long now = server.overworld().getGameTime();
        if (this.lastRequestedAt.containsKey(playerId)) {
            long previous = this.lastRequestedAt.getLong(playerId);
            if (now >= previous && now - previous < MINIMUM_REQUEST_INTERVAL_TICKS) {
                this.rejected.add(playerId);
                return false;
            }
        }
        this.lastRequestedAt.put(playerId, now);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        this.calculations.put(playerId, new PendingPreview(
                playerId,
                weaponId,
                mode,
                dimension,
                dimensionId,
                target,
                directedRadius,
                directedDepth,
                configurationRevision,
                stateRevision,
                calculation,
                stateValid));
        return true;
    }

    /** Advances every queued calculation fairly within the configured per-task and global budgets. */
    public void tick(MinecraftServer server) {
        requireServerThread(server);
        trackServer(server);
        var readyIterator = this.readyValidity.object2ObjectEntrySet().fastIterator();
        while (readyIterator.hasNext()) {
            var entry = readyIterator.next();
            if (!entry.getValue().getAsBoolean()) {
                this.previews.cancel(server, entry.getKey());
                this.rejected.add(entry.getKey());
                readyIterator.remove();
            }
        }
        this.previews.expire(server);
        long now = server.overworld().getGameTime();
        this.lastRequestedAt.object2LongEntrySet()
                .removeIf(entry -> now - entry.getLongValue() >= REQUEST_HISTORY_TICKS);
        int globalRemaining = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon.previewChunkChecksGlobalTick;
        int perTask = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon.previewChunkChecksPerTaskTick;
        int queuedAtStart = this.calculations.size();
        for (int index = 0; index < queuedAtStart && globalRemaining > 0 && !this.calculations.isEmpty(); index++) {
            PendingPreview pending = this.calculations.removeFirst();
            if (!pending.stateValid.getAsBoolean()) {
                this.rejected.add(pending.playerId);
                continue;
            }
            ServerLevel level = server.getLevel(pending.dimension);
            if (level == null) {
                this.rejected.add(pending.playerId);
                continue;
            }
            int allowance = Math.min(perTask, globalRemaining);
            try {
                globalRemaining -= pending.calculation.advance(level, allowance);
                if (pending.calculation.complete()) {
                    Optional<OrbitalAttackPreviewSessions.Preview> accepted = this.previews.begin(
                            server,
                            pending.playerId,
                            pending.mode,
                            pending.dimensionId,
                            pending.target,
                            pending.weaponId,
                            pending.directedRadius,
                            pending.directedDepth,
                            pending.configurationRevision,
                            pending.stateRevision,
                            pending.calculation.finish(),
                            pending.stateValid);
                    if (accepted.isEmpty()) {
                        this.rejected.add(pending.playerId);
                    } else {
                        this.readyValidity.put(pending.playerId, pending.stateValid);
                    }
                } else {
                    this.calculations.put(pending.playerId, pending);
                }
            } catch (RuntimeException exception) {
                this.rejected.add(pending.playerId);
                Data_Energistics.LOGGER.error(
                        "Failed to calculate orbital attack preview {} for player {} at {}",
                        pending.mode,
                        pending.playerId,
                        pending.target,
                        exception);
            }
        }
    }

    /** Returns CALCULATING, READY/HOLDING, REJECTED or IDLE without constructing presentation text. */
    public OrbitalFireControlSessionSnapshot snapshot(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        trackServer(server);
        PendingPreview pending = this.calculations.get(playerId);
        if (pending != null) {
            if (!pending.stateValid.getAsBoolean()) {
                this.calculations.remove(playerId);
                this.rejected.add(playerId);
                return OrbitalFireControlSessionSnapshot.REJECTED;
            }
            return OrbitalFireControlSessionSnapshot.calculating(
                    pending.details(null, null),
                    pending.calculation.checkedChunkCount,
                    pending.calculation.totalChunkCount);
        }
        Optional<OrbitalAttackPreviewSessions.Preview> current = this.previews.current(server, playerId);
        if (current.isPresent()) {
            OrbitalAttackPreviewSessions.Preview preview = current.orElseThrow();
            long gameTime = server.overworld().getGameTime();
            PreviewDetails details = new PreviewDetails(
                    preview.weaponId(),
                    preview.mode(),
                    preview.dimensionId(),
                    preview.target(),
                    preview.directedRadius(),
                    preview.directedDepth(),
                    preview.nonce(),
                    preview.estimate());
            return preview.holdStarted() ? OrbitalFireControlSessionSnapshot.holding(
                    details,
                    Math.min(preview.heldTicks(gameTime), OrbitalAttackPreviewSessions.DEFAULT_HOLD_TICKS),
                    OrbitalAttackPreviewSessions.DEFAULT_HOLD_TICKS,
                    gameTime,
                    preview.expiresAt()) : OrbitalFireControlSessionSnapshot.ready(
                            details,
                            OrbitalAttackPreviewSessions.DEFAULT_HOLD_TICKS,
                            gameTime,
                            preview.expiresAt());
        }
        this.readyValidity.remove(playerId);
        return this.rejected.contains(playerId) ?
                OrbitalFireControlSessionSnapshot.REJECTED : OrbitalFireControlSessionSnapshot.IDLE;
    }

    /** Starts the server-clock hold only for the player's completed matching preview. */
    public boolean startHold(MinecraftServer server, UUID playerId, OrbitalAttackMode mode, UUID nonce) {
        return this.previews.beginHold(server, playerId, mode, nonce);
    }

    /** Cancels an active hold without discarding its still-valid completed preview. */
    public void cancelHold(MinecraftServer server, UUID playerId) {
        this.previews.cancelHold(server, playerId);
    }

    /** Releases a completed hold and consumes its preview when the nonce and mode match. */
    public Optional<OrbitalAttackPreviewSessions.Preview> release(
                                                                  MinecraftServer server,
                                                                  UUID playerId,
                                                                  OrbitalAttackMode mode,
                                                                  UUID nonce) {
        Optional<OrbitalAttackPreviewSessions.Preview> released = this.previews.release(server, playerId, mode, nonce);
        this.readyValidity.remove(playerId);
        return released;
    }

    /** Removes calculation, ready/hold and rejection state while retaining request-rate history. */
    public boolean discard(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        trackServer(server);
        boolean changed = this.calculations.remove(playerId) != null;
        changed |= this.readyValidity.remove(playerId) != null;
        changed |= this.previews.cancel(server, playerId);
        changed |= this.rejected.remove(playerId);
        return changed;
    }

    /** Clears all queued and ready state owned by a stopping server instance. */
    public void clear(MinecraftServer server) {
        if (this.trackedServer != server) {
            return;
        }
        this.previews.clear(server);
        this.trackedServer = null;
        this.calculations.clear();
        this.readyValidity.clear();
        this.lastRequestedAt.clear();
        this.rejected.clear();
    }

    private void trackServer(MinecraftServer server) {
        if (this.trackedServer == server) {
            return;
        }
        this.trackedServer = server;
        this.calculations.clear();
        this.readyValidity.clear();
        this.lastRequestedAt.clear();
        this.rejected.clear();
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Orbital preview coordination may only run on the server thread");
        }
    }

    private record PendingPreview(
                                  UUID playerId,
                                  UUID weaponId,
                                  OrbitalAttackMode mode,
                                  ResourceKey<Level> dimension,
                                  ResourceLocation dimensionId,
                                  BlockPos target,
                                  int directedRadius,
                                  @Nullable OrbitalDirectedEnergyDepth directedDepth,
                                  long configurationRevision,
                                  long stateRevision,
                                  OrbitalAttackPreviewCalculation calculation,
                                  BooleanSupplier stateValid) {

        private PendingPreview {
            target = target.immutable();
        }

        private PreviewDetails details(
                                       @Nullable UUID nonce,
                                       @Nullable OrbitalAttackPreviewEstimate estimate) {
            return new PreviewDetails(
                    this.weaponId,
                    this.mode,
                    this.dimensionId,
                    this.target,
                    this.directedRadius,
                    this.directedDepth,
                    nonce,
                    estimate);
        }
    }
}
