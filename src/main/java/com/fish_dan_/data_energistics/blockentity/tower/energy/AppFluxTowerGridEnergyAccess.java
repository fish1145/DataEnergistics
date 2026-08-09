package com.fish_dan_.data_energistics.blockentity.tower.energy;

import com.fish_dan_.data_energistics.integration.appflux.AE2FluxIntegration;

import appeng.blockentity.grid.AENetworkedBlockEntity;

/** Uses AppFlux's long-width ME storage API for the tower's own grid energy. */
final class AppFluxTowerGridEnergyAccess implements TowerGridEnergyAccess {

    @Override
    public long extract(AENetworkedBlockEntity tower, long amount, boolean simulate) {
        return AE2FluxIntegration.extractEnergyFromOwnNetwork(tower, amount, simulate);
    }

    @Override
    public long restore(AENetworkedBlockEntity tower, long amount) {
        return AE2FluxIntegration.insertEnergyIntoOwnNetwork(tower, amount);
    }
}
