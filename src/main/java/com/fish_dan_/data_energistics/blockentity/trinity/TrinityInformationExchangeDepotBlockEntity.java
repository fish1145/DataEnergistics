package com.fish_dan_.data_energistics.blockentity.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.grid.FiniteNetworkStorageAccess;
import com.fish_dan_.data_energistics.ae2.grid.FiniteNetworkStorageAccess.FiniteTransferResult;
import com.fish_dan_.data_energistics.ae2.grid.FiniteNetworkStorageAccess.FiniteTransferTarget;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityDataCoreBlockEntity.CraftingAdmissionToken;
import com.fish_dan_.data_energistics.common.ServerLifecycleEventHandler;
import com.fish_dan_.data_energistics.common.compartment.CompartmentBindingHandle;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.compartment.UnavailableCompartmentStorage;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.TargetedCountedCraftingProvider;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchRejection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTargetAvailability;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityCraftingRuntimeRegistry;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.route.TrinityCraftingExecutionRoute;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.route.TrinityCraftingRouteResolver;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockContext;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockController;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityDataCoreStorageProfile;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityInformationExchangeDepotStatus;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternTerminalPartition;
import com.fish_dan_.data_energistics.menu.trinity.TrinityCraftingStatusSelection;
import com.fish_dan_.data_energistics.menu.trinity.TrinityCraftingStatusSelection.TargetState;
import com.fish_dan_.data_energistics.menu.trinity.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.menu.trinity.TrinityInformationExchangeDepotMenuHost;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.util.ConfigManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * AE network hatch that exposes the bound Trinity Data Core UUID storage instead of storing contents locally.
 */
