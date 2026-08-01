package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Immutable selection result for one ownership candidate.
 *
 * @param targetGrid target grid
 * @param towerKey   candidate tower key
 * @param sourceGrid candidate tower's current local grid
 * @param fifoOrder  explicit global FIFO token
 * @param state      current selection state
 * @param <G>        grid key type
 * @param <T>        tower key type
 */
public record VirtualGridCandidateStatus<G, T>(G targetGrid, T towerKey, G sourceGrid,
                                               long fifoOrder, VirtualGridCandidateState state) {

    /**
     * Validates the immutable candidate result.
     */
    public VirtualGridCandidateStatus {
        if (fifoOrder < 0) {
            throw new IllegalArgumentException("Virtual grid candidate FIFO order must not be negative");
        }
    }
}
