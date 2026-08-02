package com.fish_dan_.data_energistics.blockentity.tower.virtual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default deterministic in-memory implementation of {@link VirtualGridOwnership}.
 *
 * @param <G> grid key type
 * @param <T> tower key type
 */
public final class VirtualGridOwnershipImpl<G, T> implements VirtualGridOwnership<G, T> {

    private static final Comparator<VirtualGridCandidate<?, ?>> CANDIDATE_ORDER = Comparator.comparingLong(VirtualGridCandidate::fifoOrder);

    private final Map<T, VirtualGridTower<G, T>> towers = new HashMap<>();
    private final Map<CandidateKey<G, T>, VirtualGridCandidate<G, T>> candidates = new HashMap<>();

    /**
     * Creates an empty ownership registry.
     */
    public VirtualGridOwnershipImpl() {}

    @Override
    public void upsertTower(VirtualGridTower<G, T> tower) {
        this.towers.put(tower.towerKey(), tower);
    }

    @Override
    public boolean removeTower(T towerKey) {
        if (this.towers.remove(towerKey) == null) {
            return false;
        }
        this.candidates.keySet().removeIf(key -> key.towerKey.equals(towerKey));
        return true;
    }

    @Override
    public boolean setTowerAvailable(T towerKey, boolean available) {
        VirtualGridTower<G, T> tower = this.towers.get(towerKey);
        if (tower == null) {
            return false;
        }
        this.towers.put(towerKey, new VirtualGridTower<>(towerKey, tower.localGrid(), available));
        return true;
    }

    @Override
    public void upsertCandidate(VirtualGridCandidate<G, T> candidate) {
        if (!this.towers.containsKey(candidate.towerKey())) {
            throw new IllegalArgumentException("Virtual grid candidate references an unknown tower: " + candidate.towerKey());
        }
        CandidateKey<G, T> key = new CandidateKey<>(candidate.targetGrid(), candidate.towerKey());
        VirtualGridCandidate<G, T> existing = this.candidates.get(key);
        if (existing != null) {
            if (existing.fifoOrder() != candidate.fifoOrder()) {
                throw new IllegalArgumentException("Virtual grid candidate FIFO order is immutable for target/tower");
            }
            this.candidates.put(key, candidate);
            return;
        }
        ensureUniqueCandidateOrder(candidate.fifoOrder());
        this.candidates.put(key, candidate);
    }

    @Override
    public boolean removeCandidate(G targetGrid, T towerKey) {
        return this.candidates.remove(new CandidateKey<>(targetGrid, towerKey)) != null;
    }

    @Override
    public boolean setCandidateEnabled(G targetGrid, T towerKey, boolean enabled) {
        CandidateKey<G, T> key = new CandidateKey<>(targetGrid, towerKey);
        VirtualGridCandidate<G, T> candidate = this.candidates.get(key);
        if (candidate == null) {
            return false;
        }
        this.candidates.put(key, new VirtualGridCandidate<>(targetGrid, towerKey, candidate.fifoOrder(), enabled));
        return true;
    }

    @Override
    public VirtualGridOwnershipSnapshot<G, T> snapshot() {
        List<VirtualGridCandidate<G, T>> orderedCandidates = new ArrayList<>(this.candidates.values());
        orderedCandidates.sort(candidateComparator());

        Map<G, VirtualGridOwner<G, T>> ownersByTarget = new HashMap<>();
        List<VirtualGridOwner<G, T>> owners = new ArrayList<>();
        List<VirtualGridCandidateStatus<G, T>> statuses = new ArrayList<>(orderedCandidates.size());
        for (VirtualGridCandidate<G, T> candidate : orderedCandidates) {
            VirtualGridTower<G, T> tower = requiredTower(candidate.towerKey());
            VirtualGridCandidateState state;
            if (!candidate.enabled()) {
                state = VirtualGridCandidateState.DISABLED;
            } else if (!tower.available()) {
                state = VirtualGridCandidateState.TOWER_UNAVAILABLE;
            } else if (ownersByTarget.containsKey(candidate.targetGrid())) {
                state = VirtualGridCandidateState.WAITING_OWNER;
            } else if (createsCycle(candidate.targetGrid(), tower.localGrid(), ownersByTarget)) {
                state = VirtualGridCandidateState.BLOCKED_CYCLE;
            } else {
                state = VirtualGridCandidateState.OWNER;
                VirtualGridOwner<G, T> owner = new VirtualGridOwner<>(
                        candidate.targetGrid(), candidate.towerKey(), tower.localGrid(), candidate.fifoOrder());
                ownersByTarget.put(candidate.targetGrid(), owner);
                owners.add(owner);
            }
            statuses.add(new VirtualGridCandidateStatus<>(candidate.targetGrid(), candidate.towerKey(),
                    tower.localGrid(), candidate.fifoOrder(), state));
        }
        return new VirtualGridOwnershipSnapshot<>(owners, statuses);
    }

    private void ensureUniqueCandidateOrder(long fifoOrder) {
        boolean duplicate = this.candidates.values().stream()
                .anyMatch(candidate -> candidate.fifoOrder() == fifoOrder);
        if (duplicate) {
            throw new IllegalArgumentException("Duplicate virtual grid candidate FIFO order: " + fifoOrder);
        }
    }

    private VirtualGridTower<G, T> requiredTower(T towerKey) {
        VirtualGridTower<G, T> tower = this.towers.get(towerKey);
        if (tower == null) {
            throw new IllegalStateException("Virtual grid candidate lost its registered tower: " + towerKey);
        }
        return tower;
    }

    private static <G, T> boolean createsCycle(G targetGrid, G sourceGrid,
                                               Map<G, VirtualGridOwner<G, T>> ownersByTarget) {
        Set<G> visited = new HashSet<>();
        G current = sourceGrid;
        while (true) {
            if (current.equals(targetGrid)) {
                return true;
            }
            if (!visited.add(current)) {
                throw new IllegalStateException("Selected virtual grid ownership unexpectedly contains a cycle at " + current);
            }
            VirtualGridOwner<G, T> upstreamOwner = ownersByTarget.get(current);
            if (upstreamOwner == null) {
                return false;
            }
            current = upstreamOwner.sourceGrid();
        }
    }

    @SuppressWarnings("unchecked")
    private static <G, T> Comparator<VirtualGridCandidate<G, T>> candidateComparator() {
        return (Comparator<VirtualGridCandidate<G, T>>) (Comparator<?>) CANDIDATE_ORDER;
    }

    private record CandidateKey<G, T>(G targetGrid, T towerKey) {}
}
