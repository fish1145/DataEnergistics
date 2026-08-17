package com.fish_dan_.data_energistics.entity.explosive;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.DataNukeSchema;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resumable, server-thread block work for one digital-annihilation fuse.
 *
 * <p>
 * The work walks one spherical shell in chunk order and persists its chunk and block cursors. A tick visits at
 * most {@link #BLOCKS_PER_TICK} candidate positions, so a large configured radius cannot turn one server tick into a
 * world-sized mutation burst. Missing chunk futures are created on the server thread; their completion callback only
 * marks readiness by scheduling back onto that same server thread.
 * </p>
 */
public final class DigitalAnnihilationWork {

    public static final int BLOCKS_PER_TICK = 8_192;

    private static final TicketType<UUID> CHUNK_TICKET_TYPE = TicketType.create(
            Data_Energistics.MODID + ":digital_annihilation_work",
            UUID::compareTo);
    private static final int CHUNK_TICKET_DISTANCE = 2;
    private static final double CENTER_Y_OFFSET = 0.5D;
    private static final int SURFACE_INNER_MARGIN = 3;
    private static final int SURFACE_OUTER_MARGIN = 3;
    private static final String TAG_WORK_TICKS = "WorkTicks";
    private static final String TAG_EXPANSION_RADIUS = "ExpansionRadius";
    private static final String TAG_SHELL_RADIUS = "ShellRadius";
    private static final String TAG_SHELL_ACTIVE = "ShellActive";
    private static final String TAG_CHUNK_X = "ChunkX";
    private static final String TAG_CHUNK_Z = "ChunkZ";
    private static final String TAG_BLOCK_CURSOR = "BlockCursor";
    private static final String TAG_SETTINGS_INTERVAL = "SettingsInterval";
    private static final String TAG_SETTINGS_RADIUS = "SettingsRadius";
    private static final String TAG_SETTINGS_CENTER = "SettingsCenter";

    private final BlockPos origin;
    private final UUID ticketOwner;
    private final Settings settings;
    private final LongSet heldTickets = new LongOpenHashSet();

    private int workTicks;
    private int expansionRadius;
    private int shellRadius;
    private boolean shellActive;
    private int currentChunkX;
    private int currentChunkZ;
    private long blockCursor;
    @Nullable
    private ChunkPos pendingChunk;
    private boolean pendingChunkReady;
    private boolean pendingChunkFailed;
    @Nullable
    private String failure;
    private boolean released;

    private DigitalAnnihilationWork(
                                    BlockPos origin,
                                    UUID ticketOwner,
                                    Settings settings,
                                    int workTicks,
                                    int expansionRadius,
                                    int shellRadius,
                                    boolean shellActive,
                                    int currentChunkX,
                                    int currentChunkZ,
                                    long blockCursor) {
        this.origin = origin.immutable();
        this.ticketOwner = ticketOwner;
        this.settings = settings;
        this.workTicks = Math.max(0, workTicks);
        this.expansionRadius = Math.clamp(expansionRadius, 0, this.settings.maxRadius());
        this.shellRadius = Math.clamp(shellRadius, 0, this.settings.maxRadius());
        this.shellActive = shellActive && this.expansionRadius < this.settings.maxRadius();
        this.currentChunkX = currentChunkX;
        this.currentChunkZ = currentChunkZ;
        this.blockCursor = Math.max(0L, blockCursor);
    }

    /** Creates fresh work for a fuse that is about to become active. */
    public static DigitalAnnihilationWork create(BlockPos origin, UUID ticketOwner, DataNukeSchema settings) {
        return create(origin, ticketOwner, Settings.from(settings));
    }

    /**
     * Estimates the complete deterministic candidate volume and the best-case work ticks for a fresh fuse. Chunk
     * waits and the global orbital governor can only increase the returned tick count.
     */
    public static WorkEstimate estimate(ServerLevel level, BlockPos origin, DataNukeSchema settings) {
        return estimate(level, origin, Settings.from(settings));
    }

    private static WorkEstimate estimate(ServerLevel level, BlockPos origin, Settings settings) {
        long scheduledBlocks = 0L;
        long minimumTicks = 0L;
        for (int shellRadius = 1; shellRadius <= settings.maxRadius(); shellRadius++) {
            int outerRadius = Math.min(settings.maxRadius(), shellRadius + SURFACE_OUTER_MARGIN);
            long side = (long) outerRadius * 2L + 1L;
            int minY = Math.max(level.getMinBuildHeight(), origin.getY() - outerRadius);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + outerRadius);
            long candidateCount = Math.multiplyExact(
                    Math.multiplyExact(side, side),
                    (long) maxY - minY + 1L);
            scheduledBlocks = Math.addExact(scheduledBlocks, candidateCount);
            minimumTicks = Math.addExact(
                    minimumTicks,
                    settings.workIntervalTicks() - 1L + divideRoundingUp(candidateCount));
        }
        return new WorkEstimate(scheduledBlocks, minimumTicks);
    }

    /** Creates fresh work from a configuration snapshot captured at attack confirmation or fuse activation. */
    public static DigitalAnnihilationWork create(
                                                 BlockPos origin,
                                                 UUID ticketOwner,
                                                 Settings settings) {
        return new DigitalAnnihilationWork(
                origin,
                ticketOwner,
                settings,
                0,
                0,
                0,
                false,
                0,
                0,
                0L);
    }

    /** Restores work from the entity's persisted state, accepting legacy radius/tick fields. */
    public static DigitalAnnihilationWork restore(
                                                  BlockPos origin,
                                                  UUID ticketOwner,
                                                  DataNukeSchema settings,
                                                  int legacyWorkTicks,
                                                  int legacyExpansionRadius,
                                                  CompoundTag state) {
        return restore(
                origin,
                ticketOwner,
                Settings.from(settings),
                legacyWorkTicks,
                legacyExpansionRadius,
                state);
    }

    /** Restores work using an immutable settings snapshot, with no live-config lookup. */
    public static DigitalAnnihilationWork restore(
                                                  BlockPos origin,
                                                  UUID ticketOwner,
                                                  Settings settings,
                                                  int legacyWorkTicks,
                                                  int legacyExpansionRadius,
                                                  CompoundTag state) {
        Settings capturedSettings = state.contains(TAG_SETTINGS_INTERVAL) && state.contains(TAG_SETTINGS_RADIUS) && state.contains(TAG_SETTINGS_CENTER) ? new Settings(
                state.getInt(TAG_SETTINGS_INTERVAL),
                state.getInt(TAG_SETTINGS_RADIUS),
                state.getDouble(TAG_SETTINGS_CENTER)) : settings;
        return new DigitalAnnihilationWork(
                origin,
                ticketOwner,
                capturedSettings,
                state.contains(TAG_WORK_TICKS) ? state.getInt(TAG_WORK_TICKS) : legacyWorkTicks,
                state.contains(TAG_EXPANSION_RADIUS) ? state.getInt(TAG_EXPANSION_RADIUS) : legacyExpansionRadius,
                state.getInt(TAG_SHELL_RADIUS),
                state.getBoolean(TAG_SHELL_ACTIVE),
                state.getInt(TAG_CHUNK_X),
                state.getInt(TAG_CHUNK_Z),
                state.getLong(TAG_BLOCK_CURSOR));
    }

    /** Serializes the resumable shell and block cursors into an already-owned NBT compound. */
    public void save(CompoundTag tag) {
        tag.putInt(TAG_WORK_TICKS, this.workTicks);
        tag.putInt(TAG_EXPANSION_RADIUS, this.expansionRadius);
        tag.putInt(TAG_SHELL_RADIUS, this.shellRadius);
        tag.putBoolean(TAG_SHELL_ACTIVE, this.shellActive);
        tag.putInt(TAG_CHUNK_X, this.currentChunkX);
        tag.putInt(TAG_CHUNK_Z, this.currentChunkZ);
        tag.putLong(TAG_BLOCK_CURSOR, this.blockCursor);
        tag.putInt(TAG_SETTINGS_INTERVAL, this.settings.workIntervalTicks());
        tag.putInt(TAG_SETTINGS_RADIUS, this.settings.maxRadius());
        tag.putDouble(TAG_SETTINGS_CENTER, this.settings.centerEntityConsumeRadius());
    }

    /** Advances the shell work on the server thread and reports the boundary reached this tick. */
    public TickResult tick(ServerLevel level) {
        ensureServerThread(level);
        if (this.failure != null) {
            return new TickResult(State.FAULTED, 0, 0);
        }
        if (this.released) {
            return new TickResult(State.FAULTED, 0, 0);
        }
        if (!this.shellActive) {
            if (this.expansionRadius >= this.settings.maxRadius()) {
                return new TickResult(State.FINISHED, 0, 0);
            }
            if (this.workTicks + 1 < this.settings.workIntervalTicks()) {
                this.workTicks++;
                return new TickResult(State.WAITING, 0, 0);
            }
            this.workTicks = 0;
            beginShell(level);
        }
        return processShell(level);
    }

    /** Releases all work tickets and prevents a late future callback from resurrecting the work. */
    public void release(ServerLevel level) {
        ensureServerThread(level);
        this.released = true;
        this.pendingChunk = null;
        this.pendingChunkReady = false;
        this.pendingChunkFailed = false;
        for (long packedChunk : this.heldTickets) {
            ChunkPos chunk = new ChunkPos(packedChunk);
            level.getChunkSource().removeRegionTicket(
                    CHUNK_TICKET_TYPE,
                    chunk,
                    CHUNK_TICKET_DISTANCE,
                    this.ticketOwner,
                    true);
        }
        this.heldTickets.clear();
    }

    public BlockPos origin() {
        return this.origin;
    }

    public int expansionRadius() {
        return this.expansionRadius;
    }

    public int workTicks() {
        return this.workTicks;
    }

    public long blockCursor() {
        return this.blockCursor;
    }

    public double centerEntityConsumeRadius() {
        return this.settings.centerEntityConsumeRadius();
    }

    public boolean isFinished() {
        return this.expansionRadius >= this.settings.maxRadius() && !this.shellActive;
    }

    @Nullable
    public String failure() {
        return this.failure;
    }

    private void beginShell(ServerLevel level) {
        this.shellRadius = Math.min(this.settings.maxRadius(), this.expansionRadius + 1);
        this.shellActive = true;
        this.blockCursor = 0L;
        int outerRadius = outerRadius();
        this.currentChunkX = Math.floorDiv(this.origin.getX() - outerRadius, 16);
        this.currentChunkZ = Math.floorDiv(this.origin.getZ() - outerRadius, 16);
        this.pendingChunk = null;
        this.pendingChunkReady = false;
        this.pendingChunkFailed = false;
        for (long packedChunk : this.heldTickets.toLongArray()) {
            releaseTicket(level, new ChunkPos(packedChunk));
        }
    }

    private TickResult processShell(ServerLevel level) {
        int visited = 0;
        int changed = 0;
        while (visited < BLOCKS_PER_TICK) {
            if (this.pendingChunk != null) {
                if (!this.pendingChunkReady) {
                    return new TickResult(State.WAITING_FOR_CHUNK, visited, changed);
                }
                ChunkPos completedRequest = this.pendingChunk;
                this.pendingChunk = null;
                boolean failed = this.pendingChunkFailed;
                this.pendingChunkReady = false;
                this.pendingChunkFailed = false;
                if (failed) {
                    this.failure = "Chunk future failed for " + completedRequest;
                    return new TickResult(State.FAULTED, visited, changed);
                }
            }

            ChunkBounds bounds = chunkBounds(level, this.currentChunkX, this.currentChunkZ);
            if (bounds.isEmpty()) {
                if (advanceChunk(level)) {
                    return shellCompleted(visited, changed);
                }
                continue;
            }

            ChunkPos current = new ChunkPos(this.currentChunkX, this.currentChunkZ);
            LevelChunk chunk = level.getChunkSource().getChunkNow(this.currentChunkX, this.currentChunkZ);
            if (chunk == null) {
                requestChunk(level, current);
                return new TickResult(State.WAITING_FOR_CHUNK, visited, changed);
            }
            holdTicket(level, current);

            long candidateCount = bounds.candidateCount();
            if (this.blockCursor >= candidateCount) {
                releaseTicket(level, current);
                if (advanceChunk(level)) {
                    return shellCompleted(visited, changed);
                }
                continue;
            }

            BlockPos target = bounds.position(this.blockCursor++);
            visited++;
            if (!shouldClear(target, bounds.innerRadiusSqr(), bounds.outerRadiusSqr())) {
                continue;
            }
            if (!level.getBlockState(target).isAir()) {
                level.setBlock(
                        target,
                        Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                changed++;
            }
        }
        return new TickResult(State.WORKING, visited, changed);
    }

    private TickResult shellCompleted(int visited, int changed) {
        this.shellActive = false;
        this.expansionRadius = this.shellRadius;
        this.blockCursor = 0L;
        return this.expansionRadius >= this.settings.maxRadius() ? new TickResult(State.FINISHED, visited, changed) : new TickResult(State.SHELL_COMPLETED, visited, changed);
    }

    private boolean advanceChunk(ServerLevel level) {
        releaseTicket(level, new ChunkPos(this.currentChunkX, this.currentChunkZ));
        int maxChunkX = Math.floorDiv(this.origin.getX() + outerRadius(), 16);
        int maxChunkZ = Math.floorDiv(this.origin.getZ() + outerRadius(), 16);
        if (this.currentChunkZ < maxChunkZ) {
            this.currentChunkZ++;
            this.blockCursor = 0L;
            return false;
        }
        if (this.currentChunkX < maxChunkX) {
            this.currentChunkX++;
            this.currentChunkZ = Math.floorDiv(this.origin.getZ() - outerRadius(), 16);
            this.blockCursor = 0L;
            return false;
        }
        return true;
    }

    private void requestChunk(ServerLevel level, ChunkPos chunk) {
        if (chunk.equals(this.pendingChunk)) {
            return;
        }
        if (this.pendingChunk != null) {
            return;
        }
        holdTicket(level, chunk);
        this.pendingChunk = chunk;
        this.pendingChunkReady = false;
        this.pendingChunkFailed = false;
        CompletableFuture<ChunkResult<ChunkAccess>> future = level.getChunkSource()
                .getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true);
        future.whenComplete((result, throwable) -> level.getServer().execute(() -> {
            if (this.released || !chunk.equals(this.pendingChunk)) {
                return;
            }
            this.pendingChunkFailed = throwable != null || result == null || !result.isSuccess();
            this.pendingChunkReady = true;
        }));
    }

    private void holdTicket(ServerLevel level, ChunkPos chunk) {
        if (this.heldTickets.add(chunk.toLong())) {
            level.getChunkSource().addRegionTicket(
                    CHUNK_TICKET_TYPE,
                    chunk,
                    CHUNK_TICKET_DISTANCE,
                    this.ticketOwner,
                    true);
        }
    }

    private void releaseTicket(ServerLevel level, ChunkPos chunk) {
        if (this.heldTickets.remove(chunk.toLong())) {
            level.getChunkSource().removeRegionTicket(
                    CHUNK_TICKET_TYPE,
                    chunk,
                    CHUNK_TICKET_DISTANCE,
                    this.ticketOwner,
                    true);
        }
    }

    private int outerRadius() {
        return Math.min(this.settings.maxRadius(), this.shellRadius + SURFACE_OUTER_MARGIN);
    }

    private boolean shouldClear(BlockPos target, double innerRadiusSqr, double outerRadiusSqr) {
        int offsetX = target.getX() - this.origin.getX();
        int offsetZ = target.getZ() - this.origin.getZ();
        double horizontalDistanceSqr = offsetX * offsetX + offsetZ * offsetZ;
        if (horizontalDistanceSqr > outerRadiusSqr) {
            return false;
        }
        double dy = target.getY() - this.origin.getY();
        double distanceSqr = horizontalDistanceSqr + dy * dy;
        return distanceSqr >= innerRadiusSqr && distanceSqr <= outerRadiusSqr;
    }

    private ChunkBounds chunkBounds(ServerLevel level, int chunkX, int chunkZ) {
        int outerRadius = outerRadius();
        int minX = this.origin.getX() - outerRadius;
        int maxX = this.origin.getX() + outerRadius;
        int minZ = this.origin.getZ() - outerRadius;
        int maxZ = this.origin.getZ() + outerRadius;
        int minY = Math.max(
                level.getMinBuildHeight(),
                this.origin.getY() + (int) Math.floor(CENTER_Y_OFFSET - outerRadius - 0.5D));
        int maxY = Math.min(
                level.getMaxBuildHeight() - 1,
                this.origin.getY() + (int) Math.ceil(CENTER_Y_OFFSET + outerRadius - 0.5D));
        int startX = Math.max(minX, chunkX << 4);
        int endX = Math.min(maxX, (chunkX << 4) + 15);
        int startZ = Math.max(minZ, chunkZ << 4);
        int endZ = Math.min(maxZ, (chunkZ << 4) + 15);
        return new ChunkBounds(
                startX,
                endX,
                startZ,
                endZ,
                minY,
                maxY,
                Math.max(0, this.shellRadius - SURFACE_INNER_MARGIN),
                outerRadius);
    }

    private static void ensureServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Digital annihilation work must run on the server thread");
        }
    }

    private static long divideRoundingUp(long value) {
        return value == 0L ? 0L : 1L + (value - 1L) / BLOCKS_PER_TICK;
    }

    public enum State {
        WAITING,
        WAITING_FOR_CHUNK,
        WORKING,
        SHELL_COMPLETED,
        FINISHED,
        FAULTED
    }

    public record TickResult(State state, int visitedBlocks, int changedBlocks) {}

    /** Best-case work estimate before asynchronous chunk waits and shared-budget contention. */
    public record WorkEstimate(long scheduledBlocks, long minimumTicks) {

        public WorkEstimate {
            if (scheduledBlocks < 0L || minimumTicks < 0L) {
                throw new IllegalArgumentException("Digital annihilation work estimate must not be negative");
            }
        }
    }

    /** Persisted cursor values; booleans distinguish absent legacy fields from an explicit zero. */
    public record Settings(int workIntervalTicks, int maxRadius, double centerEntityConsumeRadius) {

        public Settings {
            if (workIntervalTicks < 1 || maxRadius < 1 || !Double.isFinite(centerEntityConsumeRadius) || centerEntityConsumeRadius < 0.0D) {
                throw new IllegalArgumentException("Invalid digital annihilation work settings");
            }
        }

        private static Settings from(DataNukeSchema settings) {
            return new Settings(
                    settings.workIntervalTicks(),
                    settings.maxRadius(),
                    settings.centerEntityConsumeRadius());
        }
    }

    private record ChunkBounds(
                               int startX,
                               int endX,
                               int startZ,
                               int endZ,
                               int minY,
                               int maxY,
                               int innerRadius,
                               int outerRadius) {

        private boolean isEmpty() {
            return this.startX > this.endX || this.startZ > this.endZ || this.minY > this.maxY;
        }

        private long candidateCount() {
            return (long) (this.endX - this.startX + 1) * (long) (this.endZ - this.startZ + 1) * (long) (this.maxY - this.minY + 1);
        }

        private BlockPos position(long cursor) {
            long layerSize = (long) (this.endX - this.startX + 1) * (this.endZ - this.startZ + 1);
            int width = this.endX - this.startX + 1;
            long yIndex = cursor / layerSize;
            long horizontal = cursor % layerSize;
            int z = this.startZ + (int) (horizontal / width);
            int x = this.startX + (int) (horizontal % width);
            return new BlockPos(x, this.minY + (int) yIndex, z);
        }

        private double innerRadiusSqr() {
            return (double) this.innerRadius * this.innerRadius;
        }

        private double outerRadiusSqr() {
            return (double) this.outerRadius * this.outerRadius;
        }
    }
}
