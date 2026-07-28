package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.common.ServerLifecycleEventHandler;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.compartment.UnavailableCompartmentStorage;
import com.fish_dan_.data_energistics.common.crafting.trinity.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.CountedCraftingProvider;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCraftingRuntimeRegistry;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockContext;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockController;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternTerminalPartition;
import com.fish_dan_.data_energistics.menu.TrinityCraftingStatusSelection;
import com.fish_dan_.data_energistics.menu.TrinityCraftingStatusSelection.TargetState;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
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
import appeng.menu.ISubMenu;
import appeng.util.NullConfigManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AE network hatch that exposes the bound Trinity Data Core UUID storage instead of storing contents locally.
 */
public class TrinityAccessHatchBlockEntity extends AENetworkedBlockEntity implements CompartmentPart, ITerminalHost {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private final MEStorage networkStorage = new HatchStorage();
    private final IStorageProvider storageProvider = new HatchStorageProvider();
    private final ICraftingProvider craftingProvider = new HatchCraftingProvider();
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

    public TrinityAccessHatchBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.TRINITY_ACCESS_HATCH_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .addService(IStorageProvider.class, this.storageProvider)
                .addService(ICraftingProvider.class, this.craftingProvider)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setVisualRepresentation(ModBlocks.TRINITY_ACCESS_HATCH.get())
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
     * Crafting status does not expose configurable terminal settings for this host.
     */
    @Override
    public IConfigManager getConfigManager() {
        return NullConfigManager.INSTANCE;
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return ModBlocks.TRINITY_DATA_CORE.get().asItem().getDefaultInstance();
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
    public CompartmentStorage compartmentStorage() {
        return UnavailableCompartmentStorage.INSTANCE;
    }

    @Override
    public void compartment$bindToHost(String structureName, CompartmentHost host) {
        TrinityAccessHatchBindingState requestedBinding = createBindingState(structureName, host);
        TrinityAccessHatchBindingState currentBinding = this.compartmentBindingState;
        if (currentBinding != null && currentBinding.isReleasing() && !currentBinding.isReleaseInProgress()) {
            releaseBinding(currentBinding, "retrying a previously interrupted release before rebinding");
            currentBinding = this.compartmentBindingState;
        }

        if (currentBinding != null && currentBinding.isActive() && currentBinding.matches(host, structureName)) {
            ensureHostRegistration(currentBinding);
            return;
        }

        if (currentBinding != null && !currentBinding.isReleasing()) {
            releaseBinding(currentBinding, "replacing an existing Trinity access hatch binding");
            currentBinding = this.compartmentBindingState;
        }

        if (currentBinding != null && currentBinding.isActive()) {
            if (currentBinding.matches(host, structureName)) {
                ensureHostRegistration(currentBinding);
                return;
            }
            IllegalStateException exception = new IllegalStateException(
                    "Trinity access hatch binding changed during a rebind operation");
            LOGGER.error("Cannot replace Trinity access hatch binding at {} because another binding won the transition",
                    this.worldPosition, exception);
            throw exception;
        }

        registerHost(requestedBinding);
        this.compartmentBindingState = requestedBinding;
        this.lastUnavailableReason = null;
        requestLeaseReevaluation(host);
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
        if (currentBinding == null) {
            LOGGER.warn("Ignoring Trinity access hatch unbind at {} because no local binding exists; host={}, structure={}",
                    this.worldPosition, host, structureName);
            unregisterHost(structureName, host);
            return;
        }
        if (!currentBinding.matches(host, structureName)) {
            LOGGER.warn("Ignoring stale Trinity access hatch unbind at {} for host={}, structure={}; current host={}, structure={}",
                    this.worldPosition,
                    host,
                    structureName,
                    currentBinding.host(),
                    currentBinding.structureName());
            unregisterHost(structureName, host);
            return;
        }
        if (currentBinding.isReleasing() && currentBinding.isReleaseInProgress()) {
            return;
        }
        releaseBinding(currentBinding, "explicit Trinity access hatch unbind");
    }

    @Override
    public void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                     String structureName,
                                                     VerticalMultiBlockContext<?> context) {
        CompartmentPart.super.verticalMultiBlock$addedToController(controller, structureName, context);
    }

