package com.fish_dan_.data_energistics.blockentity.tower;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default in-memory AE link graph implementation.
 */
public final class TowerLinkGraphImpl implements TowerLinkGraph {

    private static final int MAX_RETRY_ATTEMPTS = 30;
    private final Set<BlockPos> linkedPositions = new LinkedHashSet<>();
    private final Map<BlockPos, TargetLinkStatus> targetStatuses = new LinkedHashMap<>();
    private final Map<BlockPos, Map<IGridNode, IGridConnection>> linkedConnections = new HashMap<>();
    private final Map<BlockPos, Integer> retryAttempts = new HashMap<>();
    private static final TargetLinkStatus INVALID_STATUS = new TargetLinkStatus(
            TargetLinkState.INVALID, TargetLinkFailure.NONE, 0);

    @Override
    public void clear() {
        destroyAllConnections();
        this.targetStatuses.clear();
        this.retryAttempts.clear();
        this.linkedPositions.clear();
    }

    @Override
    public void addLinked(BlockPos targetPos) {
        BlockPos normalizedPos = targetPos.immutable();
        if (this.linkedPositions.add(normalizedPos)) {
            this.targetStatuses.put(normalizedPos, new TargetLinkStatus(
                    TargetLinkState.BOUND, TargetLinkFailure.NONE, 0));
        }
    }

    @Override
    public void addLinkedAll(Collection<BlockPos> positions) {
        for (BlockPos pos : positions) {
            addLinked(pos);
        }
    }

    @Override
    public void removeLinked(BlockPos targetPos) {
        BlockPos normalizedPos = targetPos.immutable();
        this.linkedPositions.remove(normalizedPos);
        this.targetStatuses.remove(normalizedPos);
        this.retryAttempts.remove(normalizedPos);
        destroyTargetConnections(normalizedPos);
    }

    @Override
    public boolean containsLinked(BlockPos targetPos) {
        return this.linkedPositions.contains(targetPos);
    }

    @Override
    public Set<BlockPos> linkedPositions() {
        return new LinkedHashSet<>(this.linkedPositions);
    }

    @Override
    public void resetRuntimeState() {
        destroyAllConnections();
        this.targetStatuses.clear();
        this.retryAttempts.clear();
        for (BlockPos targetPos : this.linkedPositions) {
            this.targetStatuses.put(targetPos, new TargetLinkStatus(
                    TargetLinkState.BOUND, TargetLinkFailure.NONE, 0));
        }
    }

    @Override
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

    @Override
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

    @Override
    public TargetLinkStatus status(BlockPos targetPos) {
        return this.targetStatuses.getOrDefault(targetPos, INVALID_STATUS);
    }

    @Override
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

    @Override
    public boolean hasRetryableTargets() {
        for (TargetLinkStatus status : this.targetStatuses.values()) {
            if (status.isRetryable()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<BlockPos> trackedPositions() {
        return List.copyOf(this.linkedPositions);
    }

    @Override
    public Map<IGridNode, IGridConnection> connections(BlockPos targetPos) {
        return Map.copyOf(this.linkedConnections.getOrDefault(targetPos, Map.of()));
    }

    @Override
    public void reconcileConnections(BlockPos targetPos, Map<IGridNode, IGridConnection> connections) {
        Map<IGridNode, IGridConnection> existing = this.linkedConnections.getOrDefault(targetPos, Map.of());
        for (Map.Entry<IGridNode, IGridConnection> entry : existing.entrySet()) {
            if (connections.get(entry.getKey()) != entry.getValue()) {
                destroyConnection(entry.getValue());
            }
        }

        if (connections.isEmpty()) {
            this.linkedConnections.remove(targetPos);
            return;
        }

        this.linkedConnections.put(targetPos.immutable(), Map.copyOf(connections));
    }

    @Override
    public boolean hasConnection(BlockPos targetPos, IGridNode targetNode) {
        return this.linkedConnections.getOrDefault(targetPos, Map.of()).containsKey(targetNode);
    }

    @Override
    public int connectionCount(BlockPos targetPos) {
        return this.linkedConnections.getOrDefault(targetPos, Map.of()).size();
    }

    @Override
    public boolean hasConnections(BlockPos targetPos) {
        return this.linkedConnections.containsKey(targetPos);
    }

    @Override
    public void destroyTargetConnections(BlockPos targetPos) {
        Map<IGridNode, IGridConnection> existingConnections = this.linkedConnections.remove(targetPos);
        if (existingConnections != null) {
            destroyConnections(existingConnections.values());
        }
    }

    @Override
    public void destroyAllConnections() {
        for (Map<IGridNode, IGridConnection> connections : new ArrayList<>(this.linkedConnections.values())) {
            destroyConnections(connections.values());
        }
        this.linkedConnections.clear();
    }

    private static void destroyConnections(Collection<IGridConnection> connections) {
        for (IGridConnection connection : connections) {
            destroyConnection(connection);
        }
    }

    private static void destroyConnection(IGridConnection connection) {
        if (connection != null) {
            connection.destroy();
        }
    }

    private static int retryDelay(int initialDelayTicks, int maximumDelayTicks, int retryAttempt) {
        int delay = initialDelayTicks;
        for (int attempt = 1; attempt < retryAttempt && delay < maximumDelayTicks; attempt++) {
            delay = delay > maximumDelayTicks / 2 ? maximumDelayTicks : delay * 2;
        }
        return delay;
    }
}
