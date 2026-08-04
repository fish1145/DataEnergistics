package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCpuStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus.StructureStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageStatus;
import com.fish_dan_.data_energistics.gui.ldlib2.SyncValueDataProvider;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreCraftingStatus;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenuHost;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Owns the deterministic LDLib2 synchronization channels used by the Trinity host UI. */
final class TrinityDataCoreUiSync {

    private static final String STORAGE_STATUS_NAME = "trinity_storage_status";
    private static final String CPU_LIST_STATUS_NAME = "trinity_cpu_list_status";
    private static final String HOST_STATUS_NAME = "trinity_host_status";
    private static final long CPU_PROGRESS_SYNC_INTERVAL_TICKS = 20L;

    private final SyncValue<TrinityDataCoreStorageStatus> storageStatus;
    private final IDataProvider<TrinityDataCoreStorageStatus> storageStatusProvider;
    private final SyncValue<TrinityCpuListStatus> cpuListStatus;
    private final IDataProvider<TrinityCpuListStatus> cpuListStatusProvider;
    private final SyncValue<TrinityDataCoreHostStatus> hostStatus;
    private final IDataProvider<TrinityDataCoreHostStatus> hostStatusProvider;
    private final CpuStatusSnapshotProvider cpuStatusSnapshots = new CpuStatusSnapshotProvider();

    private TrinityDataCoreUiSync(TrinityDataCoreMenu menu) {
        this.storageStatus = new SyncValue<>(
                STORAGE_STATUS_NAME,
                TrinityDataCoreStorageStatus.class,
                TrinityDataCoreStorageStatus.EMPTY);
        this.storageStatusProvider = new SyncValueDataProvider<>(this.storageStatus);
        this.cpuListStatus = new SyncValue<>(
                CPU_LIST_STATUS_NAME,
                TrinityCpuListStatus.class,
                TrinityCpuListStatus.EMPTY);
        this.cpuListStatusProvider = new SyncValueDataProvider<>(this.cpuListStatus);
        this.hostStatus = new SyncValue<>(
                HOST_STATUS_NAME,
                TrinityDataCoreHostStatus.class,
                TrinityDataCoreHostStatus.EMPTY);
        this.hostStatusProvider = new SyncValueDataProvider<>(this.hostStatus);
        configureStorageStatus(menu);
        configureCpuListStatus(menu);
        configureHostStatus(menu);
    }

    /** Creates side-specific channels while retaining identical channel construction order on both sides. */
    static TrinityDataCoreUiSync create(TrinityDataCoreMenu menu) {
        if (menu == null) {
            throw new NullPointerException("Trinity menu must not be null");
        }
        return new TrinityDataCoreUiSync(menu);
    }

    /** Registers channels in protocol order before the ModularUI is attached to its native menu. */
    void register(ModularUI modularUI) {
        if (modularUI == null) {
            throw new NullPointerException("Trinity ModularUI must not be null");
        }
        modularUI.syncManager.registerSyncValue(this.storageStatus);
        modularUI.syncManager.registerSyncValue(this.cpuListStatus);
        modularUI.syncManager.registerSyncValue(this.hostStatus);
    }

    IDataProvider<TrinityDataCoreStorageStatus> storageStatusProvider() {
        return this.storageStatusProvider;
    }

    IDataProvider<TrinityCpuListStatus> cpuListStatusProvider() {
        return this.cpuListStatusProvider;
    }

    IDataProvider<TrinityDataCoreHostStatus> hostStatusProvider() {
        return this.hostStatusProvider;
    }

    SyncValue<TrinityDataCoreStorageStatus> storageStatus() {
        return this.storageStatus;
    }

    SyncValue<TrinityCpuListStatus> cpuListStatus() {
        return this.cpuListStatus;
    }

    SyncValue<TrinityDataCoreHostStatus> hostStatus() {
        return this.hostStatus;
    }

    private void configureStorageStatus(TrinityDataCoreMenu menu) {
        boolean clientSide = menu.getPlayer().level().isClientSide();
        this.storageStatus.setToSync(!clientSide);
        this.storageStatus.setAcceptSync(clientSide);
        if (!clientSide) {
            this.storageStatus.setValueProvider(() -> storageStatus(menu.getHost()));
        }
    }

