package com.fish_dan_.data_energistics.blockentity.tower;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGridConnection;

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

    private final Map<BlockPos, Integer> pendingLinkPositions = new LinkedHashMap<>();
    private final Set<BlockPos> linkedPositions = new LinkedHashSet<>();
    private final Map<BlockPos, List<IGridConnection>> linkedConnections = new HashMap<>();

    @Override
    public void clear() {
        destroyAllConnections();
        this.pendingLinkPositions.clear();
        this.linkedPositions.clear();
    }

    @Override
    public void addLinked(BlockPos targetPos) {
        this.linkedPositions.add(targetPos.immutable());
    }

    @Override
    public void addLinkedAll(Collection<BlockPos> positions) {
        for (BlockPos pos : positions) {
            addLinked(pos);
        }
    }

    @Override
    public void removeLinked(BlockPos targetPos) {
        this.linkedPositions.remove(targetPos);
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
    public void clearPending() {
        this.pendingLinkPositions.clear();
    }

    @Override
    public boolean queuePending(BlockPos targetPos, int delay) {
        Integer existingDelay = this.pendingLinkPositions.get(targetPos);
        if (existingDelay == null || existingDelay > delay) {
            this.pendingLinkPositions.put(targetPos.immutable(), delay);
            return true;
        }
        return false;
    }

    @Override
    public void putPending(BlockPos targetPos, int delay) {
        this.pendingLinkPositions.put(targetPos.immutable(), delay);
    }

    @Override
    public void removePending(BlockPos targetPos) {
        this.pendingLinkPositions.remove(targetPos);
    }

    @Override
    public boolean containsPending(BlockPos targetPos) {
        return this.pendingLinkPositions.containsKey(targetPos);
    }

    @Override
    public Set<Map.Entry<BlockPos, Integer>> pendingEntries() {
        return this.pendingLinkPositions.entrySet();
    }

    @Override
    public Set<BlockPos> pendingPositions() {
        return new LinkedHashSet<>(this.pendingLinkPositions.keySet());
    }

    @Override
    public List<BlockPos> trackedPositions() {
        LinkedHashSet<BlockPos> tracked = new LinkedHashSet<>(this.linkedPositions);
        tracked.addAll(this.pendingLinkPositions.keySet());
        return List.copyOf(tracked);
    }

    @Override
    public void putConnections(BlockPos targetPos, List<IGridConnection> connections) {
        this.linkedConnections.put(targetPos.immutable(), List.copyOf(connections));
        addLinked(targetPos);
    }

    @Override
    public int connectionCount(BlockPos targetPos) {
        return this.linkedConnections.getOrDefault(targetPos, List.of()).size();
    }

    @Override
    public boolean hasConnections(BlockPos targetPos) {
        return this.linkedConnections.containsKey(targetPos);
    }

    @Override
    public void destroyTargetConnections(BlockPos targetPos) {
        List<IGridConnection> existingConnections = this.linkedConnections.remove(targetPos);
        if (existingConnections != null) {
            destroyConnections(existingConnections);
        }
    }

    @Override
    public void destroyAllConnections() {
        for (List<IGridConnection> connections : new ArrayList<>(this.linkedConnections.values())) {
            destroyConnections(connections);
        }
        this.linkedConnections.clear();
    }

    private static void destroyConnections(List<IGridConnection> connections) {
        for (IGridConnection connection : connections) {
            if (connection != null) {
                connection.destroy();
            }
        }
    }
}
