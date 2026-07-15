package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageStatus;
import com.fish_dan_.data_energistics.gui.ldlib2.SyncValueDataProvider;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenuHost;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

/** Owns the deterministic LDLib2 synchronization channels used by the Trinity host UI. */
final class TrinityDataCoreUiSync {

    private static final String STORAGE_STATUS_NAME = "trinity_storage_status";

    private final SyncValue<TrinityDataCoreStorageStatus> storageStatus;
    private final IDataProvider<TrinityDataCoreStorageStatus> storageStatusProvider;

    private TrinityDataCoreUiSync(TrinityDataCoreMenu menu) {
        this.storageStatus = new SyncValue<>(
                STORAGE_STATUS_NAME,
                TrinityDataCoreStorageStatus.class,
                TrinityDataCoreStorageStatus.EMPTY);
        this.storageStatusProvider = new SyncValueDataProvider<>(this.storageStatus);
        configureStorageStatus(menu);
    }

    /** Creates side-specific channels while retaining identical channel construction order on both sides. */
    static TrinityDataCoreUiSync create(TrinityDataCoreMenu menu) {
        if (menu == null) {
            throw new NullPointerException("Trinity menu must not be null");
        }
        return new TrinityDataCoreUiSync(menu);
    }

    /** Registers channels in protocol order before the ModularUI is attached to its AE menu. */
    void register(ModularUI modularUI) {
        if (modularUI == null) {
            throw new NullPointerException("Trinity ModularUI must not be null");
        }
        modularUI.syncManager.registerSyncValue(this.storageStatus);
    }

    IDataProvider<TrinityDataCoreStorageStatus> storageStatusProvider() {
        return this.storageStatusProvider;
    }

    SyncValue<TrinityDataCoreStorageStatus> storageStatus() {
        return this.storageStatus;
    }

    private void configureStorageStatus(TrinityDataCoreMenu menu) {
        boolean clientSide = menu.getPlayer().level().isClientSide();
        this.storageStatus.setToSync(!clientSide);
        this.storageStatus.setAcceptSync(clientSide);
        if (!clientSide) {
            this.storageStatus.setValueProvider(() -> storageStatus(menu.getHost()));
        }
    }

    private static TrinityDataCoreStorageStatus storageStatus(TrinityDataCoreMenuHost host) {
        return host == null ? TrinityDataCoreStorageStatus.EMPTY : host.getStorageStatus();
    }
}
