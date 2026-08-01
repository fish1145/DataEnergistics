package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Registers one tower as an ownership candidate for a target grid.
 *
 * @param targetGrid target grid that must have at most one owner
 * @param towerKey   tower candidate key
 * @param fifoOrder  globally unique non-negative candidate FIFO token
 * @param enabled    true when this target-specific candidacy participates in selection
 * @param <G>        grid key type
 * @param <T>        tower key type
 */
public record VirtualGridCandidate<G, T>(G targetGrid, T towerKey, long fifoOrder, boolean enabled) {

    /**
     * Validates target, tower, and explicit ordering metadata.
     */
    public VirtualGridCandidate {
        if (fifoOrder < 0) {
            throw new IllegalArgumentException("Virtual grid candidate FIFO order must not be negative");
        }
    }
}
