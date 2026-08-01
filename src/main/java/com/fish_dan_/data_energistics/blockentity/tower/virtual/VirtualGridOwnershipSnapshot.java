package com.fish_dan_.data_energistics.blockentity.tower.virtual;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable result of deterministic virtual grid owner selection.
 *
 * @param owners     selected owners in candidate FIFO order
 * @param candidates all candidate results in explicit FIFO order
 * @param <G>        grid key type
 * @param <T>        tower key type
 */
public record VirtualGridOwnershipSnapshot<G, T>(List<VirtualGridOwner<G, T>> owners,
                                                 List<VirtualGridCandidateStatus<G, T>> candidates) {

    /**
     * Defensively copies the ownership results.
     */
    public VirtualGridOwnershipSnapshot {
        owners = List.copyOf(owners);
        candidates = List.copyOf(candidates);
    }

    /**
     * Finds the sole owner selected for a target grid.
     *
     * @param targetGrid target grid key
     * @return selected owner when the target currently has one
     */
    public Optional<VirtualGridOwner<G, T>> ownerOf(G targetGrid) {
        return this.owners.stream()
                .filter(owner -> owner.targetGrid().equals(targetGrid))
                .findFirst();
    }

    /**
     * Finds one candidate's current selection status.
     *
     * @param targetGrid target grid key
     * @param towerKey   candidate tower key
     * @return candidate status when registered
     */
    public Optional<VirtualGridCandidateStatus<G, T>> candidateStatus(G targetGrid, T towerKey) {
        return this.candidates.stream()
                .filter(candidate -> candidate.targetGrid().equals(targetGrid) && candidate.towerKey().equals(towerKey))
                .findFirst();
    }

    /**
     * Resolves the single root grid reached through selected ownership edges.
     *
     * @param grid starting grid
     * @return primary root grid for the connected ownership tree
     * @throws IllegalStateException if externally constructed snapshot data contains a cycle
     */
    public G primaryGridOf(G grid) {
        Set<G> visited = new HashSet<>();
        G current = grid;
        while (visited.add(current)) {
            Optional<VirtualGridOwner<G, T>> owner = ownerOf(current);
            if (owner.isEmpty()) {
                return current;
            }
            current = owner.orElseThrow().sourceGrid();
        }
        throw new IllegalStateException("Virtual grid ownership snapshot contains a cycle at " + current);
    }
}
