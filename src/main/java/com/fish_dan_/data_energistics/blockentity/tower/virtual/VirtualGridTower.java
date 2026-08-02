package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Describes one tower that may own target grids through virtual links.
 *
 * @param towerKey  stable tower key
 * @param localGrid grid containing the physical tower node
 * @param available true when the tower may currently own targets
 * @param <G>       grid key type
 * @param <T>       tower key type
 */
public record VirtualGridTower<G, T>(T towerKey, G localGrid, boolean available) {

}