    @Override
    public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller, String structureName) {
        if (!(controller instanceof CompartmentHost host)) {
            LOGGER.warn("Ignoring Trinity access hatch removal from non-compartment controller at {}: {}",
                    this.worldPosition, controller);
            return;
        }
        compartment$unbindFromHost(structureName, host);
    }

    @Override
    public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller) {
        TrinityAccessHatchBindingState currentBinding = this.compartmentBindingState;
        if (currentBinding == null) {
            return;
        }
        if (currentBinding.host() != controller) {
            LOGGER.warn("Ignoring Trinity access hatch removal from stale controller at {}: currentHost={}, callbackHost={}",
                    this.worldPosition, currentBinding.host(), controller);
            return;
        }
        compartment$unbindFromHost(currentBinding.structureName(), currentBinding.host());
    }

    private TrinityAccessHatchBindingState createBindingState(String structureName, CompartmentHost host) {
        try {
            return TrinityAccessHatchBindingState.active(structureName, host);
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

    private void releaseBinding(TrinityAccessHatchBindingState expectedBinding, String reason) {
        if (this.compartmentBindingState != expectedBinding) {
            return;
        }
        TrinityAccessHatchBindingState releasingBinding = expectedBinding.isReleasing() ? expectedBinding : expectedBinding.releasing();
        this.compartmentBindingState = releasingBinding;
        if (releasingBinding.isReleaseInProgress()) {
            return;
        }
        releasingBinding.beginReleaseAttempt();
        try {
            unregisterHost(releasingBinding.structureName(), releasingBinding.host());
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to release Trinity access hatch binding at {} ({})", this.worldPosition, reason, exception);
            throw exception;
        } finally {
            releasingBinding.finishReleaseAttempt();
        }

        if (this.compartmentBindingState == releasingBinding) {
            this.compartmentBindingState = null;
            this.lastUnavailableReason = null;
            withdrawTrinityLeasePublications();
            requestLeaseReevaluation(releasingBinding.host());
            return;
        }

        TrinityAccessHatchBindingState replacementBinding = this.compartmentBindingState;
        if (replacementBinding != null && replacementBinding.isActive()) {
            ensureHostRegistration(replacementBinding);
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
        return target.currentState(host.getHostId(), boundCraftingRuntime(), accessGrid());
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
        return host != null && target.isRouteCurrent(host.getHostId(), boundCraftingRuntime(), accessGrid());
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

        boolean published = desired.registry().publish(desired.node(), desired.runtime());
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
        if (runtime == null) {
            return null;
        }

        IGridNode node = this.getMainNode().getNode();
        IGrid grid = node.getGrid();
        if (!(grid.getCraftingService() instanceof TrinityCraftingRuntimeRegistry registry)) {
            LOGGER.error("Cannot publish Trinity CPU at {} because the AE2 crafting service has no runtime registry",
                    this.worldPosition);
            return null;
        }
        return new CpuPublication(registry, grid, node, runtime);
    }

    @Nullable
    private CpuPublication withdrawCraftingCpuPublication() {
        CpuPublication publication = this.cpuPublication;
        if (publication == null) {
            return null;
        }
        this.cpuPublication = null;
        return publication.registry().withdraw(publication.node()) ? publication : null;
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
        if (grid.getCraftingService() instanceof TrinityCraftingRuntimeRegistry registry && registry.withdraw(node)) {
            grid.postEvent(new GridCraftingCpuChange(node));
            return true;
        }
        return false;
    }

    private static void notifyCraftingCpuChanged(CpuPublication publication) {
        publication.grid().postEvent(new GridCraftingCpuChange(publication.node()));
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
        AEItemKey icon = AEItemKey.of(ModBlocks.TRINITY_DATA_CORE.get());
        return new PatternContainerGroup(icon, ModBlocks.TRINITY_DATA_CORE.get().getName(), List.of());
    }

    private void detachTerminalPartitions() {
        for (TrinityPatternTerminalPartition partition : this.terminalPartitions) {
            partition.detach();
        }
        this.terminalPartitions = List.of();
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
                                  IGrid grid,
                                  IGridNode node,
                                  TrinityDataCoreCraftingRuntime runtime) {

        private boolean matches(CpuPublication other) {
            return this.registry == other.registry && this.grid == other.grid && this.node == other.node &&
                    this.runtime == other.runtime;
        }

        private boolean hasSameNotificationTarget(CpuPublication other) {
            return this.grid == other.grid && this.node == other.node;
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

    private final class HatchCraftingProvider implements CountedCraftingProvider {

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
            if (patternDetails == null) {
                throw new IllegalArgumentException("Trinity pattern details must not be null");
            }
            if (prototype == null) {
                throw new IllegalArgumentException("Trinity input prototype must not be null");
            }
            if (requestedCount <= 0L) {
                throw new IllegalArgumentException("requestedCount must be positive");
            }
            TrinityDataCoreBlockEntity host = patternProviderHost();
            if (host == null || level == null || level.isClientSide()) {
                return null;
            }
            return new HatchCraftingAdmission(
                    host,
                    patternDetails,
                    level.getGameTime(),
                    requestedCount,
                    prototype);
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
        private final IPatternDetails patternDetails;
        private final long queuedTick;
        private final long count;
        private final KeyCounter[] preparedPrototype;
        private boolean attempted;

        private HatchCraftingAdmission(TrinityDataCoreBlockEntity host,
                                       IPatternDetails patternDetails,
                                       long queuedTick,
                                       long count,
                                       KeyCounter[] preparedPrototype) {
            this.host = host;
            this.patternDetails = patternDetails;
            this.queuedTick = queuedTick;
            this.count = count;
            this.preparedPrototype = preparedPrototype;
        }

        @Override
        public long count() {
            return this.count;
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
            return this.host.getPatternCatalog().pushPattern(
                    this.patternDetails,
                    prototype,
                    this.queuedTick,
                    this.count);
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
            return ModBlocks.TRINITY_ACCESS_HATCH.get().getName();
        }

        private boolean canUseStorage(@Nullable TrinityDataCoreBlockEntity host) {
            return host != null && isCandidateOnline() &&
                    host.isLeaseOwner(TrinityAccessHatchBlockEntity.this) &&
                    host.isStorageAvailable();
        }
    }
}
