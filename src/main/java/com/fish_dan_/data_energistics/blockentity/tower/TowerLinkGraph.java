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
     * Removes all pending retry positions.
     */
    void clearPending();

    /**
     * Queues a target for connection after the supplied delay.
     *
     * @param targetPos target position
     * @param delay     tick delay before trying to connect
     * @return true when the pending state changed
     */
    boolean queuePending(BlockPos targetPos, int delay);

    /**
     * Updates an existing pending delay.
     *
     * @param targetPos target position
     * @param delay     next delay value
     */
    void putPending(BlockPos targetPos, int delay);

    /**
     * Removes a pending target.
     *
     * @param targetPos target position
     */
    void removePending(BlockPos targetPos);

    /**
     * Checks whether a target is pending.
     *
     * @param targetPos target position
     * @return true when queued for connection
     */
    boolean containsPending(BlockPos targetPos);

    /**
     * Returns pending targets and delays.
     *
     * @return mutable entry view for tick countdowns
     */
    Set<Map.Entry<BlockPos, Integer>> pendingEntries();

    /**
     * Returns pending target positions.
     *
     * @return immutable pending position snapshot
     */
    Set<BlockPos> pendingPositions();

    /**
     * Returns tracked target positions from persisted and pending state.
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
