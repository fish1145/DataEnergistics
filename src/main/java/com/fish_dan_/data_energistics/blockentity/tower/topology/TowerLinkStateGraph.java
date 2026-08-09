package com.fish_dan_.data_energistics.blockentity.tower.topology;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores deterministic in-memory AE link state for a Data Distribution Tower.
 *
 * <p>
 * The tower uses this graph to distinguish persisted targets, retry candidates, and live AE grid connections without
 * exposing map/set mutation details to higher-level transfer logic.
 * </p>
 */
public final class TowerLinkStateGraph {

    /**
     * Describes the runtime AE connection lifecycle for one persisted target.
     */
    public enum TargetLinkState {
        BOUND,
        WAITING_TARGET,
        PENDING,
        ALLOCATED,
        WAITING_CHANNEL,
        DISABLED,
        CONFLICT,
        BRIDGE_ERROR,
        INVALID
    }

    /**
     * Records the most recent retryable reason without retaining AE runtime objects.
     */
    public enum TargetLinkFailure {
        NONE,
        TARGET_UNAVAILABLE,
        CHANNEL_UNAVAILABLE,
        CONTROLLER_PRESENT,
        OWNERSHIP_CONFLICT,
        BRIDGE_CYCLE,
        SCOPE_CONFLICT,
        GRID_SERVICE_REGISTRATION
    }

    /**
     * Immutable runtime snapshot for one target's connection state.
     *
     * @param state      current lifecycle state
     * @param failure    most recent retryable failure category
     * @param retryTicks AE ticks remaining before the next attempt
     */
    public record TargetLinkStatus(TargetLinkState state, TargetLinkFailure failure, int retryTicks) {

        public TargetLinkStatus {
            if (retryTicks < 0) {
                throw new IllegalArgumentException("Target link retry ticks must be non-negative: " + retryTicks);
            }
        }

        /**
         * Checks whether the status should be reconsidered by a later AE tick.
         *
         * @return true when the target requires a later AE reconciliation, including a low-frequency health check
         */
        public boolean isRetryable() {
            return switch (this.state) {
                case WAITING_TARGET, PENDING, WAITING_CHANNEL, CONFLICT, BRIDGE_ERROR -> true;
                case BOUND, ALLOCATED, DISABLED, INVALID -> false;
            };
        }
    }

    private static final int MAX_RETRY_ATTEMPTS = 30;
    private final Set<BlockPos> linkedPositions = new LinkedHashSet<>();
    private final Map<BlockPos, TargetLinkStatus> targetStatuses = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> retryAttempts = new LinkedHashMap<>();
    private static final TargetLinkStatus INVALID_STATUS = new TargetLinkStatus(
            TargetLinkState.INVALID, TargetLinkFailure.NONE, 0);

    /**
     * Removes all persisted, pending, and live connection state.
     */
    public void clear() {
        this.targetStatuses.clear();
        this.retryAttempts.clear();
        this.linkedPositions.clear();
    }

    /**
     * Adds a persisted target position.
     *
     * @param targetPos target to remember
     */
    public void addLinked(BlockPos targetPos) {
        BlockPos normalizedPos = targetPos.immutable();
        if (this.linkedPositions.add(normalizedPos)) {
            this.targetStatuses.put(normalizedPos, new TargetLinkStatus(
                    TargetLinkState.BOUND, TargetLinkFailure.NONE, 0));
        }
    }

    /**
     * Adds multiple persisted targets.
     *
     * @param positions targets to add
     */
    public void addLinkedAll(Collection<BlockPos> positions) {
        for (BlockPos pos : positions) {
            addLinked(pos);
        }
    }

    /**
     * Removes a persisted target position.
     *
     * @param targetPos target to remove
     */
    public void removeLinked(BlockPos targetPos) {
        BlockPos normalizedPos = targetPos.immutable();
        this.linkedPositions.remove(normalizedPos);
        this.targetStatuses.remove(normalizedPos);
        this.retryAttempts.remove(normalizedPos);
    }

    /**
     * Checks whether the target is persisted.
     *
     * @param targetPos target position
     * @return true when the target is linked
     */
    public boolean containsLinked(BlockPos targetPos) {
        return this.linkedPositions.contains(targetPos);
    }

    /**
     * Returns persisted target positions in deterministic iteration order.
     *
     * @return immutable linked position snapshot
     */
    public Set<BlockPos> linkedPositions() {
        return new LinkedHashSet<>(this.linkedPositions);
    }

    /**
     * Clears runtime connection state while preserving persisted target identities.
     */
    public void resetRuntimeState() {
        this.targetStatuses.clear();
        this.retryAttempts.clear();
        for (BlockPos targetPos : this.linkedPositions) {
            this.targetStatuses.put(targetPos, new TargetLinkStatus(
                    TargetLinkState.BOUND, TargetLinkFailure.NONE, 0));
        }
    }

