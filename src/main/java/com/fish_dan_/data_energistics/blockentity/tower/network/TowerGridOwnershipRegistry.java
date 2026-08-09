package com.fish_dan_.data_energistics.blockentity.tower.network;

import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerRuntimeKey;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualGridCandidate;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualGridOwnership;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualGridOwnershipImpl;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualGridOwnershipSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualGridTower;

import net.minecraft.server.MinecraftServer;

import appeng.api.networking.IGrid;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Maintains server-wide virtual-grid ownership while grid-domain instances remain independently ticked.
 */
public final class TowerGridOwnershipRegistry {

    private static final Map<MinecraftServer, ServerOwnership> SERVERS = new WeakHashMap<>();

    private TowerGridOwnershipRegistry() {}

    /**
     * Adds or refreshes one tower and replaces its currently discovered target candidates.
     *
     * @param server      owning server
     * @param towerKey    tower identity
     * @param sourceGrid  tower's physical grid
     * @param available   whether the tower may currently own targets
     * @param targetGrids currently valid target grids
     */
    public static synchronized void replaceTowerCandidates(MinecraftServer server,
                                                           TowerRuntimeKey towerKey,
                                                           IGrid sourceGrid,
                                                           boolean available,
                                                           Set<IGrid> targetGrids) {
        ServerOwnership state = SERVERS.computeIfAbsent(server, ignored -> new ServerOwnership());
        state.replaceTowerCandidates(towerKey, sourceGrid, available, targetGrids);
    }

    /**
     * Marks a known tower unavailable while preserving candidate FIFO positions for failover/failback.
     *
     * @param server   owning server
     * @param towerKey tower identity
     */
    public static synchronized void markTowerUnavailable(MinecraftServer server, TowerRuntimeKey towerKey) {
        ServerOwnership state = SERVERS.get(server);
        if (state != null) {
            state.markTowerUnavailable(towerKey);
        }
    }

    /**
     * Permanently removes a destroyed tower and its candidates.
     *
     * @param server   owning server
     * @param towerKey tower identity
     */
    public static synchronized void removeTower(MinecraftServer server, TowerRuntimeKey towerKey) {
        ServerOwnership state = SERVERS.get(server);
        if (state != null) {
            state.removeTower(towerKey);
        }
    }

    /**
     * Computes the current global owner selection.
     *
     * @param server owning server
     * @return immutable ownership snapshot
     */
    public static synchronized VirtualGridOwnershipSnapshot<IGrid, TowerRuntimeKey> snapshot(
                                                                                             MinecraftServer server) {
        ServerOwnership state = SERVERS.get(server);
        if (state == null) {
            return new VirtualGridOwnershipImpl<IGrid, TowerRuntimeKey>().snapshot();
        }
        return state.ownership.snapshot();
    }

    /**
     * Returns the server-wide ownership revision observed by every primary-grid domain.
     *
     * @param server owning server
     * @return monotonically increasing runtime revision
     */
    public static synchronized long revision(MinecraftServer server) {
        ServerOwnership state = SERVERS.get(server);
        return state == null ? 0 : state.revision;
    }

    /**
     * Clears all runtime ownership data for a stopped server.
     *
     * @param server stopped server
     */
    public static synchronized void clear(MinecraftServer server) {
        SERVERS.remove(server);
    }

    private static final class ServerOwnership {

        private final VirtualGridOwnership<IGrid, TowerRuntimeKey> ownership = new VirtualGridOwnershipImpl<>();
        private final Map<TowerRuntimeKey, Set<IGrid>> targetsByTower = new HashMap<>();
        private final Map<CandidateKey, Long> candidateOrders = new HashMap<>();
        private final Map<TowerRuntimeKey, TowerState> towerStates = new HashMap<>();
        private long nextCandidateOrder;
        private long revision;

        private void replaceTowerCandidates(TowerRuntimeKey towerKey,
                                            IGrid sourceGrid,
                                            boolean available,
                                            Set<IGrid> targetGrids) {
            this.ownership.upsertTower(new VirtualGridTower<>(towerKey, sourceGrid, available));
            Set<IGrid> normalizedTargets = identitySet(targetGrids);
            Set<IGrid> previousTargets = this.targetsByTower.getOrDefault(towerKey, Set.of());
            TowerState previousTower = this.towerStates.put(towerKey, new TowerState(sourceGrid, available));
            boolean changed = previousTower == null || previousTower.sourceGrid() != sourceGrid || previousTower.available() != available || !previousTargets.equals(normalizedTargets);
            for (IGrid previousTarget : List.copyOf(previousTargets)) {
                if (!normalizedTargets.contains(previousTarget)) {
                    this.ownership.removeCandidate(previousTarget, towerKey);
                    this.candidateOrders.remove(new CandidateKey(previousTarget, towerKey));
                }
            }
            for (IGrid targetGrid : normalizedTargets) {
                CandidateKey candidateKey = new CandidateKey(targetGrid, towerKey);
                long fifoOrder = this.candidateOrders.computeIfAbsent(
                        candidateKey, ignored -> nextCandidateOrder());
                this.ownership.upsertCandidate(new VirtualGridCandidate<>(
                        targetGrid, towerKey, fifoOrder, true));
            }
            this.targetsByTower.put(towerKey, normalizedTargets);
            if (changed) {
                incrementRevision();
            }
        }

        private void markTowerUnavailable(TowerRuntimeKey towerKey) {
            TowerState previous = this.towerStates.get(towerKey);
            if (previous == null || !previous.available()) {
                return;
            }
            this.towerStates.put(towerKey, new TowerState(previous.sourceGrid(), false));
            this.ownership.setTowerAvailable(towerKey, false);
            incrementRevision();
        }

        private void removeTower(TowerRuntimeKey towerKey) {
            Set<IGrid> targets = this.targetsByTower.remove(towerKey);
            TowerState towerState = this.towerStates.remove(towerKey);
            if (targets != null) {
                for (IGrid target : targets) {
                    this.candidateOrders.remove(new CandidateKey(target, towerKey));
                }
            }
            this.ownership.removeTower(towerKey);
            if (towerState != null) {
                incrementRevision();
            }
        }

        private long nextCandidateOrder() {
            long result = this.nextCandidateOrder;
            this.nextCandidateOrder = Math.incrementExact(this.nextCandidateOrder);
            return result;
        }

        private void incrementRevision() {
            this.revision = Math.incrementExact(this.revision);
        }
    }

    private static Set<IGrid> identitySet(Set<IGrid> grids) {
        Set<IGrid> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(grids);
        return result;
    }

    private record CandidateKey(IGrid targetGrid, TowerRuntimeKey towerKey) {}

    /** Last registered availability and physical primary identity for one tower. */
    private record TowerState(IGrid sourceGrid, boolean available) {}
}