public class TrinityInformationExchangeDepotBlockEntity extends AENetworkedBlockEntity
                                                        implements CompartmentPart, ITerminalHost, TrinityInformationExchangeDepotMenuHost {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final TrinityCraftingRouteResolver CRAFTING_ROUTE_RESOLVER = new TrinityCraftingRouteResolver();
    /** Stable target identity for the bound Trinity pattern catalog. */
    private static final CraftingDispatchTarget CRAFTING_CATALOG_TARGET = new CraftingDispatchTarget("trinity-pattern-catalog");
    private static final String STORAGE_MODE_TAG = "storage_mode";
    private static final int TRANSFER_KEYS_PER_TICK = 64;
    private static final long TRANSFER_NANOS_PER_TICK = 2_000_000L;

    private final MEStorage networkStorage = new HatchStorage();
    private final IStorageProvider storageProvider = new HatchStorageProvider();
    private final ICraftingProvider craftingProvider = new HatchCraftingProvider();
    private final IStorageWatcherNode transferWatcherNode = new TransferWatcherNode();
    private final IActionSource transferActionSource = new MachineSource(this);
    private final ArrayDeque<AEKey> transferQueue = new ArrayDeque<>();
    private final Set<AEKey> queuedTransferKeys = new HashSet<>();
    private final ConfigManager craftingStatusConfig = new ConfigManager(this::saveChanges);
    private final Set<PatternContainer> managedTerminalPartitions = Collections.newSetFromMap(new IdentityHashMap<>());
    @Nullable
    private TrinityInformationExchangeDepotBindingState compartmentBindingState;
    @Nullable
    private String lastUnavailableReason;
    private List<TrinityPatternTerminalPartition> terminalPartitions = List.of();
    private boolean terminalPartitionsDirty = true;
    private boolean terminalPartitionAttachmentCheckRequested = true;
    private boolean gridBootReevaluationPending;
    private boolean patternPublicationRefreshRequested;
    @Nullable
    private UUID terminalPartitionHostId;
    @Nullable
    private IGrid terminalPartitionGrid;
    private long terminalPartitionLayoutRevision = -1L;
    @Nullable
    private CpuPublication cpuPublication;
    @Nullable
    private PatternPublication patternPublication;
    private StorageMode storageMode = StorageMode.STORAGE;
    @Nullable
    private IStackWatcher transferWatcher;
    @Nullable
    private IGrid transferGrid;
    @Nullable
    private UUID transferStorageId;
    @Nullable
    private TrinityDataCoreStorageProfile transferStorageProfile;
    private long transferStorageStructureRevision = -1L;
    private boolean transferWatcherConfigured;
    private boolean inputSnapshotDirty = true;
    private boolean outputSnapshotDirty = true;
    /** Complete elapsed time of the most recently executed server tick for this depot. */
    private long lastServerTickNanos;

    public TrinityInformationExchangeDepotBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.TRINITY_INFORMATION_EXCHANGE_DEPOT_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .addService(IStorageProvider.class, this.storageProvider)
                .addService(ICraftingProvider.class, this.craftingProvider)
                .addService(IStorageWatcherNode.class, this.transferWatcherNode)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setVisualRepresentation(DEBlocks.TRINITY_INFORMATION_EXCHANGE_DEPOT.get())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    /**
     * Exposes the hatch's main node as the anchor used by AE2's pattern-access menu.
     */
    @Nullable
    @Override
    public IGridNode getGridNode() {
        return this.getMainNode().getNode();
    }

    /**
     * Exposes the existing host-backed storage to AE's crafting-status terminal menu.
     */
    @Override
    public MEStorage getInventory() {
        return this.networkStorage;
    }

    /**
     * Reports both the grid-node state and whether this hatch still owns the Trinity lease.
     */
    @Override
    public ILinkStatus getLinkStatus() {
        ILinkStatus nodeStatus = ILinkStatus.ofManagedNode(this.getMainNode());
        if (!nodeStatus.connected() || isAccessOnline()) {
            return nodeStatus;
        }
        return ILinkStatus.ofDisconnected();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return UpgradeInventories.empty();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.craftingStatusConfig;
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.storageMode = data.contains(STORAGE_MODE_TAG) ?
                StorageMode.fromSerializedName(data.getString(STORAGE_MODE_TAG)) : StorageMode.STORAGE;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putString(STORAGE_MODE_TAG, this.storageMode.serializedName());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return DEBlocks.TRINITY_DATA_CORE.get().asItem().getDefaultInstance();
    }

    /**
     * Resolves the live bound controller instead of returning to the information exchange depot itself.
     */
    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        TrinityCraftingStatusSelection.Target target = subMenu instanceof TrinityCraftingStatusSelection.TargetedMenu menu ? menu.dataEnergistics$getTrinityTarget() : null;
        TrinityDataCoreBlockEntity host = boundHost(false);
        if (target == null || host == null || !isCurrentCpuStatusRoute(target)) {
            LOGGER.warn(
                    "Cannot return from Trinity CPU status because its original route is stale: access={}, expectedHost={}, currentHost={}",
                    this.worldPosition,
                    target == null ? null : target.hostId(),
                    host == null ? null : host.getHostId());
            player.closeContainer();
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            LOGGER.error("Cannot return from Trinity CPU status outside a server player context: player={}", player);
            player.closeContainer();
            return;
        }
        try {
            if (TrinityDataCoreMenu.open(serverPlayer, host)) {
                return;
            }
            LOGGER.error("Failed to return from Trinity CPU status to host {} at {}",
                    host.getHostId(), host.getBlockPos());
            player.closeContainer();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to resolve the Trinity host menu at {} from information exchange depot {}",
                    host.getBlockPos(), this.worldPosition, exception);
            player.closeContainer();
        }
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        long tickStartedAtNanos = System.nanoTime();
        try {
            finishGridBootReevaluation();
            updateActiveState();
            tickStorageTransfer();
            refreshTerminalPartitionsSafely();
        } finally {
            this.lastServerTickNanos = System.nanoTime() - tickStartedAtNanos;
        }
    }

    /**
     * Notifies the selected grid that only the host storage content changed.
     */
    public void refreshTrinityStorageContent() {
        if (!canRefreshGridServices()) {
            return;
        }
        switch (this.storageMode) {
            case STORAGE -> requestStorageUpdate();
            case INPUT -> this.inputSnapshotDirty = true;
            case OUTPUT -> this.outputSnapshotDirty = true;
        }
    }

    /**
     * Remounts Trinity storage after its AE2 priority changes.
     */
    public void refreshTrinityStoragePriority() {
        if (!canRefreshGridServices()) {
            return;
        }
        if (this.storageMode.mountsStorage()) {
            requestStorageUpdate();
        }
    }

    /**
     * Synchronizes virtual CPU membership before posting AE2's CPU-cache notification.
     */
    public void refreshTrinityCpuTopology() {
        if (!canRefreshGridServices()) {
            return;
        }
        if (!synchronizeCraftingCpuPublication() && this.cpuPublication != null) {
            notifyCraftingCpuChanged(this.cpuPublication);
        }
    }

    /**
     * Reconciles the immutable pattern snapshot published to AE2.
     *
     * @return whether AE2 had to rebuild this provider's pattern index
     */
    public boolean refreshTrinityPatternPublication() {
        if (!canRefreshGridServices()) {
            return false;
        }
        return synchronizeCraftingPatternPublication();
    }

    /**
     * Rebuilds AE2's crafting-provider index after the aggregate pattern priority changes.
     */
    public void refreshTrinityPatternPriority() {
        if (!canRefreshGridServices()) {
            return;
        }
        requestCraftingProviderUpdate();
    }

    /**
     * Reconciles only the pattern-terminal layout and its grid attachments.
     */
    public void refreshTrinityTerminalLayout() {
        if (!canRefreshGridServices()) {
            return;
        }
        this.terminalPartitionsDirty = true;
        refreshTerminalPartitionsSafely();
    }

    /**
     * Forcefully withdraws an old lease owner in terminal, CPU, pattern, then storage order.
     */
    public void withdrawTrinityLeasePublications() {
        resetTransferState();
        if (this.level instanceof ServerLevel serverLevel &&
                ServerLifecycleEventHandler.isStopping(serverLevel.getServer())) {
            discardShutdownPublications();
            return;
        }
        this.terminalPartitionsDirty = true;
        detachTerminalPartitions();
        withdrawCraftingCpuPublicationAndNotify();
        withdrawCraftingPatternPublication();
        if (canRefreshGridServices()) {
            requestStorageUpdate();
            updateActiveState();
        }
    }

    /**
     * The closing server owns the remaining AE2 graph; rebuilding it while every chunk unloads is wasted work.
     */
    private void discardShutdownPublications() {
        this.terminalPartitionsDirty = false;
        this.terminalPartitions = List.of();
        this.managedTerminalPartitions.clear();
        this.terminalPartitionHostId = null;
        this.terminalPartitionGrid = null;
        this.terminalPartitionLayoutRevision = -1L;
        this.terminalPartitionAttachmentCheckRequested = false;
        this.cpuPublication = null;
        this.patternPublication = null;
        this.patternPublicationRefreshRequested = false;
    }

    /**
     * Publishes a selected lease owner in CPU, storage, pattern, then terminal order.
     */
    public void publishTrinityLeasePublications() {
        if (!canRefreshGridServices()) {
            return;
        }
        refreshTrinityCpuTopology();
        refreshTrinityStorageContent();
        refreshTrinityPatternPublication();
        refreshTrinityTerminalLayout();
        updateActiveState();
    }

    private boolean canRefreshGridServices() {
        return this.level != null && !this.level.isClientSide();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (reason == IGridNodeListener.State.GRID_BOOT) {
            this.gridBootReevaluationPending = true;
            return;
        }
        this.gridBootReevaluationPending = false;
        this.terminalPartitionAttachmentCheckRequested = true;
        TrinityDataCoreBlockEntity host = boundHost(false);
        if (host == null || !isCandidateOnline()) {
            withdrawTrinityLeasePublications();
        }
        if (host != null) {
            host.requestAccessLeaseReevaluation();
        }
    }

    @Override
    public void onChunkUnloaded() {
        this.gridBootReevaluationPending = false;
        withdrawTrinityLeasePublications();
        TrinityDataCoreBlockEntity host = boundHost(false);
        super.onChunkUnloaded();
        if (host != null) {
            host.requestAccessLeaseReevaluation();
        }
    }

    @Override
    public void setRemoved() {
        this.gridBootReevaluationPending = false;
        withdrawTrinityLeasePublications();
        TrinityDataCoreBlockEntity host = boundHost(false);
        super.setRemoved();
        if (host != null) {
            host.requestMainStructureRecheck();
            host.requestAccessLeaseReevaluation();
        }
    }

    private void updateActiveState() {
        boolean active = isAccessOnline();
        BlockState state = getBlockState();
        if (state.hasProperty(CompartmentBlock.ACTIVE) && state.getValue(CompartmentBlock.ACTIVE) != active) {
            this.level.setBlock(this.worldPosition, state.setValue(CompartmentBlock.ACTIVE, active), 3);
        }
    }

    private void finishGridBootReevaluation() {
        if (!this.gridBootReevaluationPending || !isCandidateOnline()) {
            return;
        }
        this.gridBootReevaluationPending = false;
        this.terminalPartitionAttachmentCheckRequested = true;
        this.terminalPartitionsDirty = true;
        this.patternPublicationRefreshRequested = true;
        TrinityDataCoreBlockEntity host = boundHost(false);
        if (host != null) {
            host.requestAccessLeaseReevaluation();
        }
    }

    @Override
    public CompartmentType compartmentType() {
        return CompartmentType.TRINITY_INFORMATION_EXCHANGE;
    }

    @Override
    public VerticalMultiBlockPos compartmentPos() {
        return new VerticalMultiBlockPos(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
    }

    @Nullable
    @Override
    public CompartmentHost compartmentHost() {
        TrinityInformationExchangeDepotBindingState binding = this.compartmentBindingState;
        return binding == null ? null : binding.host();
    }

    @Nullable
    @Override
    public String compartmentStructureName() {
        TrinityInformationExchangeDepotBindingState binding = this.compartmentBindingState;
        return binding == null ? null : binding.structureName();
    }

    @Override
    public boolean isCompartmentBound() {
        TrinityInformationExchangeDepotBindingState binding = this.compartmentBindingState;
        return binding != null && binding.isActive();
    }

    @Override
    public CompartmentStorage compartmentStorage() {
        return UnavailableCompartmentStorage.INSTANCE;
    }

    @Override
    public void compartment$bindToHost(String structureName, CompartmentHost host) {
        bindToHost(structureName, host, null, -1L);
    }

    private void bindToHost(String structureName,
                            CompartmentHost host,
                            @Nullable VerticalMultiBlockController verticalController,
                            long verticalBindingEpoch) {
        TrinityInformationExchangeDepotBindingState requestedBinding = createBindingState(
                structureName,
                host,
                verticalController,
                verticalBindingEpoch);
        TrinityInformationExchangeDepotBindingState currentBinding = this.compartmentBindingState;
        if (currentBinding == null) {
            activateBinding(requestedBinding);
            return;
        }
        if (currentBinding.isActive() && currentBinding.matchesRequestedBinding(
                host,
                structureName,
                verticalController,
                verticalBindingEpoch)) {
            ensureHostRegistration(currentBinding);
            return;
        }
        if (currentBinding.isReleasing()) {
            currentBinding.queueReplacement(requestedBinding);
            releaseBinding(currentBinding, "retrying or completing a Trinity information exchange depot replacement");
            return;
        }
        releaseBinding(
                currentBinding,
                requestedBinding,
                "replacing an existing Trinity information exchange depot binding");
    }

    @Override
    public void compartment$unbindFromHost(String structureName, CompartmentHost host) {
        try {
            requireUnbindArguments(structureName, host);
        } catch (RuntimeException exception) {
            LOGGER.error("Rejecting invalid Trinity information exchange depot unbind at {}: host={}, structure={}",
                    this.worldPosition, host, structureName, exception);
            throw exception;
        }
        TrinityInformationExchangeDepotBindingState currentBinding = this.compartmentBindingState;
        if (currentBinding == null || !currentBinding.matches(host, structureName)) {
            unregisterHost(structureName, host);
            return;
        }
        LOGGER.debug("Ignoring untagged Trinity information exchange depot unbind at {} for host={}, structure={}; " +
                "lifecycle owners must return the captured binding handle",
                this.worldPosition,
                host,
                structureName);
    }

    @Nullable
    @Override
    public CompartmentBindingHandle compartment$bindingHandle() {
        return this.compartmentBindingState;
    }

    @Override
    public boolean compartment$requiresBindingRetry(String structureName, CompartmentHost host) {
        TrinityInformationExchangeDepotBindingState binding = this.compartmentBindingState;
        return binding != null && binding.requiresBindingRetry(host, structureName);
    }

    @Override
    public void compartment$unbindFromHost(CompartmentBindingHandle bindingHandle) {
        if (!(bindingHandle instanceof TrinityInformationExchangeDepotBindingState expectedBinding)) {
            LOGGER.warn("Ignoring foreign Trinity information exchange depot binding handle at {}: {}",
                    this.worldPosition,
                    bindingHandle);
            return;
        }
        if (this.compartmentBindingState != expectedBinding) {
            LOGGER.debug("Ignoring stale Trinity information exchange depot binding handle at {} for host={}, structure={}",
                    this.worldPosition,
                    expectedBinding.host(),
                    expectedBinding.structureName());
            return;
        }
        releaseBinding(expectedBinding, "identity-aware Trinity information exchange depot unbind");
    }

    @Override
    public void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                     String structureName,
                                                     VerticalMultiBlockContext<?> context) {
        LOGGER.debug("Ignoring untagged Trinity information exchange depot vertical bind at {} for controller={}, structure={}; " +
                "the vertical runtime must supply its binding epoch",
                this.worldPosition,
                controller,
                structureName);
    }

    @Override
    public void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                     String structureName,
                                                     VerticalMultiBlockContext<?> context,
                                                     long bindingEpoch) {
        if (!(controller instanceof CompartmentHost host)) {
            LOGGER.warn("Ignoring Trinity information exchange depot bind from non-compartment controller at {}: {}",
                    this.worldPosition,
                    controller);
            return;
        }
        bindToHost(structureName, host, controller, bindingEpoch);
    }

    @Override
    public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller, String structureName) {
        LOGGER.debug("Ignoring untagged Trinity information exchange depot vertical removal at {} for controller={}, structure={}; " +
                "the vertical runtime must return its captured binding epoch",
                this.worldPosition,
                controller,
                structureName);
    }

    @Override
    public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller,
                                                         String structureName,
                                                         long bindingEpoch) {
        if (!(controller instanceof CompartmentHost host)) {
            LOGGER.warn("Ignoring Trinity information exchange depot removal from non-compartment controller at {}: {}",
                    this.worldPosition, controller);
            return;
        }
        TrinityInformationExchangeDepotBindingState currentBinding = this.compartmentBindingState;
        if (currentBinding == null) {
            return;
        }
        if (!currentBinding.matchesVerticalRemoval(controller, structureName, bindingEpoch)) {
            LOGGER.debug("Ignoring stale Trinity information exchange depot vertical removal at {} for host={}, structure={}, epoch={}",
                    this.worldPosition,
                    host,
                    structureName,
                    bindingEpoch);
            return;
        }
        compartment$unbindFromHost(currentBinding);
    }

    @Override
    public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller) {
        LOGGER.debug("Ignoring untagged Trinity information exchange depot legacy vertical removal at {} for controller={}; " +
                "the vertical runtime must return its captured binding epoch",
                this.worldPosition,
                controller);
    }

    private TrinityInformationExchangeDepotBindingState createBindingState(String structureName,
                                                                           CompartmentHost host,
                                                                           @Nullable VerticalMultiBlockController verticalController,
                                                                           long verticalBindingEpoch) {
        try {
            return verticalController == null ? TrinityInformationExchangeDepotBindingState.active(structureName, host) :
                    TrinityInformationExchangeDepotBindingState.active(
                            structureName,
                            host,
                            verticalController,
                            verticalBindingEpoch);
        } catch (RuntimeException exception) {
            LOGGER.error("Rejecting invalid Trinity information exchange depot binding at {}: host={}, structure={}",
                    this.worldPosition, host, structureName, exception);
            throw exception;
        }
    }

    private void registerHost(TrinityInformationExchangeDepotBindingState binding) {
        try {
            CompartmentPart.super.compartment$bindToHost(binding.structureName(), binding.host());
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to register Trinity information exchange depot at {} with host={}, structure={}",
                    this.worldPosition, binding.host(), binding.structureName(), exception);
            throw exception;
        }
    }

    private void ensureHostRegistration(TrinityInformationExchangeDepotBindingState binding) {
        if (binding.host().compartmentHost$getCompartments(binding.structureName()).contains(this)) {
            return;
        }
        registerHost(binding);
        requestLeaseReevaluation(binding.host());
    }

    private void unregisterHost(String structureName, CompartmentHost host) {
        try {
            CompartmentPart.super.compartment$unbindFromHost(structureName, host);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to remove stale Trinity information exchange depot registration at {} from host={}, structure={}",
                    this.worldPosition, host, structureName, exception);
            throw exception;
        }
    }

    private void activateBinding(TrinityInformationExchangeDepotBindingState binding) {
        registerHost(binding);
        this.compartmentBindingState = binding;
        this.lastUnavailableReason = null;
        requestLeaseReevaluation(binding.host());
    }

    private void releaseBinding(TrinityInformationExchangeDepotBindingState expectedBinding, String reason) {
        releaseBinding(expectedBinding, null, reason);
    }

    private void releaseBinding(TrinityInformationExchangeDepotBindingState expectedBinding,
                                @Nullable TrinityInformationExchangeDepotBindingState replacementBinding,
                                String reason) {
        if (this.compartmentBindingState != expectedBinding) {
            return;
        }
        TrinityInformationExchangeDepotBindingState releasingBinding = expectedBinding.isReleasing() ? expectedBinding : expectedBinding.releasing();
        this.compartmentBindingState = releasingBinding;
        if (replacementBinding != null) {
            releasingBinding.queueReplacement(replacementBinding);
        }
        if (releasingBinding.isReleaseInProgress()) {
            return;
        }
        if (!releasingBinding.isReleaseCompleted()) {
            releasingBinding.beginReleaseAttempt();
            try {
                unregisterHost(releasingBinding.structureName(), releasingBinding.host());
                releasingBinding.markReleaseCompleted();
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to release Trinity information exchange depot binding at {} ({})",
                        this.worldPosition,
                        reason,
                        exception);
                throw exception;
            } finally {
                releasingBinding.finishReleaseAttempt();
            }
        }
        completeReleasedBinding(releasingBinding, reason);
    }

    private void completeReleasedBinding(TrinityInformationExchangeDepotBindingState releasingBinding, String reason) {
        if (this.compartmentBindingState != releasingBinding || releasingBinding.isReleaseCompletionInProgress()) {
            return;
        }
        releasingBinding.beginReleaseCompletion();
        try {
            withdrawTrinityLeasePublications();
            requestLeaseReevaluation(releasingBinding.host());
            if (this.compartmentBindingState != releasingBinding) {
                return;
            }
            TrinityInformationExchangeDepotBindingState replacementBinding = releasingBinding.pendingReplacement();
            if (replacementBinding == null) {
                this.compartmentBindingState = null;
                this.lastUnavailableReason = null;
                return;
            }
            if (!replacementBinding.host().compartmentHost$getCompartments(replacementBinding.structureName()).contains(this)) {
                registerHost(replacementBinding);
            }
            if (this.compartmentBindingState != releasingBinding) {
                return;
            }
            this.compartmentBindingState = replacementBinding;
            this.lastUnavailableReason = null;
            requestLeaseReevaluation(replacementBinding.host());
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to register queued Trinity information exchange depot replacement at {} ({})",
                    this.worldPosition,
                    reason,
                    exception);
            throw exception;
        } finally {
            releasingBinding.finishReleaseCompletion();
        }
    }

    private static void requireUnbindArguments(String structureName, CompartmentHost host) {
        if (host == null) {
            throw new IllegalArgumentException("Trinity information exchange depot unbind host must not be null");
        }
        if (structureName == null || structureName.isBlank()) {
            throw new IllegalArgumentException(
                    "Trinity information exchange depot unbind structure name must not be blank");
        }
    }

    private static void requestLeaseReevaluation(CompartmentHost host) {
        if (host instanceof TrinityDataCoreBlockEntity dataCore) {
            dataCore.requestAccessLeaseReevaluation();
        }
    }

    public @Nullable TrinityDataCoreCraftingRuntime boundCraftingRuntime() {
        TrinityDataCoreBlockEntity host = boundHost(false);
        return host == null || !isCandidateOnline() || !host.isLeaseOwner(this) ||
                !host.isCpuProviderAvailable() ? null : host.getCraftingRuntime();
    }

    /**
     * Classifies the original CPU pin against this hatch's current lease publication.
     */
    public TargetState cpuStatusTargetState(TrinityCraftingStatusSelection.Target target) {
        if (target == null) {
            throw new IllegalArgumentException("Trinity CPU status target cannot be null");
        }
        TrinityDataCoreBlockEntity host = boundHost(false);
        if (host == null) {
            return TargetState.STALE_ROUTE;
        }
        return target.currentState(host.getHostId(), boundCraftingRuntime(), craftingExecutionRoute());
    }

    /**
     * Verifies that the original CPU is still published on this hatch's exact lease Grid.
     */
    public boolean isCurrentCpuStatusTarget(TrinityCraftingStatusSelection.Target target) {
        return cpuStatusTargetState(target) == TargetState.CURRENT_CPU;
    }

    /**
     * Verifies the Host, runtime, lease and Grid route after the original worker retires.
     */
    public boolean isCurrentCpuStatusRoute(TrinityCraftingStatusSelection.Target target) {
        if (target == null) {
            throw new IllegalArgumentException("Trinity CPU status target cannot be null");
        }
        TrinityDataCoreBlockEntity host = boundHost(false);
        return host != null && target.isRouteCurrent(
                host.getHostId(),
                boundCraftingRuntime(),
                craftingExecutionRoute());
    }

    /**
     * Resolves the current CPU execution route while retaining {@link #accessGrid()} as the physical lease identity.
     *
     * @return immutable execution route, or {@code null} while this hatch does not own a usable CPU lease
     */
    public @Nullable TrinityCraftingExecutionRoute craftingExecutionRoute() {
        TrinityDataCoreBlockEntity host = boundHost(false);
        return host == null || !host.isLeaseOwner(this) ? null : host.craftingExecutionRoute();
    }

    /** Resolves this exact hatch node against the lease epoch already validated by its host. */
    @Nullable
    TrinityCraftingExecutionRoute resolveCraftingExecutionRoute(long leaseEpoch) {
        if (!isCandidateOnline()) {
            return null;
        }
        return CRAFTING_ROUTE_RESOLVER.resolve(this.getMainNode().getNode(), leaseEpoch);
    }

    public @Nullable IGrid connectedGrid() {
        if (boundHost(false) == null) {
            return null;
        }
        var node = this.getMainNode().getNode();
        if (node == null) {
            return null;
        }
        return node.getGrid();
    }

    public @Nullable IGrid accessGrid() {
        return isAccessOnline() ? connectedGrid() : null;
    }

    public boolean isCandidateOnline() {
        if (boundHost(false) == null) {
            return false;
        }
        var node = this.getMainNode().getNode();
        return node != null && node.isActive();
    }

    public boolean isAccessOnline() {
        TrinityDataCoreBlockEntity host = boundHost(false);
        return host != null && host.isLeaseOwner(this) && host.isStorageAvailable() &&
                isCandidateOnline();
    }

    /**
     * Validates the physical block-entity route and normal eight-block menu interaction range.
     */
    @Override
    public boolean isInformationExchangeDepotMenuValid(Player player) {
        if (player == null) {
            throw new IllegalArgumentException(
                    "Trinity information exchange depot menu player cannot be null");
        }
        Level currentLevel = this.level;
        return currentLevel != null &&
                !this.isRemoved() &&
                player.level() == currentLevel &&
                currentLevel.getBlockEntity(this.worldPosition) == this &&
                currentLevel.getBlockState(this.worldPosition).is(DEBlocks.TRINITY_INFORMATION_EXCHANGE_DEPOT.get()) &&
                player.distanceToSqr(
                        this.worldPosition.getX() + 0.5D,
                        this.worldPosition.getY() + 0.5D,
                        this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public StorageMode informationExchangeMode() {
        return this.storageMode;
    }

    @Override
    public TrinityInformationExchangeDepotStatus informationExchangeStatus() {
        TrinityDataCoreBlockEntity host = boundHost(false);
        if (host == null) {
            return TrinityInformationExchangeDepotStatus.unbound(this.lastServerTickNanos);
        }
        return new TrinityInformationExchangeDepotStatus(
                host.getPatternMaintenanceSnapshot(),
                host.lastServerTickNanos(),
                this.lastServerTickNanos);
    }

    @Override
    public boolean setInformationExchangeMode(Player player, StorageMode mode) {
        if (!(player instanceof ServerPlayer) || !isInformationExchangeDepotMenuValid(player)) {
            return false;
        }
        if (this.storageMode == mode) {
            return true;
        }
        this.storageMode = mode;
        resetTransferState();
        this.saveChanges();
        this.markForUpdate();
        requestStorageUpdate();
        return true;
    }

    /**
     * Returns the immutable set of terminal partitions currently owned by this hatch.
     */
    public List<TrinityPatternTerminalPartition> terminalPartitions() {
        return this.terminalPartitions;
    }

    public IActionSource actionSource() {
        return IActionSource.ofMachine(this);
    }

    @Nullable
    private TrinityDataCoreBlockEntity boundHost() {
        return boundHost(true);
    }

    @Nullable
    private TrinityDataCoreBlockEntity boundHost(boolean logUnavailable) {
        TrinityInformationExchangeDepotBindingState binding = this.compartmentBindingState;
        if (binding == null || !binding.isActive()) {
            logUnavailable(logUnavailable, "not bound to a trinity structure");
            return null;
        }
        if (!(binding.host() instanceof TrinityDataCoreBlockEntity host)) {
            logUnavailable(logUnavailable, "bound host is not a Trinity Data Core");
            return null;
        }
        if (!host.isStructureFormed()) {
            logUnavailable(logUnavailable, "bound Trinity Data Core structure is not formed");
            return null;
        }
        this.lastUnavailableReason = null;
        return host;
    }

    @Nullable
    private TrinityDataCoreBlockEntity patternProviderHost() {
        TrinityDataCoreBlockEntity host = boundHost(false);
        if (host == null || !isCandidateOnline() || !host.isLeaseOwner(this) ||
                !host.isPatternProviderAvailable()) {
            return null;
        }
        return host;
    }

    private void logUnavailable(boolean shouldLog, String reason) {
        if (!shouldLog) {
            return;
        }
        if (reason.equals(this.lastUnavailableReason)) {
            return;
        }
        this.lastUnavailableReason = reason;
        LOGGER.warn("Trinity information exchange depot at {} exposes empty storage: {}", this.worldPosition, reason);
    }

    private void requestStorageUpdate() {
        IStorageProvider.requestUpdate(this.getMainNode());
    }

    private void requestCraftingProviderUpdate() {
        ICraftingProvider.requestUpdate(this.getMainNode());
    }

    private boolean synchronizeCraftingPatternPublication() {
        PatternPublication desired = resolveCraftingPatternPublication();
        PatternPublication current = this.patternPublication;
        if (current == null && desired == null) {
            return false;
        }
        if (!this.patternPublicationRefreshRequested &&
                current != null && desired != null && current.matches(desired)) {
            return false;
        }
        this.patternPublication = desired;
        this.patternPublicationRefreshRequested = false;
        requestCraftingProviderUpdate();
        return true;
    }

    @Nullable
    private PatternPublication resolveCraftingPatternPublication() {
        TrinityDataCoreBlockEntity host = patternProviderHost();
        if (host == null) {
            return null;
        }
        IGridNode node = this.getMainNode().getNode();
        return new PatternPublication(
                host.getHostId(),
                node.getGrid(),
                node,
                host.getPatternCatalog().publicationRevision());
    }

    private void withdrawCraftingPatternPublication() {
        this.patternPublicationRefreshRequested = false;
        if (this.patternPublication == null) {
            return;
        }
        this.patternPublication = null;
        if (canRefreshGridServices()) {
            requestCraftingProviderUpdate();
        }
    }

    private boolean synchronizeCraftingCpuPublication() {
        CpuPublication desired = resolveCraftingCpuPublication();
        CpuPublication current = this.cpuPublication;
        if (current != null && desired != null && current.matches(desired)) {
            return false;
        }

        CpuPublication withdrawn = withdrawCraftingCpuPublication();
        if (desired == null) {
            if (withdrawn != null) {
                notifyCraftingCpuChanged(withdrawn);
                return true;
            }
            return withdrawUntrackedCraftingCpuPublicationAndNotify();
        }

        boolean published = desired.registry().data_energistics$publish(desired.node(), desired.runtime());
        this.cpuPublication = desired;
        boolean notified = false;
        if (withdrawn != null && !withdrawn.hasSameNotificationTarget(desired)) {
            notifyCraftingCpuChanged(withdrawn);
            notified = true;
        }
        if (published) {
            notifyCraftingCpuChanged(desired);
            notified = true;
        }
        return notified;
    }

    @Nullable
    private CpuPublication resolveCraftingCpuPublication() {
        TrinityDataCoreCraftingRuntime runtime = boundCraftingRuntime();
        TrinityCraftingExecutionRoute route = craftingExecutionRoute();
        if (runtime == null || route == null) {
            return null;
        }

        IGridNode node = this.getMainNode().getNode();
        IGrid serviceGrid = route.serviceGrid();
        if (!(serviceGrid.getCraftingService() instanceof TrinityCraftingRuntimeRegistry registry)) {
            LOGGER.error("Cannot publish Trinity CPU at {} because the AE2 crafting service has no runtime registry",
                    this.worldPosition);
            return null;
        }
        return new CpuPublication(registry, route, node, runtime);
    }

    @Nullable
    private CpuPublication withdrawCraftingCpuPublication() {
        CpuPublication publication = this.cpuPublication;
        if (publication == null) {
            return null;
        }
        this.cpuPublication = null;
        return publication.registry().data_energistics$withdraw(publication.node()) ? publication : null;
    }

    private void withdrawCraftingCpuPublicationAndNotify() {
        CpuPublication withdrawn = withdrawCraftingCpuPublication();
        if (withdrawn != null) {
            notifyCraftingCpuChanged(withdrawn);
            return;
        }

        withdrawUntrackedCraftingCpuPublicationAndNotify();
    }

    private boolean withdrawUntrackedCraftingCpuPublicationAndNotify() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null) {
            return false;
        }
        IGrid grid = node.getGrid();
        if (grid.getCraftingService() instanceof TrinityCraftingRuntimeRegistry registry && registry.data_energistics$withdraw(node)) {
            grid.postEvent(new GridCraftingCpuChange(node));
            return true;
        }
        return false;
    }

    private static void notifyCraftingCpuChanged(CpuPublication publication) {
        publication.route().serviceGrid().postEvent(new GridCraftingCpuChange(publication.node()));
    }

    private void refreshTerminalPartitionsSafely() {
        if (!this.terminalPartitionsDirty) {
            return;
        }
        this.terminalPartitionsDirty = false;
        try {
            refreshTerminalPartitions();
        } catch (RuntimeException exception) {
            this.terminalPartitionsDirty = true;
            detachTerminalPartitions();
            LOGGER.error("Failed to mount Trinity pattern terminal partitions at {}", this.worldPosition, exception);
        }
    }

    private void refreshTerminalPartitions() {
        TrinityDataCoreBlockEntity host = patternProviderHost();
        IGridNode accessNode = this.getMainNode().getNode();
        if (host == null || !(this.level instanceof ServerLevel serverLevel) || accessNode == null ||
                !accessNode.isActive()) {
            detachTerminalPartitions();
            return;
        }

        IGrid grid = accessNode.getGrid();
        long layoutRevision = host.getPatternCatalog().layoutSnapshot().revision();
        if (host.getHostId().equals(this.terminalPartitionHostId) &&
                this.terminalPartitionGrid == grid &&
                this.terminalPartitionLayoutRevision == layoutRevision) {
            if (!this.terminalPartitionAttachmentCheckRequested || terminalPartitionsAttachedTo(grid)) {
                this.terminalPartitionAttachmentCheckRequested = false;
                return;
            }
        }
        List<TrinityPatternTerminalPartition> desired = TrinityPatternTerminalPartition.createLayout(
                host.getPatternCatalog(),
                terminalGroup());
        Map<TrinityPatternTerminalPartition.PartitionKey, TrinityPatternTerminalPartition> existingByKey = new HashMap<>();
        for (TrinityPatternTerminalPartition existing : this.terminalPartitions) {
            existingByKey.put(existing.key(), existing);
        }

        List<TrinityPatternTerminalPartition> reconciled = desired.stream().map(next -> {
            TrinityPatternTerminalPartition existing = existingByKey.remove(next.key());
            if (existing != null && existing.hasSameLayout(next)) {
                return existing;
            }
            if (existing != null) {
                existing.detach();
            }
            return next;
        }).toList();
        for (TrinityPatternTerminalPartition removed : existingByKey.values()) {
            removed.detach();
        }

        this.terminalPartitions = List.copyOf(reconciled);
        this.managedTerminalPartitions.clear();
        this.managedTerminalPartitions.addAll(this.terminalPartitions);
        for (TrinityPatternTerminalPartition partition : this.terminalPartitions) {
            if (!partition.isAttachedTo(grid)) {
                partition.detach();
                partition.attach(serverLevel, accessNode);
            }
        }
        this.terminalPartitionHostId = host.getHostId();
        this.terminalPartitionGrid = grid;
        this.terminalPartitionLayoutRevision = layoutRevision;
        this.terminalPartitionAttachmentCheckRequested = false;
    }

    private boolean terminalPartitionsAttachedTo(IGrid grid) {
        for (TrinityPatternTerminalPartition partition : this.terminalPartitions) {
            if (!partition.isAttachedTo(grid)) {
                return false;
            }
        }
        return true;
    }

    private PatternContainerGroup terminalGroup() {
        AEItemKey icon = AEItemKey.of(DEBlocks.TRINITY_DATA_CORE.get());
        return new PatternContainerGroup(icon, DEBlocks.TRINITY_DATA_CORE.get().getName(), List.of());
    }

    private void detachTerminalPartitions() {
        for (TrinityPatternTerminalPartition partition : this.terminalPartitions) {
            partition.detach();
        }
        this.terminalPartitions = List.of();
        this.managedTerminalPartitions.clear();
        this.terminalPartitionHostId = null;
        this.terminalPartitionGrid = null;
        this.terminalPartitionLayoutRevision = -1L;
        this.terminalPartitionAttachmentCheckRequested = true;
    }

    /** Advances active input/output transfer work without allowing one depot to monopolize the server tick. */
    private void tickStorageTransfer() {
        if (this.storageMode == StorageMode.STORAGE) {
            if (this.transferGrid != null || !this.transferQueue.isEmpty() || this.transferWatcherConfigured) {
                resetTransferState();
            }
            return;
        }

        TrinityDataCoreBlockEntity host = boundHost(false);
        IGrid grid = accessGrid();
        if (host == null || grid == null || !(this.level instanceof ServerLevel serverLevel)) {
            if (this.transferGrid != null || !this.transferQueue.isEmpty() || this.transferWatcherConfigured) {
                resetTransferState();
            }
            return;
        }

        MEStorage aggregateStorage = grid.getStorageService().getInventory();
        FiniteNetworkStorageAccess finiteStorage = (FiniteNetworkStorageAccess) aggregateStorage;
        updateTransferContext(host, grid, finiteStorage.storageStructureRevision());

        TrinityDataCoreStorageSavedData storageData = TrinityDataCoreStorageSavedData.get(serverLevel.getServer());
        if (this.storageMode.pullsFromNetwork()) {
            configureInputWatcher();
            if (this.inputSnapshotDirty) {
                rebuildInputSnapshot(grid);
            }
            processInputQueue(finiteStorage, new TrinityStorageTarget(
                    storageData,
                    host.getStorageId(),
                    host.storageProfile()));
            return;
        }

        disableTransferWatcher();
        if (this.outputSnapshotDirty) {
            rebuildOutputSnapshot(storageData, host.getStorageId());
        }
        processOutputQueue(storageData, host, aggregateStorage);
    }

    private void updateTransferContext(TrinityDataCoreBlockEntity host, IGrid grid, long storageStructureRevision) {
        UUID storageId = host.getStorageId();
        TrinityDataCoreStorageProfile storageProfile = host.storageProfile();
        boolean contextChanged = this.transferGrid != grid ||
                !storageId.equals(this.transferStorageId) ||
                !storageProfile.equals(this.transferStorageProfile);
        if (contextChanged) {
            clearTransferQueue();
            disableTransferWatcher();
            this.transferGrid = grid;
            this.transferStorageId = storageId;
            this.transferStorageProfile = storageProfile;
            this.transferStorageStructureRevision = storageStructureRevision;
            this.inputSnapshotDirty = true;
            this.outputSnapshotDirty = true;
            return;
        }

        if (this.storageMode.pullsFromNetwork() &&
                this.transferStorageStructureRevision != storageStructureRevision) {
            clearTransferQueue();
            this.transferStorageStructureRevision = storageStructureRevision;
            this.inputSnapshotDirty = true;
        }
    }

    private void configureInputWatcher() {
        if (this.transferWatcher == null || this.transferWatcherConfigured) {
            return;
        }
        this.transferWatcher.setWatchAll(true);
        this.transferWatcherConfigured = true;
        this.inputSnapshotDirty = true;
    }

    private void disableTransferWatcher() {
        if (this.transferWatcher != null && this.transferWatcherConfigured) {
            this.transferWatcher.reset();
        }
        this.transferWatcherConfigured = false;
    }

    private void rebuildInputSnapshot(IGrid grid) {
        clearTransferQueue();
        for (var entry : grid.getStorageService().getCachedInventory()) {
            if (entry.getLongValue() > 0L) {
                enqueueTransfer(entry.getKey());
            }
        }
        this.inputSnapshotDirty = false;
    }

    private void rebuildOutputSnapshot(TrinityDataCoreStorageSavedData storageData, UUID storageId) {
        clearTransferQueue();
        KeyCounter contents = new KeyCounter();
        storageData.addAvailableStacks(storageId, contents);
        for (var entry : contents) {
            if (entry.getLongValue() > 0L) {
                enqueueTransfer(entry.getKey());
            }
        }
        this.outputSnapshotDirty = false;
    }

    private void processInputQueue(FiniteNetworkStorageAccess finiteStorage, FiniteTransferTarget target) {
        long startedAtNanos = System.nanoTime();
        int scheduledKeys = Math.min(TRANSFER_KEYS_PER_TICK, this.transferQueue.size());
        for (int processedKeys = 0; processedKeys < scheduledKeys && System.nanoTime() - startedAtNanos < TRANSFER_NANOS_PER_TICK; processedKeys++) {
            AEKey key = this.transferQueue.removeFirst();
            boolean retry = false;
            try {
                FiniteTransferResult result = finiteStorage.transferFinite(
                        key,
                        Long.MAX_VALUE,
                        this.transferActionSource,
                        target);
                if (!result.consistent()) {
                    logInputTransferMismatch(key, result);
                }
                retry = result.retrySuggested();
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to transfer finite AE storage key {} into Trinity storage at {}",
                        key,
                        this.worldPosition,
                        exception);
                retry = true;
            } finally {
                finishTransferKey(key, retry);
            }
        }
    }

    private void processOutputQueue(TrinityDataCoreStorageSavedData storageData,
                                    TrinityDataCoreBlockEntity host,
                                    MEStorage aggregateStorage) {
        long startedAtNanos = System.nanoTime();
        int scheduledKeys = Math.min(TRANSFER_KEYS_PER_TICK, this.transferQueue.size());
        for (int processedKeys = 0; processedKeys < scheduledKeys && System.nanoTime() - startedAtNanos < TRANSFER_NANOS_PER_TICK; processedKeys++) {
            AEKey key = this.transferQueue.removeFirst();
            boolean retry = false;
            try {
                OutputTransferResult result = transferOutputKey(
                        storageData,
                        host,
                        aggregateStorage,
                        key);
                if (!result.consistent()) {
                    logOutputTransferMismatch(key, result);
                }
                retry = result.retrySuggested();
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to transfer Trinity storage key {} into AE storage at {}",
                        key,
                        this.worldPosition,
                        exception);
                retry = true;
            } finally {
                finishTransferKey(key, retry);
            }
        }
    }

    private OutputTransferResult transferOutputKey(TrinityDataCoreStorageSavedData storageData,
                                                   TrinityDataCoreBlockEntity host,
                                                   MEStorage target,
                                                   AEKey key) {
        UUID storageId = host.getStorageId();
        TrinityDataCoreStorageProfile storageProfile = host.storageProfile();
        long simulatedExtraction = storageData.extract(storageId, key, Long.MAX_VALUE, Actionable.SIMULATE);
        if (simulatedExtraction <= 0L) {
            return OutputTransferResult.EMPTY;
        }

        long simulatedTargetInsert = target.insert(
                key,
                simulatedExtraction,
                Actionable.SIMULATE,
                this.transferActionSource);
        if (simulatedTargetInsert <= 0L) {
            return OutputTransferResult.RETRY;
        }

        long plannedExtraction = Math.min(simulatedExtraction, simulatedTargetInsert);
        long extracted = storageData.extract(storageId, key, plannedExtraction, Actionable.MODULATE);
        if (extracted != plannedExtraction) {
            long restored = storageData.insert(storageId, key, extracted, Actionable.MODULATE, storageProfile);
            return new OutputTransferResult(
                    plannedExtraction,
                    extracted,
                    0L,
                    extracted,
                    restored,
                    true);
        }

        long accepted;
        try {
            accepted = target.insert(key, extracted, Actionable.MODULATE, this.transferActionSource);
        } catch (RuntimeException exception) {
            long restored = storageData.insert(storageId, key, extracted, Actionable.MODULATE, storageProfile);
            if (restored != extracted) {
                exception.addSuppressed(new IllegalStateException(
                        "Trinity output transfer restored " + restored + " of " + extracted +
                                " to storage " + storageId));
            }
            throw exception;
        }

        long rollbackAmount = accepted < 0L || accepted > extracted ? extracted : extracted - accepted;
        long restored = storageData.insert(storageId, key, rollbackAmount, Actionable.MODULATE, storageProfile);
        boolean remaining = storageData.extract(storageId, key, Long.MAX_VALUE, Actionable.SIMULATE) > 0L;
        return new OutputTransferResult(
                plannedExtraction,
                extracted,
                accepted,
                rollbackAmount,
                restored,
                accepted != extracted || remaining);
    }

    private void logInputTransferMismatch(AEKey key, FiniteTransferResult result) {
        LOGGER.error(
                "Finite AE input transfer mismatch at {} for {}: transferred={}, planned={}, extracted={}, " +
                        "targetAccepted={}, sourceRollback={}/{}, skippedInfinite={}",
                this.worldPosition,
                key,
                result.transferred(),
                result.plannedSourceExtraction(),
                result.sourceExtracted(),
                result.targetAccepted(),
                result.sourceRollbackAccepted(),
                result.sourceRollback(),
                result.skippedInfiniteSources());
    }

    private void logOutputTransferMismatch(AEKey key, OutputTransferResult result) {
        LOGGER.error(
                "Trinity output transfer mismatch at {} for {}: planned={}, extracted={}, targetAccepted={}, " +
                        "storageRollback={}/{}",
                this.worldPosition,
                key,
                result.plannedExtraction(),
                result.sourceExtracted(),
                result.targetAccepted(),
                result.sourceRollbackAccepted(),
                result.sourceRollback());
    }

    private void finishTransferKey(AEKey key, boolean retry) {
        this.queuedTransferKeys.remove(key);
        if (retry) {
            enqueueTransfer(key);
        }
    }

    private void enqueueTransfer(AEKey key) {
        if (this.queuedTransferKeys.add(key)) {
            this.transferQueue.addLast(key);
        }
    }

    private void clearTransferQueue() {
        this.transferQueue.clear();
        this.queuedTransferKeys.clear();
    }

    private void resetTransferState() {
        disableTransferWatcher();
        clearTransferQueue();
        this.transferGrid = null;
        this.transferStorageId = null;
        this.transferStorageProfile = null;
        this.transferStorageStructureRevision = -1L;
        this.inputSnapshotDirty = true;
        this.outputSnapshotDirty = true;
    }

    private final class TransferWatcherNode implements IStorageWatcherNode {

        @Override
        public void updateWatcher(IStackWatcher newWatcher) {
            transferWatcher = newWatcher;
            transferWatcherConfigured = false;
            if (storageMode.pullsFromNetwork()) {
                clearTransferQueue();
                inputSnapshotDirty = true;
                configureInputWatcher();
            }
        }

        @Override
        public void onStackChange(AEKey what, long amount) {
            if (storageMode.pullsFromNetwork() && amount > 0L) {
                enqueueTransfer(what);
            }
        }
    }

    private record TrinityStorageTarget(TrinityDataCoreStorageSavedData storageData,
                                        UUID storageId,
                                        TrinityDataCoreStorageProfile storageProfile)
            implements FiniteTransferTarget {

        @Override
        public long simulateInsert(AEKey what, long amount) {
            return this.storageData.insert(
                    this.storageId,
                    what,
                    amount,
                    Actionable.SIMULATE,
                    this.storageProfile);
        }

        @Override
        public long insert(AEKey what, long amount) {
            return this.storageData.insert(
                    this.storageId,
                    what,
                    amount,
                    Actionable.MODULATE,
                    this.storageProfile);
        }
    }

    private record OutputTransferResult(long plannedExtraction,
                                        long sourceExtracted,
                                        long targetAccepted,
                                        long sourceRollback,
                                        long sourceRollbackAccepted,
                                        boolean retrySuggested) {

        private static final OutputTransferResult EMPTY = new OutputTransferResult(0L, 0L, 0L, 0L, 0L, false);
        private static final OutputTransferResult RETRY = new OutputTransferResult(0L, 0L, 0L, 0L, 0L, true);

        private boolean consistent() {
            return this.plannedExtraction == this.sourceExtracted &&
                    this.sourceExtracted == this.targetAccepted + this.sourceRollback &&
                    this.sourceRollback == this.sourceRollbackAccepted;
        }
    }

    private final class HatchStorageProvider implements IStorageProvider {

        @Override
        public void mountInventories(IStorageMounts storageMounts) {
            TrinityDataCoreBlockEntity host = boundHost(false);
            if (storageMode.mountsStorage() && host != null &&
                    host.isLeaseOwner(TrinityInformationExchangeDepotBlockEntity.this) &&
                    host.isStorageAvailable()) {
                storageMounts.mount(networkStorage, host.getStoragePriority());
            }
        }
    }

    private record CpuPublication(TrinityCraftingRuntimeRegistry registry,
                                  TrinityCraftingExecutionRoute route,
                                  IGridNode node,
                                  TrinityDataCoreCraftingRuntime runtime) {

        private boolean matches(CpuPublication other) {
            return this.registry == other.registry && this.route.isCurrent(other.route) && this.node == other.node &&
                    this.runtime == other.runtime;
        }

        private boolean hasSameNotificationTarget(CpuPublication other) {
            return this.route.serviceGrid() == other.route.serviceGrid() && this.node == other.node;
        }
    }

    /**
     * Identifies the exact immutable pattern view already indexed by one AE grid.
     */
    private record PatternPublication(UUID hostId,
                                      IGrid grid,
                                      IGridNode node,
                                      long revision) {

        private boolean matches(PatternPublication other) {
            return this.hostId.equals(other.hostId) &&
                    this.grid == other.grid &&
                    this.node == other.node &&
                    this.revision == other.revision;
        }
    }

    private final class HatchCraftingProvider implements TargetedCountedCraftingProvider {

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            TrinityDataCoreBlockEntity host = patternProviderHost();
            return host == null ? List.of() : host.getPatternCatalog().getAvailablePatterns();
        }

        @Override
        public int getPatternPriority() {
            TrinityDataCoreBlockEntity host = patternProviderHost();
            return host == null ? 0 : host.getPatternPriority();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            CountedCraftingAdmission admission = prepareBatch(patternDetails, inputHolder, 1L);
            return admission != null && admission.commit(inputHolder);
        }

        @Override
        public @Nullable CountedCraftingAdmission prepareBatch(IPatternDetails patternDetails,
                                                               KeyCounter[] prototype,
                                                               long requestedCount) {
            return prepareBatch(
                    patternDetails,
                    prototype,
                    requestedCount,
                    CraftingDispatchTargetAvailability.all()).admission();
        }

        @Override
        public CountedCraftingPreparation prepareBatch(
                                                       IPatternDetails patternDetails,
                                                       KeyCounter[] prototype,
                                                       long requestedCount,
                                                       CraftingDispatchTargetAvailability targetAvailability) {
            if (patternDetails == null) {
                throw new IllegalArgumentException("Trinity pattern details must not be null");
            }
            if (prototype == null) {
                throw new IllegalArgumentException("Trinity input prototype must not be null");
            }
            if (requestedCount <= 0L) {
                throw new IllegalArgumentException("requestedCount must be positive");
            }
            if (targetAvailability == null) {
                throw new IllegalArgumentException("Crafting dispatch target availability must not be null");
            }
            if (!targetAvailability.canAttempt(CRAFTING_CATALOG_TARGET)) {
                return CountedCraftingPreparation.rejected(
                        CraftingDispatchRejection.targeted(
                                CraftingDispatchStatus.NO_CAPACITY,
                                CRAFTING_CATALOG_TARGET));
            }
            TrinityDataCoreBlockEntity host = patternProviderHost();
            if (host == null || level == null || level.isClientSide()) {
                return CountedCraftingPreparation.rejected(
                        CraftingDispatchRejection.scoped(CraftingDispatchStatus.OFFLINE));
            }
            CraftingAdmissionToken token = host.issueCraftingAdmission(
                    TrinityInformationExchangeDepotBlockEntity.this,
                    patternDetails,
                    level.getGameTime(),
                    requestedCount);
            if (token == null) {
                return CountedCraftingPreparation.rejected(
                        CraftingDispatchRejection.targeted(
                                CraftingDispatchStatus.NO_CAPACITY,
                                CRAFTING_CATALOG_TARGET));
            }
            return CountedCraftingPreparation.accepted(
                    new HatchCraftingAdmission(
                            host,
                            token,
                            prototype),
                    CRAFTING_CATALOG_TARGET);
        }

        @Override
        public List<ProviderCapacitySnapshot> snapshotCapacity(
                                                               CraftingProviderId providerId,
                                                               IPatternDetails patternDetails,
                                                               KeyCounter[] prototype,
                                                               long requestedCrafts,
                                                               String patternIdentity,
                                                               long publicationRevision,
                                                               long capacityRevision,
                                                               long captureTick) {
            TrinityDataCoreBlockEntity host = patternProviderHost();
            if (host == null || level == null || level.isClientSide() ||
                    !host.getPatternCatalog().getAvailablePatterns().contains(patternDetails)) {
                return List.of();
            }
            return List.of(new ProviderCapacitySnapshot(
                    providerId,
                    CRAFTING_CATALOG_TARGET,
                    Optional.empty(),
                    patternIdentity,
                    publicationRevision,
                    capacityRevision,
                    captureTick,
                    ProviderRoutingMode.TARGETED,
                    new DispatchCapacity.Known(requestedCrafts),
                    new DispatchCapacity.Known(requestedCrafts)));
        }

        @Override
        @Nullable
        public CountedCraftingAdmission prepareBatchForTarget(
                                                              IPatternDetails patternDetails,
                                                              KeyCounter[] prototype,
                                                              long requestedCount,
                                                              CraftingDispatchTarget target) {
            if (!CRAFTING_CATALOG_TARGET.equals(target)) {
                return null;
            }
            return prepareBatch(patternDetails, prototype, requestedCount);
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    /**
     * Commits the full logical batch to the exact Trinity catalog selected during admission.
     */
    private static final class HatchCraftingAdmission implements CountedCraftingAdmission {

        private final TrinityDataCoreBlockEntity host;
        private final CraftingAdmissionToken token;
        private final KeyCounter[] preparedPrototype;
        private boolean attempted;

        private HatchCraftingAdmission(TrinityDataCoreBlockEntity host,
                                       CraftingAdmissionToken token,
                                       KeyCounter[] preparedPrototype) {
            this.host = host;
            this.token = token;
            this.preparedPrototype = preparedPrototype;
        }

        @Override
        public long count() {
            return this.token.count();
        }

        @Override
        public boolean commit(KeyCounter[] prototype) {
            if (prototype != this.preparedPrototype) {
                throw new IllegalArgumentException("Admission must be committed with its prepared prototype");
            }
            if (this.attempted) {
                throw new IllegalStateException("Admission has already been committed");
            }
            this.attempted = true;
            return this.host.commitCraftingAdmission(this.token, prototype);
        }
    }

    private final class HatchStorage implements MEStorage {

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            if (!storageMode.mountsStorage()) {
                return 0L;
            }
            TrinityDataCoreBlockEntity host = boundHost();
            if (!canUseStorage(host) || !(level instanceof ServerLevel serverLevel)) {
                return 0L;
            }
            long inserted = TrinityDataCoreStorageSavedData.get(serverLevel.getServer())
                    .insert(host.getStorageId(), what, amount, mode, host.storageProfile());
            if (inserted > 0L && mode == Actionable.MODULATE) {
                refreshTrinityStorageContent();
            }
            return inserted;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            if (!storageMode.mountsStorage()) {
                return 0L;
            }
            TrinityDataCoreBlockEntity host = boundHost();
            if (!canUseStorage(host) || !(level instanceof ServerLevel serverLevel)) {
                return 0L;
            }
            long extracted = TrinityDataCoreStorageSavedData.get(serverLevel.getServer())
                    .extract(host.getStorageId(), what, amount, mode);
            if (extracted > 0L && mode == Actionable.MODULATE) {
                refreshTrinityStorageContent();
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (!storageMode.mountsStorage()) {
                return;
            }
            TrinityDataCoreBlockEntity host = boundHost();
            if (!canUseStorage(host) || !(level instanceof ServerLevel serverLevel)) {
                return;
            }
            TrinityDataCoreStorageSavedData.get(serverLevel.getServer()).addAvailableStacks(host.getStorageId(), out);
        }

        @Override
        public Component getDescription() {
            return DEBlocks.TRINITY_INFORMATION_EXCHANGE_DEPOT.get().getName();
        }

        private boolean canUseStorage(@Nullable TrinityDataCoreBlockEntity host) {
            return host != null && isCandidateOnline() &&
                    host.isLeaseOwner(TrinityInformationExchangeDepotBlockEntity.this) &&
                    host.isStorageAvailable();
        }
    }

    /** Selects one explicit exchange behavior while retaining stable network IDs and serialized names. */
    public enum StorageMode {

        STORAGE("storage"),
        INPUT("input"),
        OUTPUT("output");

        private final String serializedName;

        StorageMode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public boolean mountsStorage() {
            return this == STORAGE;
        }

        public boolean pullsFromNetwork() {
            return this == INPUT;
        }

        public boolean pushesToNetwork() {
            return this == OUTPUT;
        }

        public int networkId() {
            return switch (this) {
                case STORAGE -> 0;
                case INPUT -> 1;
                case OUTPUT -> 2;
            };
        }

        public static StorageMode fromNetworkId(int networkId) {
            return switch (networkId) {
                case 0 -> STORAGE;
                case 1 -> INPUT;
                case 2 -> OUTPUT;
                default -> throw new IllegalArgumentException("Unknown information exchange mode " + networkId);
            };
        }

        private static StorageMode fromSerializedName(String name) {
            return switch (name) {
                case "storage" -> STORAGE;
                case "input" -> INPUT;
                case "output" -> OUTPUT;
                default -> {
                    LOGGER.warn("Unknown Trinity information exchange storage mode '{}'; using storage mode", name);
                    yield STORAGE;
                }
            };
        }
    }
}
