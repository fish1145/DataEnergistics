package com.fish_dan_.data_energistics.gui.ldlib2.trinity.core;

import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCpuStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreHostStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreHostStatus.StructureStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageView;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternCatalogView;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreCraftingStatus;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenuHost;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Owns the deterministic synchronization channels used by the Trinity host UI.
 */
final class TrinityDataCoreUiSync {

    private static final String STORAGE_STATUS_NAME = "trinity_storage_status";
    private static final String STORAGE_PAGE_NAME = "trinity_storage_page";
    private static final String STORAGE_VIEW_NAME = "trinity_storage_view";
    private static final String STORAGE_PRIORITY_NAME = "trinity_storage_priority";
    private static final String PATTERN_PRIORITY_NAME = "trinity_pattern_priority";
    private static final String PATTERN_PAGE_NAME = "trinity_pattern_page";
    private static final String PATTERN_VIEW_NAME = "trinity_pattern_view";
    private static final String CPU_LIST_STATUS_NAME = "trinity_cpu_list_status";
    private static final String HOST_STATUS_NAME = "trinity_host_status";
    private static final long CPU_PROGRESS_SYNC_INTERVAL_TICKS = 20L;

    private final SyncValue<TrinityDataCoreStorageStatus> storageStatus;
    private final IDataProvider<TrinityDataCoreStorageStatus> storageStatusProvider;
    private final SyncValue<Integer> storagePage;
    private final SyncValue<TrinityDataCoreStorageView> storageView;
    private final IDataProvider<TrinityDataCoreStorageView> storageViewProvider;
    private final SyncValue<Integer> storagePriority;
    private final IDataProvider<Integer> storagePriorityProvider;
    private final SyncValue<Integer> patternPriority;
    private final IDataProvider<Integer> patternPriorityProvider;
    private final SyncValue<Integer> patternPage;
    private final SyncValue<TrinityPatternCatalogView> patternView;
    private final IDataProvider<TrinityPatternCatalogView> patternViewProvider;
    private final SyncValue<TrinityCpuListStatus> cpuListStatus;
    private final IDataProvider<TrinityCpuListStatus> cpuListStatusProvider;
    private final SyncValue<TrinityDataCoreHostStatus> hostStatus;
    private final IDataProvider<TrinityDataCoreHostStatus> hostStatusProvider;
    private final CpuStatusSnapshotProvider cpuStatusSnapshots = new CpuStatusSnapshotProvider();
    private BooleanSupplier storageWindowOpen = () -> false;
    private BooleanSupplier patternWindowOpen = () -> false;

    private TrinityDataCoreUiSync(TrinityDataCoreMenu menu) {
        this.storageStatus = new SyncValue<>(
                STORAGE_STATUS_NAME,
                TrinityDataCoreStorageStatus.class,
                TrinityDataCoreStorageStatus.EMPTY);
        this.storageStatusProvider = new SyncValueDataProvider<>(this.storageStatus);
        this.storagePage = new SyncValue<>(STORAGE_PAGE_NAME, Integer.class, 0);
        this.storageView = new SyncValue<>(
                STORAGE_VIEW_NAME,
                TrinityDataCoreStorageView.class,
                TrinityDataCoreStorageView.EMPTY);
        this.storageViewProvider = new SyncValueDataProvider<>(this.storageView);
        this.storagePriority = new SyncValue<>(STORAGE_PRIORITY_NAME, Integer.class, 0);
        this.storagePriorityProvider = new SyncValueDataProvider<>(this.storagePriority);
        this.patternPriority = new SyncValue<>(PATTERN_PRIORITY_NAME, Integer.class, 0);
        this.patternPriorityProvider = new SyncValueDataProvider<>(this.patternPriority);
        this.patternPage = new SyncValue<>(PATTERN_PAGE_NAME, Integer.class, 0);
        this.patternView = new SyncValue<>(
                PATTERN_VIEW_NAME,
                TrinityPatternCatalogView.class,
                TrinityPatternCatalogView.EMPTY);
        this.patternViewProvider = new SyncValueDataProvider<>(this.patternView);
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
        configureStoragePage(menu);
        configureStorageView(menu);
        configurePriorities(menu);
        configurePatternPage(menu);
        configurePatternView(menu);
        configureCpuListStatus(menu);
        configureHostStatus(menu);
    }

    /**
     * Creates side-specific channels while retaining identical channel construction order on both sides.
     */
    static TrinityDataCoreUiSync create(TrinityDataCoreMenu menu) {
        return new TrinityDataCoreUiSync(menu);
    }

    /**
     * Registers channels in protocol order before the ModularUI is attached to its native menu.
     */
    void register(ModularUI modularUI) {
        modularUI.syncManager.registerSyncValue(this.storageStatus);
        modularUI.syncManager.registerSyncValue(this.storagePage);
        modularUI.syncManager.registerSyncValue(this.storageView);
        modularUI.syncManager.registerSyncValue(this.storagePriority);
        modularUI.syncManager.registerSyncValue(this.patternPriority);
        modularUI.syncManager.registerSyncValue(this.patternPage);
        modularUI.syncManager.registerSyncValue(this.patternView);
        modularUI.syncManager.registerSyncValue(this.cpuListStatus);
        modularUI.syncManager.registerSyncValue(this.hostStatus);
    }

