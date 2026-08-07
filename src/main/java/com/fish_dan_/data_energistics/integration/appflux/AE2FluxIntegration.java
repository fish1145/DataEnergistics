package com.fish_dan_.data_energistics.integration.appflux;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.glodblock.github.appflux.common.caps.NetworkFEPower;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public final class AE2FluxIntegration {

    private AE2FluxIntegration() {}

    /** Returns whether a public FE capability directly wraps an Applied Flux network inventory. */
    public static boolean isNetworkEnergyStorage(IEnergyStorage storage) {
        return storage instanceof NetworkFEPower;
    }

    /**
     * Returns the physical Applied Flux storage identity shared by equivalent FE wrappers.
     *
     * @param storage public FE capability
     * @return backing storage service, or {@code null} for unrelated capabilities
     */
    @Nullable
    public static IStorageService networkEnergyStorageIdentity(IEnergyStorage storage) {
        return storage instanceof NetworkFEPower networkPower ? networkPower.storage() : null;
    }

    /** Returns the current storage identity of an active tower's own AE grid. */
    @Nullable
    public static IStorageService ownNetworkEnergyStorageIdentity(AENetworkedBlockEntity blockEntity) {
        IManagedGridNode mainNode = blockEntity.getMainNode();
        if (mainNode == null || !mainNode.isReady()) {
            return null;
        }
        IGrid grid = mainNode.getGrid();
        return grid == null ? null : grid.getStorageService();
    }

    /** Performs one long-width extraction through an Applied Flux network wrapper. */
    public static long extractEnergyFromNetworkStorage(IEnergyStorage storage, long amount, boolean simulate) {
        return transferNetworkEnergy(storage, amount, simulate, false);
    }

    /** Performs one long-width insertion through an Applied Flux network wrapper. */
    public static long insertEnergyIntoNetworkStorage(IEnergyStorage storage, long amount, boolean simulate) {
        return transferNetworkEnergy(storage, amount, simulate, true);
    }

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

    /** Executes a verified long-width transfer against the wrapper's public storage and action source. */
    private static long transferNetworkEnergy(
            IEnergyStorage storage, long amount, boolean simulate, boolean inserting) {
        if (amount < 0) {
            throw new IllegalArgumentException("Applied Flux transfer amount must not be negative");
        }
        if (amount == 0) {
            return 0;
        }
        if (!(storage instanceof NetworkFEPower networkPower)) {
            throw new IllegalArgumentException("Energy storage is not backed by an Applied Flux network");
        }
        Actionable actionable = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        MEStorage inventory = networkPower.storage().getInventory();
        return inserting
                ? inventory.insert(FluxKey.of(EnergyType.FE), amount, actionable, networkPower.source())
                : inventory.extract(FluxKey.of(EnergyType.FE), amount, actionable, networkPower.source());
    }
}
