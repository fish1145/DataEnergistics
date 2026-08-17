package com.fish_dan_.data_energistics.orbital.attack.work;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;

import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Server-thread governor for future-backed kinetic and directed-energy terrain work.
 *
 * <p>
 * Runtime futures and region tickets are deliberately not serialized. The attack record persists the deterministic
 * block cursor and work boundary; after a restart the same cursor reacquires its next ticket and future. Ticket,
 * generation and mutation limits are read from the current immutable configuration snapshot at the start of each
 * server tick.
 * </p>
 */
public final class OrbitalTerrainWorkScheduler {

    private static final TicketType<UUID> CHUNK_TICKET_TYPE = TicketType.create(
            Data_Energistics.MODID + ":orbital_terrain_work",
            UUID::compareTo);
    private static final int CHUNK_TICKET_DISTANCE = 2;

    private final Map<UUID, TaskState> tasks = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Integer> pendingRequestsByDimension = new HashMap<>();
    private final Map<UUID, Integer> mutationsReservedByTask = new HashMap<>();

    private int maxTicketsPerTask;
    private int maxTicketsGlobal;
    private int maxRequestsPerDimension;
    private int maxRequestsGlobal;
    private int maxMutationsPerTask;
    private int heldTicketCount;
    private int pendingRequestCount;
    private int remainingMutationBudget;
    private boolean tickOpen;

    /**
     * Captures the current limits and opens a fresh global mutation budget for one server tick.
     */
    public void beginTick(MinecraftServer server, DataEnergisticsSettings.OrbitalWeapon settings) {
        requireServerThread(server);
        this.maxTicketsPerTask = settings.maxAttackChunkTicketsPerTask();
        this.maxTicketsGlobal = settings.maxAttackChunkTicketsGlobal();
        this.maxRequestsPerDimension = settings.maxAttackChunkGenerationPerDimension();
        this.maxRequestsGlobal = settings.maxAttackChunkGenerationGlobal();
        this.maxMutationsPerTask = settings.maxAttackBlockMutationsPerTaskTick();
        this.remainingMutationBudget = settings.maxAttackBlockMutationsGlobalTick();
        this.mutationsReservedByTask.clear();
        this.tickOpen = true;
        trimTicketsToLimits(server);
    }

    /**
     * Reserves a fair, bounded terrain-position allowance for the supplied task in the current tick.
     */
    public int reserveMutationBudget(UUID attackId) {
        requireOpenTick();
        int alreadyReserved = this.mutationsReservedByTask.getOrDefault(attackId, 0);
        int taskRemaining = Math.max(0, this.maxMutationsPerTask - alreadyReserved);
        int granted = Math.min(taskRemaining, this.remainingMutationBudget);
        if (granted > 0) {
            this.mutationsReservedByTask.put(attackId, alreadyReserved + granted);
            this.remainingMutationBudget -= granted;
        }
        return granted;
    }

    /**
     * Returns an unused portion of a previously granted mutation allowance to the current global tick budget.
     */
    public void settleMutationBudget(UUID attackId, int granted, int visited) {
        requireOpenTick();
        if (granted < 0 || visited < 0 || visited > granted) {
            throw new IllegalArgumentException("Invalid orbital terrain mutation settlement");
        }
        int reserved = this.mutationsReservedByTask.getOrDefault(attackId, 0);
        if (granted > reserved) {
            throw new IllegalStateException("Orbital terrain task settled more mutations than it reserved");
        }
        int unused = granted - visited;
        int retained = reserved - unused;
        if (retained == 0) {
            this.mutationsReservedByTask.remove(attackId);
        } else {
            this.mutationsReservedByTask.put(attackId, retained);
        }
        this.remainingMutationBudget = Math.addExact(this.remainingMutationBudget, unused);
    }

    /**
     * Ensures the requested chunk is held by an attack ticket and available at FULL status without synchronously
     * generating it. The caller must stop at the current persisted cursor unless this method returns {@link
     * ChunkReadiness#READY}.
     */
    public ChunkReadiness prepareChunk(ServerLevel level, UUID attackId, ChunkPos chunk) {
        requireOpenTick();
        requireServerThread(level.getServer());
        TaskState task = this.tasks.computeIfAbsent(attackId, ignored -> new TaskState(level.dimension()));
        if (!task.dimension.equals(level.dimension())) {
            throw new IllegalStateException("An orbital terrain task cannot change target dimensions");
        }
        if (task.failure != null) {
            return setReadiness(task, ChunkReadiness.FAULTED);
        }

        PendingRequest pending = task.pendingRequest;
        if (pending != null) {
            if (!pending.chunk.equals(chunk)) {
                return setReadiness(task, ChunkReadiness.WAITING_FOR_CHUNK);
            }
            if (!pending.complete) {
                return setReadiness(task, ChunkReadiness.WAITING_FOR_CHUNK);
            }
            task.pendingRequest = null;
            if (pending.failed) {
                task.failure = "Chunk future failed for " + pending.chunk;
                return setReadiness(task, ChunkReadiness.FAULTED);
            }
        }

        if (!holdTicket(level, attackId, task, chunk)) {
            return setReadiness(task, ChunkReadiness.WAITING_FOR_BUDGET);
        }
        if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
            return setReadiness(task, ChunkReadiness.READY);
        }
        if (this.pendingRequestCount >= this.maxRequestsGlobal || this.pendingRequestsByDimension.getOrDefault(level.dimension(), 0) >= this.maxRequestsPerDimension) {
            return setReadiness(task, ChunkReadiness.WAITING_FOR_BUDGET);
        }

