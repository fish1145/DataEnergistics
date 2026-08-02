package com.fish_dan_.data_energistics.integration.appflux;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;

public final class AE2FluxIntegration {

    private AE2FluxIntegration() {}

    public static long extractEnergyFromOwnNetwork(AENetworkedBlockEntity blockEntity, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }

        return AppFluxThrowableBoundary.isolateExtraction(() -> {
            IManagedGridNode mainNode = blockEntity.getMainNode();
            if (mainNode == null || !mainNode.isReady()) {
                return 0;
            }

            IGrid grid = mainNode.getGrid();
            if (grid == null) {
                return 0;
            }

            IStorageService storageService = grid.getStorageService();
            if (storageService == null) {
                return 0;
            }

            MEStorage inventory = storageService.getInventory();
            if (inventory == null) {
                return 0;
            }

            Actionable actionable = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
            return inventory.extract(FluxKey.of(EnergyType.FE), amount, actionable, IActionSource.ofMachine(blockEntity));
        });
    }

    /**
     * Reinserts an active-transfer remainder into the tower's own AppFlux network.
     *
     * @param blockEntity tower accessing its grid storage
     * @param amount      non-negative amount to restore
     * @return amount accepted by the network
     */
    public static long insertEnergyIntoOwnNetwork(AENetworkedBlockEntity blockEntity, long amount) {
        return insertEnergyIntoOwnNetwork(blockEntity, amount, false);
    }

    /**
     * Inserts FE into the tower's own AppFlux network with explicit simulation control.
     *
     * @param blockEntity tower accessing its grid storage
     * @param amount      non-negative amount to insert
     * @param simulate    whether the insertion must leave storage unchanged
     * @return amount accepted by the network
     */
    public static long insertEnergyIntoOwnNetwork(
                                                  AENetworkedBlockEntity blockEntity, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }

        return AppFluxThrowableBoundary.isolateRestoration(() -> {
            IManagedGridNode mainNode = blockEntity.getMainNode();
            if (mainNode == null || !mainNode.isReady()) {
                return 0;
            }

            IGrid grid = mainNode.getGrid();
            if (grid == null) {
                return 0;
            }

            IStorageService storageService = grid.getStorageService();
            if (storageService == null) {
                return 0;
            }

            MEStorage inventory = storageService.getInventory();
            if (inventory == null) {
                return 0;
            }

            Actionable actionable = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
            return inventory.insert(
                    FluxKey.of(EnergyType.FE), amount, actionable, IActionSource.ofMachine(blockEntity));
        });
    }
}