    private void configureCpuListStatus(TrinityDataCoreMenu menu) {
        boolean clientSide = menu.getPlayer().level().isClientSide();
        this.cpuListStatus.setToSync(!clientSide);
        this.cpuListStatus.setAcceptSync(clientSide);
        if (!clientSide) {
            this.cpuListStatus.setValueProvider(() -> this.cpuStatusSnapshots.select(
                    cpuListStatus(menu.getHost()),
                    menu.getPlayer().level().getGameTime()));
        }
    }

    private void configureHostStatus(TrinityDataCoreMenu menu) {
        boolean clientSide = menu.getPlayer().level().isClientSide();
        this.hostStatus.setToSync(!clientSide);
        this.hostStatus.setAcceptSync(clientSide);
        if (!clientSide) {
            this.hostStatus.setValueProvider(() -> hostStatus(menu.getHost()));
        }
    }

    private static TrinityDataCoreStorageStatus storageStatus(TrinityDataCoreMenuHost host) {
        return host == null ? TrinityDataCoreStorageStatus.EMPTY : host.getStorageStatus();
    }

    private static TrinityCpuListStatus cpuListStatus(TrinityDataCoreMenuHost host) {
        return host == null ? TrinityCpuListStatus.EMPTY : host.getCpuListStatus();
    }

    static TrinityDataCoreHostStatus hostStatus(TrinityDataCoreMenuHost host) {
        if (host == null) {
            return TrinityDataCoreHostStatus.EMPTY;
        }
        TrinityDataCoreCraftingStatus crafting = host.getCraftingStatus();
        GenericStack target = crafting.target();
        Optional<Component> targetName = crafting.hasTarget() ?
                Optional.of(target.what().getDisplayName().copy()) : Optional.empty();
        return new TrinityDataCoreHostStatus(
                Optional.of(host.getHostId()),
                host.isOnline(),
                structureStatus(
                        host.isStructureFormed(),
                        host.getMatchedBlockCount(),
                        host.getLastFailureReason(),
                        host.getLastFailurePosition()),
                structureStatus(
                        host.isCpuStructureFormed(),
                        host.getCpuStructureMatchedBlockCount(),
                        host.getCpuLastFailureReason(),
                        host.getCpuLastFailurePosition()),
                structureStatus(
                        host.isCraftingStructureFormed(),
                        host.getCraftingStructureMatchedBlockCount(),
                        host.getCraftingLastFailureReason(),
                        host.getCraftingLastFailurePosition()),
                crafting.busyCpuCount(),
                crafting.cpuPartitionCount(),
                crafting.busyCpuPartitionCount(),
                crafting.cpuStorageBytes(),
                crafting.cpuCoProcessors(),
                targetName);
    }

    private static StructureStatus structureStatus(boolean formed,
                                                   int matchedBlocks,
                                                   String failureReason,
                                                   BlockPos failurePosition) {
        return new StructureStatus(
                formed,
                matchedBlocks,
                failureReason,
                failurePosition == null ? "" :
                        failurePosition.getX() + ", " + failurePosition.getY() + ", " + failurePosition.getZ());
    }

    /** Retains CPU progress for 20 ticks while allowing membership, configuration, and task changes through. */
    static final class CpuStatusSnapshotProvider {

        private TrinityCpuListStatus published = TrinityCpuListStatus.EMPTY;
        private long publishedAt;
        private boolean initialized;

        TrinityCpuListStatus select(TrinityCpuListStatus current, long gameTime) {
            if (current == null) {
                throw new NullPointerException("Current Trinity CPU status must not be null");
            }
            if (!this.initialized || gameTime < this.publishedAt ||
                    gameTime - this.publishedAt >= CPU_PROGRESS_SYNC_INTERVAL_TICKS ||
                    !sameImmediateState(this.published.cpus(), current.cpus())) {
                this.published = current;
                this.publishedAt = gameTime;
                this.initialized = true;
            }
            return this.published;
        }

        private static boolean sameImmediateState(List<TrinityCpuStatus> previous, List<TrinityCpuStatus> current) {
            if (previous.size() != current.size()) {
                return false;
            }
            for (int index = 0; index < previous.size(); index++) {
                TrinityCpuStatus left = previous.get(index);
                TrinityCpuStatus right = current.get(index);
                if (left.number() != right.number() ||
                        left.storage() != right.storage() ||
                        left.coProcessors() != right.coProcessors() ||
                        !Objects.equals(left.name(), right.name()) ||
                        left.mode() != right.mode() ||
                        !Objects.equals(left.currentJob(), right.currentJob())) {
                    return false;
                }
            }
            return true;
        }
    }
}