        startChunkRequest(level, attackId, task, chunk);
        return setReadiness(task, ChunkReadiness.WAITING_FOR_CHUNK);
    }

    /**
     * Returns the most recent readiness boundary reached by a terrain task.
     */
    public ChunkReadiness lastReadiness(UUID attackId) {
        TaskState task = this.tasks.get(attackId);
        return task == null ? ChunkReadiness.READY : task.lastReadiness;
    }

    /**
     * Returns one stable failure description after a chunk future has faulted.
     */
    @Nullable
    public String failure(UUID attackId) {
        TaskState task = this.tasks.get(attackId);
        return task == null ? null : task.failure;
    }

    /**
     * Releases every future permit and region ticket retained by one completed, aborted or faulted task.
     */
    public void release(MinecraftServer server, UUID attackId) {
        requireServerThread(server);
        TaskState task = this.tasks.remove(attackId);
        if (task == null) {
            return;
        }
        releasePendingPermit(task);
        ServerLevel level = server.getLevel(task.dimension);
        if (level == null) {
            this.heldTicketCount -= task.heldTickets.size();
            task.heldTickets.clear();
            return;
        }
        for (ChunkPos chunk : Set.copyOf(task.heldTickets)) {
            releaseTicket(level, attackId, task, chunk);
        }
    }

    /**
     * Releases runtime resources whose persisted attack no longer owns an active terrain delivery.
     */
    public void releaseMissing(MinecraftServer server, Set<UUID> liveAttackIds) {
        requireServerThread(server);
        for (UUID attackId : Set.copyOf(this.tasks.keySet())) {
            if (!liveAttackIds.contains(attackId)) {
                release(server, attackId);
            }
        }
    }

    /**
     * Drops completed-chunk lookbehind tickets after one task's turn while retaining its newest or pending chunk. This
     * prevents early tasks from monopolizing the global ticket pool across ticks.
     */
    public void endTaskTick(MinecraftServer server, UUID attackId) {
        requireServerThread(server);
        TaskState task = this.tasks.get(attackId);
        if (task == null) {
            return;
        }
        ServerLevel level = server.getLevel(task.dimension);
        while (level != null && task.heldTickets.size() > 1) {
            releaseOldestTicket(level, attackId, task);
        }
    }

    /**
     * Releases all runtime resources before the server's dimensions shut down.
     */
    public void releaseAll(MinecraftServer server) {
        requireServerThread(server);
        for (UUID attackId : Set.copyOf(this.tasks.keySet())) {
            release(server, attackId);
        }
        this.pendingRequestsByDimension.clear();
        this.mutationsReservedByTask.clear();
        this.pendingRequestCount = 0;
        this.heldTicketCount = 0;
        this.remainingMutationBudget = 0;
        this.tickOpen = false;
    }

    private void startChunkRequest(ServerLevel level, UUID attackId, TaskState task, ChunkPos chunk) {
        long requestId = ++task.nextRequestId;
        PendingRequest request = new PendingRequest(chunk, requestId);
        task.pendingRequest = request;
        this.pendingRequestCount++;
        this.pendingRequestsByDimension.merge(level.dimension(), 1, Integer::sum);

        CompletableFuture<ChunkResult<ChunkAccess>> future = CompletableFuture
                .supplyAsync(
                        () -> level.getChunkSource().getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true),
                        Util.backgroundExecutor())
                .thenCompose(Function.identity());
        future.whenComplete((result, throwable) -> level.getServer().execute(() -> {
            TaskState currentTask = this.tasks.get(attackId);
            if (currentTask == null || currentTask.pendingRequest == null || currentTask.pendingRequest.requestId != requestId) {
                return;
            }
            PendingRequest currentRequest = currentTask.pendingRequest;
            releasePendingPermit(currentTask);
            currentRequest.failed = throwable != null || result == null || !result.isSuccess();
            currentRequest.complete = true;
        }));
    }

    private boolean holdTicket(
                               ServerLevel level,
                               UUID attackId,
                               TaskState task,
                               ChunkPos chunk) {
        if (task.heldTickets.contains(chunk)) {
            if (!chunk.equals(task.mostRecentChunk)) {
                task.heldTickets.remove(chunk);
                task.heldTickets.add(chunk);
                task.mostRecentChunk = chunk;
            }
            return true;
        }
        while (task.heldTickets.size() >= this.maxTicketsPerTask) {
            releaseOldestTicket(level, attackId, task);
        }
        if (this.heldTicketCount >= this.maxTicketsGlobal && !task.heldTickets.isEmpty()) {
            releaseOldestTicket(level, attackId, task);
        }
        if (this.heldTicketCount >= this.maxTicketsGlobal) {
            return false;
        }
        level.getChunkSource().addRegionTicket(
                CHUNK_TICKET_TYPE,
                chunk,
                CHUNK_TICKET_DISTANCE,
                attackId,
                true);
        task.heldTickets.add(chunk);
        task.mostRecentChunk = chunk;
        this.heldTicketCount++;
        return true;
    }

    private void releaseOldestTicket(ServerLevel level, UUID attackId, TaskState task) {
        Iterator<ChunkPos> iterator = task.heldTickets.iterator();
        if (!iterator.hasNext()) {
            throw new IllegalStateException("Orbital terrain ticket accounting lost its task ticket");
        }
        releaseTicket(level, attackId, task, iterator.next());
    }

    private void releaseTicket(ServerLevel level, UUID attackId, TaskState task, ChunkPos chunk) {
        if (!task.heldTickets.remove(chunk)) {
            return;
        }
        level.getChunkSource().removeRegionTicket(
                CHUNK_TICKET_TYPE,
                chunk,
                CHUNK_TICKET_DISTANCE,
                attackId,
                true);
        this.heldTicketCount--;
    }

    private void releasePendingPermit(TaskState task) {
        PendingRequest pending = task.pendingRequest;
        if (pending == null || !pending.permitHeld) {
            return;
        }
        pending.permitHeld = false;
        this.pendingRequestCount--;
        int remaining = this.pendingRequestsByDimension.getOrDefault(task.dimension, 0) - 1;
        if (remaining < 0 || this.pendingRequestCount < 0) {
            throw new IllegalStateException("Orbital chunk-request permit accounting became negative");
        }
        if (remaining == 0) {
            this.pendingRequestsByDimension.remove(task.dimension);
        } else {
            this.pendingRequestsByDimension.put(task.dimension, remaining);
        }
    }

    private void trimTicketsToLimits(MinecraftServer server) {
        for (Map.Entry<UUID, TaskState> entry : this.tasks.entrySet()) {
            ServerLevel level = server.getLevel(entry.getValue().dimension);
            if (level == null) {
                this.heldTicketCount -= entry.getValue().heldTickets.size();
                entry.getValue().heldTickets.clear();
                continue;
            }
            while (entry.getValue().heldTickets.size() > this.maxTicketsPerTask) {
                releaseOldestTicket(level, entry.getKey(), entry.getValue());
            }
        }
        if (this.heldTicketCount <= this.maxTicketsGlobal) {
            return;
        }
        for (Map.Entry<UUID, TaskState> entry : this.tasks.entrySet()) {
            ServerLevel level = server.getLevel(entry.getValue().dimension);
            while (level != null && this.heldTicketCount > this.maxTicketsGlobal && !entry.getValue().heldTickets.isEmpty()) {
                releaseOldestTicket(level, entry.getKey(), entry.getValue());
            }
        }
    }

    private static ChunkReadiness setReadiness(TaskState task, ChunkReadiness readiness) {
        task.lastReadiness = readiness;
        return readiness;
    }

    private void requireOpenTick() {
        if (!this.tickOpen) {
            throw new IllegalStateException("Orbital terrain scheduler was used outside its server tick");
        }
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Orbital terrain work may only be scheduled on the server thread");
        }
    }

    /**
     * Result of attempting to acquire one FULL attack work chunk.
     */
    public enum ChunkReadiness {
        READY,
        WAITING_FOR_CHUNK,
        WAITING_FOR_BUDGET,
        FAULTED
    }

    private static final class TaskState {

        private final ResourceKey<Level> dimension;
        private final Set<ChunkPos> heldTickets = new LinkedHashSet<>();
        private long nextRequestId;
        @Nullable
        private ChunkPos mostRecentChunk;
        @Nullable
        private PendingRequest pendingRequest;
        @Nullable
        private String failure;
        private ChunkReadiness lastReadiness = ChunkReadiness.READY;

        private TaskState(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    private static final class PendingRequest {

        private final ChunkPos chunk;
        private final long requestId;
        private boolean permitHeld = true;
        private boolean complete;
        private boolean failed;

        private PendingRequest(ChunkPos chunk, long requestId) {
            this.chunk = chunk;
            this.requestId = requestId;
        }
    }
}
