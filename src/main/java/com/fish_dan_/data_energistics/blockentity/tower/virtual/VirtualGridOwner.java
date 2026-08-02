package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Identifies the selected tower and upstream grid for one target grid.
 *
 * @param targetGrid target grid
 * @param towerKey   selected tower key
 * @param sourceGrid selected tower's local grid
 * @param fifoOrder  selected candidate FIFO token
 * @param <G>        grid key type
 * @param <T>        tower key type
 */
public record VirtualGridOwner<G, T>(G targetGrid, T towerKey, G sourceGrid, long fifoOrder) {

    /**
     * Validates the immutable selected-owner result.
     */
    public VirtualGridOwner {
        if (fifoOrder < 0) {
            throw new IllegalArgumentException("Virtual grid owner FIFO order must not be negative");
        }
    }
}
