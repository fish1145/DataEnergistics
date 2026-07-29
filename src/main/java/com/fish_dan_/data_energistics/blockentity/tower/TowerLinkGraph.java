package com.fish_dan_.data_energistics.blockentity.tower;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores AE link state for a Data Distribution Tower.
 *
 * <p>
 * The tower uses this graph to distinguish persisted targets, retry candidates, and live AE grid connections without
 * exposing map/set mutation details to higher-level transfer logic.
 */
public interface TowerLinkGraph {

    /**
     * Describes the runtime AE connection lifecycle for one persisted target.
     */
    enum TargetLinkState {
        BOUND,
        WAITING_TARGET,
        WAITING_GRID,
        PENDING,
        PARTIAL,
        CONNECTED,
        DISABLED,
        INVALID
    }

    /**
     * Records the most recent retryable reason without retaining AE runtime objects.
     */
    enum TargetLinkFailure {
        NONE,
        TARGET_UNAVAILABLE,
        GRID_UNAVAILABLE,
        CHANNEL_UNAVAILABLE,
        CONNECTION_EXCEPTION
    }

    /**
     * Immutable runtime snapshot for one target's connection state.
     *
     * @param state      current lifecycle state
     * @param failure    most recent retryable failure category
     * @param retryTicks AE ticks remaining before the next attempt
     */
    record TargetLinkStatus(TargetLinkState state, TargetLinkFailure failure, int retryTicks) {

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
                case WAITING_TARGET, WAITING_GRID, PENDING, PARTIAL, CONNECTED -> true;
                case BOUND, DISABLED, INVALID -> false;
            };
        }
    }

    /**
     * Removes all persisted, pending, and live connection state.
     */
    void clear();

    /**
     * Adds a persisted target position.
     *
     * @param targetPos target to remember
     */
    void addLinked(BlockPos targetPos);

    /**
     * Removes a persisted target position.
     *
     * @param targetPos target to remove
     */
    void removeLinked(BlockPos targetPos);

    /**
     * Checks whether the target is persisted.
     *
     * @param targetPos target position
     * @return true when the target is linked
     */
    boolean containsLinked(BlockPos targetPos);

    /**
     * Returns persisted target positions in deterministic iteration order.
     *
     * @return immutable linked position snapshot
     */
    Set<BlockPos> linkedPositions();

    /**
     * Clears runtime connection state while preserving persisted target identities.
     */
    void resetRuntimeState();

    /**
     * Updates one persisted target's runtime state.
     *
     * @param targetPos  target position
     * @param state      next lifecycle state
     * @param failure    current retryable failure category
     * @param retryTicks AE ticks before the next attempt
     * @return true when the runtime state changed
     */
    boolean transition(BlockPos targetPos, TargetLinkState state, TargetLinkFailure failure, int retryTicks);

    /**
     * Schedules a retryable state using a bounded per-target exponential backoff.
     *
     * <p>
     * A normal {@link #transition(BlockPos, TargetLinkState, TargetLinkFailure, int)} resets the accumulated
     * backoff. Lifecycle wake-ups can therefore retry immediately, while repeated unchanged failures remain
     * rate-limited.
     * </p>
     *
     * @param targetPos         target position
     * @param state             retryable lifecycle state
     * @param failure           current retryable failure category
     * @param initialDelayTicks delay for the first retry
     * @param maximumDelayTicks maximum bounded retry delay
     * @return true when the runtime state changed
     */
    boolean scheduleRetry(BlockPos targetPos, TargetLinkState state, TargetLinkFailure failure,
                          int initialDelayTicks, int maximumDelayTicks);

    /**
     * Returns one target's runtime status.
     *
     * @param targetPos target position
     * @return current status, or {@link TargetLinkState#INVALID} when the target is not persisted
     */
    TargetLinkStatus status(BlockPos targetPos);

    /**
     * Advances retry timers using elapsed AE ticks and returns every target now ready for reconciliation.
     *
     * @param elapsedTicks elapsed ticks reported by the AE tick manager
     * @return deterministic snapshot of retry-ready target positions
     */
    List<BlockPos> advanceRetryClock(int elapsedTicks);

    /**
     * Checks whether any persisted target is scheduled for a later AE reconciliation attempt.
     *
     * @return true when retryable or health-check runtime work remains
     */
    boolean hasRetryableTargets();

    /**
     * Returns every persisted target position in deterministic iteration order.
     *
     * @return immutable tracked position snapshot
     */
    List<BlockPos> trackedPositions();

    /**
     * Returns the live node-to-connection mapping for a target.
     *
     * @param targetPos target position
     * @return immutable connection snapshot keyed by exposed target node
     */
    Map<IGridNode, IGridConnection> connections(BlockPos targetPos);

    /**
     * Reconciles live connections with the currently exposed target nodes.
     *
     * <p>
     * Connections omitted from the supplied map or replaced for the same node are destroyed, while retained
     * node-to-connection mappings are left untouched.
     * </p>
     *
     * @param targetPos   target position
     * @param connections desired node-to-connection mapping
     */
    void reconcileConnections(BlockPos targetPos, Map<IGridNode, IGridConnection> connections);

    /**
     * Checks whether a specific exposed node already has a live tower connection.
     *
     * @param targetPos  target position
     * @param targetNode exposed target node
     * @return true when the node is already connected by this tower
     */
    boolean hasConnection(BlockPos targetPos, IGridNode targetNode);

    /**
     * Returns live connection count for a target.
     *
     * @param targetPos target position
     * @return connection count
     */
    int connectionCount(BlockPos targetPos);

    /**
     * Checks whether live connections are recorded for a target.
     *
     * @param targetPos target position
     * @return true when connections exist
     */
    boolean hasConnections(BlockPos targetPos);

    /**
     * Destroys and removes live connections for a target.
     *
     * @param targetPos target position
     */
    void destroyTargetConnections(BlockPos targetPos);

    /**
     * Destroys all live connections.
     */
    void destroyAllConnections();

    /**
     * Adds multiple persisted targets.
     *
     * @param positions targets to add
     */
    void addLinkedAll(Collection<BlockPos> positions);
}