    /**
     * Updates one persisted target's runtime state.
     *
     * @param targetPos  target position
     * @param state      next lifecycle state
     * @param failure    current retryable failure category
     * @param retryTicks AE ticks before the next attempt
     * @return true when the runtime state changed
     */
    public boolean transition(BlockPos targetPos, TargetLinkState state, TargetLinkFailure failure, int retryTicks) {
        BlockPos normalizedPos = targetPos.immutable();
        if (!this.linkedPositions.contains(normalizedPos)) {
            return false;
        }
        TargetLinkStatus nextStatus = new TargetLinkStatus(state, failure, retryTicks);
        TargetLinkStatus previousStatus = this.targetStatuses.put(normalizedPos, nextStatus);
        this.retryAttempts.remove(normalizedPos);
        return !nextStatus.equals(previousStatus);
    }

    /**
     * Schedules a retryable state using a bounded per-target exponential backoff.
     *
     * <p>
     * A normal {@link #transition(BlockPos, TargetLinkState, TargetLinkFailure, int)} resets the accumulated backoff.
     * Lifecycle wake-ups can therefore retry immediately, while repeated unchanged failures remain rate-limited.
     * </p>
     *
     * @param targetPos         target position
     * @param state             retryable lifecycle state
     * @param failure           current retryable failure category
     * @param initialDelayTicks delay for the first retry
     * @param maximumDelayTicks maximum bounded retry delay
     * @return true when the runtime state changed
     */
    public boolean scheduleRetry(BlockPos targetPos, TargetLinkState state, TargetLinkFailure failure,
                                 int initialDelayTicks, int maximumDelayTicks) {
        if (initialDelayTicks <= 0 || maximumDelayTicks < initialDelayTicks) {
            throw new IllegalArgumentException("Target link retry delays must satisfy 0 < initial <= maximum");
        }

        BlockPos normalizedPos = targetPos.immutable();
        if (!this.linkedPositions.contains(normalizedPos)) {
            return false;
        }

        TargetLinkStatus previousStatus = this.targetStatuses.get(normalizedPos);
        int retryAttempt = previousStatus != null && previousStatus.state() == state && previousStatus.failure() == failure ? Math.min(MAX_RETRY_ATTEMPTS, this.retryAttempts.getOrDefault(normalizedPos, 0) + 1) : 1;
        int retryTicks = retryDelay(initialDelayTicks, maximumDelayTicks, retryAttempt);
        TargetLinkStatus nextStatus = new TargetLinkStatus(state, failure, retryTicks);
        this.targetStatuses.put(normalizedPos, nextStatus);
        this.retryAttempts.put(normalizedPos, retryAttempt);
        return !nextStatus.equals(previousStatus);
    }

    /**
     * Returns one target's runtime status.
     *
     * @param targetPos target position
     * @return current status, or {@link TargetLinkState#INVALID} when the target is not persisted
     */
    public TargetLinkStatus status(BlockPos targetPos) {
        return this.targetStatuses.getOrDefault(targetPos, INVALID_STATUS);
    }

    /**
     * Advances retry timers using elapsed AE ticks and returns every target now ready for reconciliation.
     *
     * @param elapsedTicks elapsed ticks reported by the AE tick manager
     * @return deterministic snapshot of retry-ready target positions
     */
    public List<BlockPos> advanceRetryClock(int elapsedTicks) {
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("Target link elapsed ticks must be non-negative: " + elapsedTicks);
        }

        ArrayList<BlockPos> readyTargets = new ArrayList<>();
        for (Map.Entry<BlockPos, TargetLinkStatus> entry : this.targetStatuses.entrySet()) {
            TargetLinkStatus status = entry.getValue();
            if (!status.isRetryable()) {
                continue;
            }

            int remainingTicks = Math.max(0, status.retryTicks() - elapsedTicks);
            if (remainingTicks != status.retryTicks()) {
                entry.setValue(new TargetLinkStatus(status.state(), status.failure(), remainingTicks));
            }
            if (remainingTicks == 0) {
                readyTargets.add(entry.getKey());
            }
        }
        return List.copyOf(readyTargets);
    }

    /**
     * Checks whether any persisted target is scheduled for a later AE reconciliation attempt.
     *
     * @return true when retryable or health-check runtime work remains
     */
    public boolean hasRetryableTargets() {
        for (TargetLinkStatus status : this.targetStatuses.values()) {
            if (status.isRetryable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns every persisted target position in deterministic iteration order.
     *
     * @return immutable tracked position snapshot
     */
    public List<BlockPos> trackedPositions() {
        return List.copyOf(this.linkedPositions);
    }

    private static int retryDelay(int initialDelayTicks, int maximumDelayTicks, int retryAttempt) {
        int delay = initialDelayTicks;
        for (int attempt = 1; attempt < retryAttempt && delay < maximumDelayTicks; attempt++) {
            delay = delay > maximumDelayTicks / 2 ? maximumDelayTicks : delay * 2;
        }
        return delay;
    }
}
