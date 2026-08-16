package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

/**
 * Immutable aggregate exposed by one primary Grid's shared tower energy port.
 *
 * @param stored         energy currently visible through source-capable endpoints
 * @param sourceCapacity capacity of those source-capable endpoints
 * @param receivable     energy currently accepted by receiver-capable endpoints
 * @param canExtract     whether at least one source route is available
 * @param canReceive     whether at least one receiver route is available
 */
public record TowerEnergyAccessSnapshot(long stored,
                                        long sourceCapacity,
                                        long receivable,
                                        boolean canExtract,
                                        boolean canReceive) {

    public static final TowerEnergyAccessSnapshot EMPTY = new TowerEnergyAccessSnapshot(0, 0, 0, false, false);

    /** Validates aggregate energy bounds at the domain boundary. */
    public TowerEnergyAccessSnapshot {
        if (stored < 0 || sourceCapacity < stored || receivable < 0) {
            throw new IllegalArgumentException("Tower energy access snapshot contains invalid bounds");
        }
    }
}
