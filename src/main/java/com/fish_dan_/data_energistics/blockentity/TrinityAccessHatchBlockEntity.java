package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity.CraftingAdmissionToken;
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
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternTerminalPartition;
import com.fish_dan_.data_energistics.menu.TrinityCraftingStatusSelection;
import com.fish_dan_.data_energistics.menu.TrinityCraftingStatusSelection.TargetState;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.menu.trinity.TrinityAccessHatchMenuHost;
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
import appeng.api.config.Settings;
import appeng.api.config.ShowPatternProviders;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
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
import appeng.menu.ISubMenu;
import appeng.util.ConfigManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * AE network hatch that exposes the bound Trinity Data Core UUID storage instead of storing contents locally.
 */
public class TrinityAccessHatchBlockEntity extends AENetworkedBlockEntity
                                           implements CompartmentPart, ITerminalHost, TrinityAccessHatchMenuHost {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final TrinityCraftingRouteResolver CRAFTING_ROUTE_RESOLVER = new TrinityCraftingRouteResolver();
    /** Stable target identity for the bound Trinity pattern catalog. */
    private static final CraftingDispatchTarget CRAFTING_CATALOG_TARGET = new CraftingDispatchTarget("trinity-pattern-catalog");
    private static final String TERMINAL_CONFIG_TAG = "terminal_config";

    private final MEStorage networkStorage = new HatchStorage();
    private final IStorageProvider storageProvider = new HatchStorageProvider();
    private final ICraftingProvider craftingProvider = new HatchCraftingProvider();
    private final ConfigManager configManager = new ConfigManager(this::onTerminalConfigChanged);
    private final Set<PatternContainer> managedTerminalPartitions = Collections.newSetFromMap(new IdentityHashMap<>());
    @Nullable
    private TrinityAccessHatchBindingState compartmentBindingState;
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
    private boolean loadingTerminalConfig;

    public TrinityAccessHatchBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.TRINITY_ACCESS_HATCH_BLOCK_ENTITY.get(), blockPos, blockState);
        this.configManager.registerSetting(
                Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,
                ShowPatternProviders.VISIBLE);
        this.getMainNode()
                .addService(IStorageProvider.class, this.storageProvider)
                .addService(ICraftingProvider.class, this.craftingProvider)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setVisualRepresentation(DEBlocks.TRINITY_ACCESS_HATCH.get())
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

    /**
     * Crafting status does not expose upgrades for the Trinity access hatch.
     */
    @Override
    public IUpgradeInventory getUpgrades() {
        return UpgradeInventories.empty();
    }

    /**
     * Shares the persisted pattern-provider visibility setting with AE2's pattern-access terminal menu.
     */
    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.loadingTerminalConfig = true;
        try {
            if (data.contains(TERMINAL_CONFIG_TAG)) {
                this.configManager.readFromNBT(data.getCompound(TERMINAL_CONFIG_TAG), registries);
            }
        } finally {
            this.loadingTerminalConfig = false;
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        CompoundTag terminalConfig = new CompoundTag();
        this.configManager.writeToNBT(terminalConfig, registries);
        data.put(TERMINAL_CONFIG_TAG, terminalConfig);
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return DEBlocks.TRINITY_DATA_CORE.get().asItem().getDefaultInstance();
    }

    /**
     * Resolves the live bound controller instead of returning to the access hatch itself.
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
            LOGGER.error("Failed to resolve the Trinity host menu at {} from access hatch {}",
                    host.getBlockPos(), this.worldPosition, exception);
            player.closeContainer();
        }
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        finishGridBootReevaluation();
        updateActiveState();
        refreshTerminalPartitionsSafely();
    }

    /**
     * Notifies the selected grid that only the host storage content changed.
     */
    public void refreshTrinityStorageContent() {
        if (!canRefreshGridServices()) {
            return;
        }
        requestStorageUpdate();
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
        return CompartmentType.TRINITY_ACCESS;
    }

    @Override
    public VerticalMultiBlockPos compartmentPos() {
        return new VerticalMultiBlockPos(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
    }

    @Nullable
    @Override
    public CompartmentHost compartmentHost() {
        TrinityAccessHatchBindingState binding = this.compartmentBindingState;
        return binding == null ? null : binding.host();
    }

    @Nullable
    @Override
    public String compartmentStructureName() {
        TrinityAccessHatchBindingState binding = this.compartmentBindingState;
        return binding == null ? null : binding.structureName();
    }

    @Override
    public boolean isCompartmentBound() {
        TrinityAccessHatchBindingState binding = this.compartmentBindingState;
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
        TrinityAccessHatchBindingState requestedBinding = createBindingState(
                structureName,
                host,
                verticalController,
                verticalBindingEpoch);
        TrinityAccessHatchBindingState currentBinding = this.compartmentBindingState;
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
            releaseBinding(currentBinding, "retrying or completing a Trinity access hatch replacement");
            return;
        }
        releaseBinding(currentBinding, requestedBinding, "replacing an existing Trinity access hatch binding");
    }

    @Override
    public void compartment$unbindFromHost(String structureName, CompartmentHost host) {
        try {
            requireUnbindArguments(structureName, host);
        } catch (RuntimeException exception) {
            LOGGER.error("Rejecting invalid Trinity access hatch unbind at {}: host={}, structure={}",
                    this.worldPosition, host, structureName, exception);
            throw exception;
        }
        TrinityAccessHatchBindingState currentBinding = this.compartmentBindingState;
        if (currentBinding == null || !currentBinding.matches(host, structureName)) {
            unregisterHost(structureName, host);
            return;
        }
        LOGGER.debug("Ignoring untagged Trinity access hatch unbind at {} for host={}, structure={}; " +
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
        TrinityAccessHatchBindingState binding = this.compartmentBindingState;
        return binding != null && binding.requiresBindingRetry(host, structureName);
    }

    @Override
    public void compartment$unbindFromHost(CompartmentBindingHandle bindingHandle) {
        if (!(bindingHandle instanceof TrinityAccessHatchBindingState expectedBinding)) {
            LOGGER.warn("Ignoring foreign Trinity access hatch binding handle at {}: {}",
                    this.worldPosition,
                    bindingHandle);
            return;
        }
        if (this.compartmentBindingState != expectedBinding) {
            LOGGER.debug("Ignoring stale Trinity access hatch binding handle at {} for host={}, structure={}",
                    this.worldPosition,
                    expectedBinding.host(),
                    expectedBinding.structureName());
            return;
        }
        releaseBinding(expectedBinding, "identity-aware Trinity access hatch unbind");
    }

    @Override
    public void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                     String structureName,
                                                     VerticalMultiBlockContext<?> context) {
        LOGGER.debug("Ignoring untagged Trinity access hatch vertical bind at {} for controller={}, structure={}; " +
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
            LOGGER.warn("Ignoring Trinity access hatch bind from non-compartment controller at {}: {}",
                    this.worldPosition,
                    controller);
            return;
        }
        bindToHost(structureName, host, controller, bindingEpoch);
    }

    @Override
    public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller, String structureName) {
        LOGGER.debug("Ignoring untagged Trinity access hatch vertical removal at {} for controller={}, structure={}; " +
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
            LOGGER.warn("Ignoring Trinity access hatch removal from non-compartment controller at {}: {}",
                    this.worldPosition, controller);
            return;
        }
        TrinityAccessHatchBindingState currentBinding = this.compartmentBindingState;
        if (currentBinding == null) {
            return;
        }
        if (!currentBinding.matchesVerticalRemoval(controller, structureName, bindingEpoch)) {
            LOGGER.debug("Ignoring stale Trinity access hatch vertical removal at {} for host={}, structure={}, epoch={}",
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
        LOGGER.debug("Ignoring untagged Trinity access hatch legacy vertical removal at {} for controller={}; " +
                "the vertical runtime must return its captured binding epoch",
                this.worldPosition,
                controller);
    }

    private TrinityAccessHatchBindingState createBindingState(String structureName,
                                                              CompartmentHost host,
                                                              @Nullable VerticalMultiBlockController verticalController,
                                                              long verticalBindingEpoch) {
        try {
            return verticalController == null ? TrinityAccessHatchBindingState.active(structureName, host) :
                    TrinityAccessHatchBindingState.active(
                            structureName,
                            host,
                            verticalController,
                            verticalBindingEpoch);
        } catch (RuntimeException exception) {
            LOGGER.error("Rejecting invalid Trinity access hatch binding at {}: host={}, structure={}",
                    this.worldPosition, host, structureName, exception);
            throw exception;
        }
    }

    private void registerHost(TrinityAccessHatchBindingState binding) {
        try {
            CompartmentPart.super.compartment$bindToHost(binding.structureName(), binding.host());
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to register Trinity access hatch at {} with host={}, structure={}",
                    this.worldPosition, binding.host(), binding.structureName(), exception);
            throw exception;
        }
    }

    private void ensureHostRegistration(TrinityAccessHatchBindingState binding) {
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
            LOGGER.error("Failed to remove stale Trinity access hatch registration at {} from host={}, structure={}",
                    this.worldPosition, host, structureName, exception);
            throw exception;
        }
    }

    private void activateBinding(TrinityAccessHatchBindingState binding) {
        registerHost(binding);
        this.compartmentBindingState = binding;
        this.lastUnavailableReason = null;
        requestLeaseReevaluation(binding.host());
    }

    private void releaseBinding(TrinityAccessHatchBindingState expectedBinding, String reason) {
        releaseBinding(expectedBinding, null, reason);
    }

    private void releaseBinding(TrinityAccessHatchBindingState expectedBinding,
                                @Nullable TrinityAccessHatchBindingState replacementBinding,
                                String reason) {
        if (this.compartmentBindingState != expectedBinding) {
            return;
        }
        TrinityAccessHatchBindingState releasingBinding = expectedBinding.isReleasing() ? expectedBinding : expectedBinding.releasing();
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
                LOGGER.error("Failed to release Trinity access hatch binding at {} ({})", this.worldPosition, reason, exception);
                throw exception;
            } finally {
                releasingBinding.finishReleaseAttempt();
            }
        }
        completeReleasedBinding(releasingBinding, reason);
    }

    private void completeReleasedBinding(TrinityAccessHatchBindingState releasingBinding, String reason) {
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
            TrinityAccessHatchBindingState replacementBinding = releasingBinding.pendingReplacement();
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
            LOGGER.error("Failed to register queued Trinity access hatch replacement at {} ({})",
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
            throw new IllegalArgumentException("Trinity access hatch unbind host must not be null");
        }
        if (structureName == null || structureName.isBlank()) {
            throw new IllegalArgumentException("Trinity access hatch unbind structure name must not be blank");
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
    public boolean isAccessHatchMenuValid(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Trinity access hatch menu player cannot be null");
        }
        Level currentLevel = this.level;
        return currentLevel != null &&
                !this.isRemoved() &&
                player.level() == currentLevel &&
                currentLevel.getBlockEntity(this.worldPosition) == this &&
                currentLevel.getBlockState(this.worldPosition).is(DEBlocks.TRINITY_ACCESS_HATCH.get()) &&
                player.distanceToSqr(
                        this.worldPosition.getX() + 0.5D,
                        this.worldPosition.getY() + 0.5D,
                        this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    /**
     * Validates the complete server route before a data-core management operation is allowed.
     */
    @Override
    public boolean isAccessHatchManagementAvailable(Player player) {
        Level currentLevel = this.level;
        return player instanceof ServerPlayer &&
                isAccessHatchMenuValid(player) &&
                currentLevel != null &&
                !currentLevel.isClientSide() &&
                isAccessOnline();
    }

    /**
     * Accepts only an identity currently mounted by this exact lease-holding hatch.
     */
    @Override
    public boolean isManagedPatternContainer(PatternContainer container) {
        if (container == null) {
            throw new IllegalArgumentException("Trinity pattern container cannot be null");
        }
        return isAccessOnline() && this.managedTerminalPartitions.contains(container);
    }

    /**
     * Refunds installed patterns through the currently bound host after revalidating the access lease.
     */
    @Override
    public TrinityHostedActionStatus refundPatterns(Player player) {
        TrinityDataCoreBlockEntity host = refundHost(player);
        if (host == null) {
            return TrinityHostedActionStatus.REJECTED;
        }
        return switch (host.tryRefundPatterns(player)) {
            case COMPLETED -> TrinityHostedActionStatus.COMPLETED;
            case NO_PATTERNS -> TrinityHostedActionStatus.NO_OP;
            case BLOCKED_BY_WORK, STALE -> TrinityHostedActionStatus.STALE_STATE;
            case DELIVERY_REJECTED, DELIVERY_FAILED -> TrinityHostedActionStatus.DELIVERY_FAILED;
            case INTERNAL_ERROR -> TrinityHostedActionStatus.INTERNAL_ERROR;
        };
    }

    /**
     * Refunds queued inputs and pending outputs through the current host after revalidating the access lease.
     */
    @Override
    public TrinityHostedActionStatus refundRetainedItems(Player player) {
        TrinityDataCoreBlockEntity host = refundHost(player);
        if (host == null) {
            return TrinityHostedActionStatus.REJECTED;
        }
        if (!host.hasRefundablePatternState()) {
            return host.isCraftingStructureFormed() ?
                    TrinityHostedActionStatus.NO_OP : TrinityHostedActionStatus.STALE_STATE;
        }
        return host.tryRefundAll(player) ?
                TrinityHostedActionStatus.COMPLETED : TrinityHostedActionStatus.DELIVERY_FAILED;
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

    private void onTerminalConfigChanged() {
        if (!this.loadingTerminalConfig) {
            this.saveChanges();
        }
    }

    @Nullable
    private TrinityDataCoreBlockEntity refundHost(Player player) {
        if (!isAccessHatchManagementAvailable(player)) {
            return null;
        }
        TrinityDataCoreBlockEntity host = boundHost(false);
        return host != null && host.isLeaseOwner(this) && host.isStorageAvailable() ? host : null;
    }

    @Nullable
    private TrinityDataCoreBlockEntity boundHost() {
        return boundHost(true);
    }

    @Nullable
    private TrinityDataCoreBlockEntity boundHost(boolean logUnavailable) {
        TrinityAccessHatchBindingState binding = this.compartmentBindingState;
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
        LOGGER.warn("Trinity access hatch at {} exposes empty storage: {}", this.worldPosition, reason);
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

    private final class HatchStorageProvider implements IStorageProvider {

        @Override
        public void mountInventories(IStorageMounts storageMounts) {
            TrinityDataCoreBlockEntity host = boundHost(false);
            if (host != null && host.isLeaseOwner(TrinityAccessHatchBlockEntity.this) &&
                    host.isStorageAvailable()) {
                storageMounts.mount(networkStorage, 0);
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
                    TrinityAccessHatchBlockEntity.this,
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
            TrinityDataCoreBlockEntity host = boundHost();
            if (!canUseStorage(host) || !(level instanceof ServerLevel serverLevel)) {
                return;
            }
            TrinityDataCoreStorageSavedData.get(serverLevel.getServer()).addAvailableStacks(host.getStorageId(), out);
        }

        @Override
        public Component getDescription() {
            return DEBlocks.TRINITY_ACCESS_HATCH.get().getName();
        }

        private boolean canUseStorage(@Nullable TrinityDataCoreBlockEntity host) {
            return host != null && isCandidateOnline() &&
                    host.isLeaseOwner(TrinityAccessHatchBlockEntity.this) &&
                    host.isStorageAvailable();
        }
    }
}
