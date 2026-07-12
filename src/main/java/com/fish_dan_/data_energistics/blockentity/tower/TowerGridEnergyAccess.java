package com.fish_dan_.data_energistics.blockentity.tower;

import appeng.blockentity.grid.AENetworkedBlockEntity;

/**
 * Provides long-width access to energy stored in the tower's own AE grid.
 *
 * <p>
 * The active distributor depends on this boundary so AppFlux extraction and compensation retain their full
 * {@code long} width without coupling balancing logic to optional integration classes.
 */
interface TowerGridEnergyAccess {

    /**
     * Extracts FE from the tower's own grid.
     *
     * @param tower    tower whose grid storage is queried
     * @param amount   non-negative requested FE
     * @param simulate whether the extraction must leave storage unchanged
     * @return extracted FE in {@code [0, amount]}
     */
    long extract(AENetworkedBlockEntity tower, long amount, boolean simulate);

    /**
     * Restores undelivered FE to the tower's own grid after a receiver short-write.
     *
     * @param tower  tower whose grid receives the compensation
     * @param amount non-negative FE to restore
     * @return restored FE in {@code [0, amount]}
     */
    long restore(AENetworkedBlockEntity tower, long amount);
}