    IDataProvider<TrinityDataCoreStorageStatus> storageStatusProvider() {
        return this.storageStatusProvider;
    }

    IDataProvider<TrinityDataCoreStorageView> storageViewProvider() {
        return this.storageViewProvider;
    }

    IDataProvider<Integer> storagePriorityProvider() {
        return this.storagePriorityProvider;
    }

    IDataProvider<Integer> patternPriorityProvider() {
        return this.patternPriorityProvider;
    }

    IDataProvider<TrinityPatternCatalogView> patternViewProvider() {
        return this.patternViewProvider;
    }

    void setStorageWindowOpen(BooleanSupplier storageWindowOpen) {
        this.storageWindowOpen = storageWindowOpen;
    }

    void setPatternWindowOpen(BooleanSupplier patternWindowOpen) {
        this.patternWindowOpen = patternWindowOpen;
    }

    void requestPatternPage(int firstGlobalSlot) {
        if (firstGlobalSlot < 0) {
            throw new IllegalArgumentException("Trinity pattern page request must not be negative");
        }
        if (this.patternPage.getValue() == firstGlobalSlot) {
            return;
        }
        this.patternPage.setValue(firstGlobalSlot);
        this.patternPage.markAsChanged();
    }

    void requestStoragePage(int firstEntry) {
        if (firstEntry < 0) {
            throw new IllegalArgumentException("Trinity storage page request must not be negative");
        }
        if (this.storagePage.getValue() == firstEntry) {
            return;
        }
        this.storagePage.setValue(firstEntry);
        this.storagePage.markAsChanged();
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

    private void configurePriorities(TrinityDataCoreMenu menu) {
        boolean clientSide = menu.getPlayer().level().isClientSide();
        this.storagePriority.setToSync(!clientSide);
        this.storagePriority.setAcceptSync(clientSide);
        this.patternPriority.setToSync(!clientSide);
        this.patternPriority.setAcceptSync(clientSide);
        if (!clientSide) {
            this.storagePriority.setValueProvider(() -> storagePriority(menu.getHost()));
            this.patternPriority.setValueProvider(() -> patternPriority(menu.getHost()));
        }
    }

    private void configureStoragePage(TrinityDataCoreMenu menu) {
        boolean clientSide = menu.getPlayer().level().isClientSide();
        this.storagePage.setToSync(clientSide);
        this.storagePage.setAcceptSync(true);
    }

    private void configureStorageView(TrinityDataCoreMenu menu) {
        boolean clientSide = menu.getPlayer().level().isClientSide();
        this.storageView.setToSync(!clientSide);
        this.storageView.setAcceptSync(clientSide);
        if (!clientSide) {
            this.storageView.setValueProvider(() -> this.storageWindowOpen.getAsBoolean() ?
                    storageView(menu.getHost(), this.storagePage.getValue()) : TrinityDataCoreStorageView.EMPTY);
        }
    }

    private void configurePatternPage(TrinityDataCoreMenu menu) {
        boolean clientSide = menu.getPlayer().level().isClientSide();
        this.patternPage.setToSync(clientSide);
        this.patternPage.setAcceptSync(true);
    }

    private void configurePatternView(TrinityDataCoreMenu menu) {
        boolean clientSide = menu.getPlayer().level().isClientSide();
        this.patternView.setToSync(!clientSide);
        this.patternView.setAcceptSync(clientSide);
        if (!clientSide) {
            this.patternView.setValueProvider(() -> this.patternWindowOpen.getAsBoolean() ?
                    patternView(menu.getHost(), this.patternPage.getValue()) : TrinityPatternCatalogView.EMPTY);
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

    private static TrinityDataCoreStorageView storageView(TrinityDataCoreMenuHost host, int firstEntry) {
        return host == null ? TrinityDataCoreStorageView.EMPTY : host.getStorageView(firstEntry);
    }

    private static int storagePriority(TrinityDataCoreMenuHost host) {
        return host == null ? 0 : host.getStoragePriority();
    }

    private static int patternPriority(TrinityDataCoreMenuHost host) {
        return host == null ? 0 : host.getPatternPriority();
    }

    private static TrinityPatternCatalogView patternView(TrinityDataCoreMenuHost host, int firstGlobalSlot) {
        return host == null ? TrinityPatternCatalogView.EMPTY : host.getPatternCatalogView(firstGlobalSlot);
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

    /**
     * Retains CPU progress for 20 ticks while allowing membership, configuration, and task changes through.
     */
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

    private record SyncValueDataProvider<T>(SyncValue<T> syncValue) implements IDataProvider<T> {

        @Override
        public ISubscription registerListener(Consumer<T> listener) {
            return this.syncValue.addListener(listener);
        }

        @Override
        public T getValue() {
            return this.syncValue.getValue();
        }
    }
}
