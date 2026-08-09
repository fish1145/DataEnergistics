package com.fish_dan_.data_energistics.blockentity.tower.network;

import com.fish_dan_.data_energistics.blockentity.tower.network.energy.TowerEnergyTransferEndpoint;

import java.util.List;

/**
 * Executes one atomic-as-possible, two-phase FE equalization transaction for a primary-grid domain.
 */
public interface TowerEnergyTransaction {

    /**
     * Freezes, plans, simulates, and then executes one domain transaction.
     *
     * <p>
     * No real mutation occurs when the frozen topology is already balanced or when any preflight simulation cannot
     * satisfy the complete plan. Runtime short writes are compensated back to sources; unrecoverable FE is returned
     * as quarantined energy.
     * </p>
     *
     * @param endpoints stable ordered endpoint topology
     * @return immutable execution result
     */
    TowerEnergyTransactionResult execute(List<TowerEnergyTransferEndpoint> endpoints);
}
