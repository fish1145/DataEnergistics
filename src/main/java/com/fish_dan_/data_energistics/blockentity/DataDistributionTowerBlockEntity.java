package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.ChannelHubHost;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.tower.TowerCoverage;
import com.fish_dan_.data_energistics.blockentity.tower.TowerCoverageImpl;
import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyDistributor;
import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyDistributorContext;
import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyDistributorImpl;
import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyEndpoint;
import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyEndpointResolver;
import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyEndpointResolverContext;
import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyEndpointResolverImpl;
import com.fish_dan_.data_energistics.blockentity.tower.TowerLinkGraph;
import com.fish_dan_.data_energistics.blockentity.tower.TowerLinkGraphImpl;
import com.fish_dan_.data_energistics.blockentity.tower.TowerTargetDisplayResolver;
import com.fish_dan_.data_energistics.blockentity.tower.TowerTargetDisplayResolverContext;
import com.fish_dan_.data_energistics.blockentity.tower.TowerTargetDisplayResolverImpl;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.appflux.AE2FluxIntegration;
import com.fish_dan_.data_energistics.integration.curios.CuriosDataDistributionConnectorAccess;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccessImpl;
import com.fish_dan_.data_energistics.integration.tower.AeCraftingDisplayBridge;
import com.fish_dan_.data_energistics.integration.tower.BrandonsCoreEnergyBridge;
import com.fish_dan_.data_energistics.integration.tower.NeoEcoAeTowerBridge;
import com.fish_dan_.data_energistics.integration.tower.OritechEnergyBridge;
import com.fish_dan_.data_energistics.item.DataDistributionConnectorItem;
import com.fish_dan_.data_energistics.item.DataDistributionConnectorSelector;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.util.MemoryCardSettingsHelper;
import com.fish_dan_.data_energistics.util.ServerTickDelayQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import appeng.api.AECapabilities;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.AEItemDefinitionFilter;
import lombok.Getter;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = Data_Energistics.MODID)
public class DataDistributionTowerBlockEntity extends AENetworkedBlockEntity implements ChannelHubHost,
                                              InternalInventoryHost, TowerEnergyEndpointResolverContext, TowerEnergyDistributorContext,
                                              TowerTargetDisplayResolverContext, IGridTickable {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final UnlimitedEnergyAccess UNLIMITED_ENERGY_ACCESS = new UnlimitedEnergyAccessImpl();
    /**
     * Selects the connector source captured when a player places a potentially compatible target.
     */
    private static final DataDistributionConnectorSelector CONNECTOR_SELECTOR = DataDistributionConnectorSelector.create();
    private static final String SHOW_RANGE_TAG = "show_range";
    private static final String LINKED_POSITIONS_TAG = "linked_positions";
    private static final String CONNECTION_MODE_TAG = "connection_mode";
    private static final String TARGET_TRANSFER_MODES_TAG = "target_transfer_modes";
    private static final String BUFFERED_TRANSFER_ENERGY_TAG = "buffered_transfer_energy";
    private static final String QUARANTINED_TRANSFER_ENERGY_TAG = "quarantined_transfer_energy";
    private static final int PERSISTED_LINK_RETRY_DELAY = 10;
    private static final int PERSISTED_LINK_REQUEUE_TICKS = 40;
    private static final String RANGE_ADJUSTMENT_MODE_TAG = "range_adjustment_mode";
    private static final int INITIAL_PENDING_DELAY = 2;
    private static final int INITIAL_DISCOVERY_STAGGER_TICKS = 10;
    private static final int AUTO_DISCOVERY_INTERVAL_TICKS = 20;
    private static final int RANGE_SCAN_CHUNKS_PER_TICK = 1;
    private static final int MAX_RANGE_SCAN_CHUNK_CREDIT = 64;
    private static final int MAX_RANGE_SCAN_CHUNKS_PER_AE_TICK = 8;
    private static final int AE_TICK_MIN_INTERVAL_TICKS = 1;
    private static final int AE_TICK_MAX_INTERVAL_TICKS = 20;
    private static final TickingRequest AE_TICKING_REQUEST = new TickingRequest(
            AE_TICK_MIN_INTERVAL_TICKS, AE_TICK_MAX_INTERVAL_TICKS, false);
    private static final int CLUSTER_CACHE_TICKS = 10;
    private static final int DIAGNOSTIC_LOG_INTERVAL_TICKS = 100;
    private static final int CACHE_CLEANUP_INTERVAL_TICKS = 6000;
    private static final int MAX_ENERGY_STORAGE_VIEWS = 256;
    private static final double BASE_IDLE_POWER_USAGE = 4.0;
    private static final double IDLE_POWER_USAGE_PER_ADDITIONAL_CHUNK = 8.0;
    private static final Map<ChunkKey, Set<BlockPos>> TOWER_CHUNK_POSITIONS = new HashMap<>();
    private static MinecraftServer boundServer;

    private final TowerCoverage coverage;
    private final TowerLinkGraph linkGraph = new TowerLinkGraphImpl();
    private final NeoEcoAeTowerBridge neoEcoAeBridge = new NeoEcoAeTowerBridge();
    private final TowerEnergyEndpointResolver energyEndpointResolver;
    private final TowerEnergyDistributor energyDistributor;
    private final TowerTargetDisplayResolver targetDisplayResolver;
    private final Map<BlockPos, TargetTransferMode> targetTransferModes = new HashMap<>();
    private final Map<BlockPos, TowerEnergyStorage> cachedEnergyStorageViews = new HashMap<>();
    private final AppEngInternalInventory wirelessBoosters = new AppEngInternalInventory(this, 1);
    private long bufferedTransferEnergy;
    private long quarantinedTransferEnergy;
    private long lastClusterCacheTick = Long.MIN_VALUE;
    private List<BlockPos> cachedEndpoints = List.of();
    private List<BlockPos> cachedAeDisplayTargets = List.of();
    private List<DataDistributionTowerBlockEntity> cachedTowerCluster = List.of();
    private boolean endpointCacheValid;
    private long targetDisplayStateRevision;
    private BlockPos cachedClusterCoordinatorPos;
    private long diagnosticWindowStartTick = Long.MIN_VALUE;
    private int diagnosticRealExtractCalls;
    private int diagnosticSimulatedExtractCalls;
    private int diagnosticReceiveCalls;
    private int diagnosticGetStoredCalls;
    private int diagnosticGetMaxStoredCalls;
    private int diagnosticCanExtractCalls;
    private int diagnosticCanReceiveCalls;
    private int diagnosticSimulatedCacheHits;
    private int diagnosticSimulatedCacheMisses;
    private long diagnosticRequestedRealExtract;
    private long diagnosticReturnedRealExtract;
    private long diagnosticRequestedSimulatedExtract;
    private long diagnosticReturnedSimulatedExtract;
    private long diagnosticRequestedReceive;
    private long diagnosticReturnedReceive;
    private int diagnosticMaxExtractEndpoints;
    private int diagnosticMaxReceiveEndpoints;
    private boolean showRange = false;
    private boolean syncedOnline = false;
    private boolean pendingRangeRefresh = false;
    private boolean pendingNetworkRecovery;
    private boolean mainNodeActive;
    private int cacheCleanupCooldown;
    @Getter
    private ConnectionMode connectionMode = ConnectionMode.AE_AND_FE;
    @Getter
    private RangeAdjustmentMode rangeAdjustmentMode = RangeAdjustmentMode.POINT;
    private int autoDiscoveryCooldown;
    private RangeScanCursor rangeScanCursor;
    private int rangeScanChunkCredit;
    private int indexedChunkRadius = -1;
    private int syncedChunkRadius = 0;
    private long recoveryEpoch;

    public DataDistributionTowerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.coverage = new TowerCoverageImpl(blockPos);
        BrandonsCoreEnergyBridge brandonsCoreEnergyBridge = new BrandonsCoreEnergyBridge();
        OritechEnergyBridge oritechEnergyBridge = new OritechEnergyBridge();
        AeCraftingDisplayBridge aeCraftingDisplayBridge = new AeCraftingDisplayBridge();
        this.energyEndpointResolver = new TowerEnergyEndpointResolverImpl(
                this, brandonsCoreEnergyBridge, oritechEnergyBridge, UNLIMITED_ENERGY_ACCESS);
        this.energyDistributor = new TowerEnergyDistributorImpl(
                this, this.energyEndpointResolver, brandonsCoreEnergyBridge, UNLIMITED_ENERGY_ACCESS);
        this.targetDisplayResolver = new TowerTargetDisplayResolverImpl(this, this.neoEcoAeBridge, aeCraftingDisplayBridge);
        this.wirelessBoosters.setFilter(new AEItemDefinitionFilter(AEItems.WIRELESS_BOOSTER));
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
                .setIdlePowerUsage(BASE_IDLE_POWER_USAGE)
                .addService(IGridTickable.class, this);
    }

    @Override
    public void onReady() {
        this.recoveryEpoch++;
        super.onReady();
        updateIdlePowerUsage();
        if (this.level != null && !this.level.isClientSide()) {
            registerInChunkIndex();
            invalidateEndpointCache();
            requeuePersistedLinks();
            schedulePersistedLinkRequeue();
            resetAutoDiscoveryCooldown();
            this.pendingNetworkRecovery = true;
            this.mainNodeActive = this.getMainNode().isActive();
            requestAeTickWake();
        }
    }

    @Override
    public void setRemoved() {
        this.recoveryEpoch++;
        this.pendingNetworkRecovery = false;
        if (this.level != null && !this.level.isClientSide()) {
            unregisterFromChunkIndex();
            destroyAllConnections();
            clearRuntimeCaches();
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        this.recoveryEpoch++;
        this.pendingNetworkRecovery = false;
        super.onChunkUnloaded();
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.showRange = data.getBoolean(SHOW_RANGE_TAG);
        this.connectionMode = ConnectionMode.fromSerializedName(data.getString(CONNECTION_MODE_TAG));
        this.rangeAdjustmentMode = RangeAdjustmentMode.fromSerializedName(data.getString(RANGE_ADJUSTMENT_MODE_TAG));
        this.bufferedTransferEnergy = readBufferedTransferEnergy(data);
        this.quarantinedTransferEnergy = readQuarantinedTransferEnergy(data);
        this.wirelessBoosters.readFromNBT(data, "wireless_boosters", registries);
        this.syncedChunkRadius = computeChunkRadius();
        updateIdlePowerUsage();
        this.linkGraph.clear();
        this.targetTransferModes.clear();

        Tag root = data.get(LINKED_POSITIONS_TAG);
        if (root instanceof ListTag list) {
            for (Tag tag : list) {
                if (tag instanceof CompoundTag compound) {
                    NbtUtils.readBlockPos(compound, "pos").ifPresent(this.linkGraph::addLinked);
                }
            }
        }

        Tag targetTransferModesTag = data.get(TARGET_TRANSFER_MODES_TAG);
        if (targetTransferModesTag instanceof ListTag list) {
            for (Tag tag : list) {
                if (tag instanceof CompoundTag compound) {
                    NbtUtils.readBlockPos(compound, "pos").ifPresent(pos -> {
                        TargetTransferMode mode = TargetTransferMode.fromSerializedName(compound.getString("mode"));
                        if (mode != TargetTransferMode.AUTO) {
                            this.targetTransferModes.put(pos.immutable(), mode);
                        }
                    });
                }
            }
        }

        clearRuntimeCaches();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putBoolean(SHOW_RANGE_TAG, this.showRange);
        data.putString(CONNECTION_MODE_TAG, this.connectionMode.getSerializedName());
        data.putString(RANGE_ADJUSTMENT_MODE_TAG, this.rangeAdjustmentMode.getSerializedName());
        data.putLong(BUFFERED_TRANSFER_ENERGY_TAG, this.bufferedTransferEnergy);
        data.putLong(QUARANTINED_TRANSFER_ENERGY_TAG, this.quarantinedTransferEnergy);
        this.wirelessBoosters.writeToNBT(data, "wireless_boosters", registries);

        data.put(LINKED_POSITIONS_TAG, createLinkedPositionsTag());

        ListTag targetTransferModes = new ListTag();
        for (Map.Entry<BlockPos, TargetTransferMode> entry : this.targetTransferModes.entrySet()) {
            if (entry.getValue() == TargetTransferMode.AUTO) {
                continue;
            }
            CompoundTag compound = new CompoundTag();
            compound.put("pos", NbtUtils.writeBlockPos(entry.getKey()));
            compound.putString("mode", entry.getValue().getSerializedName());
            targetTransferModes.add(compound);
        }
        data.put(TARGET_TRANSFER_MODES_TAG, targetTransferModes);
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            if (this.bufferedTransferEnergy > 0 || this.quarantinedTransferEnergy > 0) {
                CompoundTag settings = new CompoundTag();
                if (this.bufferedTransferEnergy > 0) {
                    settings.putLong(BUFFERED_TRANSFER_ENERGY_TAG, this.bufferedTransferEnergy);
                }
                if (this.quarantinedTransferEnergy > 0) {
                    settings.putLong(QUARANTINED_TRANSFER_ENERGY_TAG, this.quarantinedTransferEnergy);
                }
                builder.set(ModDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get(), settings);
            }
            return;
        }
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = new CompoundTag();
        settings.putBoolean(SHOW_RANGE_TAG, this.showRange);
        settings.putString(CONNECTION_MODE_TAG, this.connectionMode.getSerializedName());
        settings.putString(RANGE_ADJUSTMENT_MODE_TAG, this.rangeAdjustmentMode.getSerializedName());
        settings.put(TARGET_TRANSFER_MODES_TAG, createTargetTransferModesTag());
        builder.set(ModDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get(), settings);
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        CompoundTag settings = input.get(ModDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get());
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            if (settings != null) {
                setBufferedTransferEnergy(readBufferedTransferEnergy(settings));
                setQuarantinedTransferEnergy(readQuarantinedTransferEnergy(settings));
            }
            return;
        }
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        if (settings != null) {
            applyMemoryCardSettings(settings);
        }
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.showRange);
        data.writeBoolean(isTowerActive());
        data.writeVarInt(computeChunkRadius());
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean showRange = data.readBoolean();
        if (showRange != this.showRange) {
            this.showRange = showRange;
            changed = true;
        }
        boolean syncedOnline = data.readBoolean();
        if (syncedOnline != this.syncedOnline) {
            this.syncedOnline = syncedOnline;
            changed = true;
        }
        int syncedChunkRadius = data.readVarInt();
        if (syncedChunkRadius != this.syncedChunkRadius) {
            this.syncedChunkRadius = syncedChunkRadius;
            changed = true;
        }
        return changed;
    }

    public void lifecycleTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        emitDiagnosticLogIfNeeded();

        if (--this.cacheCleanupCooldown <= 0) {
            this.cacheCleanupCooldown = CACHE_CLEANUP_INTERVAL_TICKS;
            trimCaches();
        }

        if (this.pendingNetworkRecovery) {
            requestAeTickWake();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return AE_TICKING_REQUEST;
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (node != this.getMainNode().getNode() || !node.isActive() || !this.getMainNode().hasGridBooted()) {
            return TickRateModulation.SLEEP;
        }

        int elapsedTicks = Math.max(1, ticksSinceLastCall);
        boolean completedNetworkWork = false;
        syncClientOnlineState();

        if (this.pendingNetworkRecovery) {
            this.pendingNetworkRecovery = false;
            completedNetworkWork |= enqueuePersistedLinkReconciliation();
        }
        if (this.pendingRangeRefresh) {
            applyPendingRangeRefresh();
            completedNetworkWork = true;
        }

        completedNetworkWork |= processAutoDiscovery(elapsedTicks);
        completedNetworkWork |= processRangeScanBatch(elapsedTicks);

        if (node.getUsedChannels() < getMaxLinkChannels()) {
            completedNetworkWork |= processPendingLinks(node, elapsedTicks);
        }

        if (isClusterCoordinator()) {
            completedNetworkWork |= performActiveRangeTransfer();
        } else {
            completedNetworkWork |= this.energyDistributor.flushBufferedEnergy();
        }

        return completedNetworkWork ? TickRateModulation.URGENT : hasDeferredAeNetworkWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    public IEnergyStorage getEnergyStorageForQuery(BlockPos accessPos, @Nullable Direction side) {
        BlockPos excludedPos = side == null ? null : accessPos.relative(side);
        BlockPos normalizedExcludedPos = normalizeExcludedPos(excludedPos);
        if (normalizedExcludedPos == null) {
            return this.cachedEnergyStorageViews.computeIfAbsent(null, ignored -> new TowerEnergyStorage(null));
        }
        return this.cachedEnergyStorageViews.computeIfAbsent(
                normalizedExcludedPos,
                TowerEnergyStorage::new);
    }

    public boolean toggleRangeDisplay() {
        this.showRange = !this.showRange;
        this.setChanged();
        this.markForClientUpdate();
        return this.showRange;
    }

    public boolean isRangeDisplayEnabled() {
        return this.showRange;
    }

    public boolean isPointToPointMode() {
        return this.rangeAdjustmentMode == RangeAdjustmentMode.POINT;
    }

    public TargetTransferMode getTargetTransferMode(BlockPos targetPos) {
        return this.targetTransferModes.getOrDefault(normalizeTargetPos(targetPos), TargetTransferMode.AUTO);
    }

    public void setTargetTransferMode(BlockPos targetPos, @Nullable TargetTransferMode mode) {
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        TargetTransferMode normalizedMode = mode == null ? TargetTransferMode.AUTO : mode;
        if (normalizedMode == TargetTransferMode.AUTO) {
            this.targetTransferModes.remove(normalizedPos);
        } else {
            this.targetTransferModes.put(normalizedPos, normalizedMode);
        }

        if (normalizedMode == TargetTransferMode.DISABLED) {
            destroyTargetConnections(normalizedPos);
            this.linkGraph.removePending(normalizedPos);
        } else if (canMaintainGridLinkTo(normalizedPos)) {
            queueLink(normalizedPos, 0);
        }

        invalidateEndpointCache();
        invalidateClusterCache();
        this.setChanged();
        this.markForClientUpdate();
    }

    public void setConnectionMode(@Nullable ConnectionMode connectionMode) {
        ConnectionMode normalizedMode = connectionMode == null ? ConnectionMode.AE_AND_FE : connectionMode;
        if (this.connectionMode == normalizedMode) {
            return;
        }

        this.connectionMode = normalizedMode;
        refreshConnectionTargets();
        this.setChanged();
        this.markForClientUpdate();
    }

    public void setRangeAdjustmentMode(@Nullable RangeAdjustmentMode rangeAdjustmentMode) {
        RangeAdjustmentMode normalizedMode = rangeAdjustmentMode == null ? RangeAdjustmentMode.POINT : rangeAdjustmentMode;
        if (this.rangeAdjustmentMode == normalizedMode) {
            return;
        }

        this.rangeAdjustmentMode = normalizedMode;
        if (this.level != null && !this.level.isClientSide() && this.rangeAdjustmentMode == RangeAdjustmentMode.SCOPE) {
            requestNearbyConnectableNodeScan();
        }
        invalidateEndpointCache();
        invalidateClusterCache();
        this.setChanged();
        this.markForClientUpdate();
    }

    public ConnectorBindResult bindTargetFromConnector(BlockPos targetPos) {
        if (this.level == null || this.level.isClientSide()) {
            return ConnectorBindResult.fail(ConnectorBindFailure.UNSUPPORTED);
        }
        if (!isPointToPointMode()) {
            return ConnectorBindResult.fail(ConnectorBindFailure.NOT_POINT_MODE);
        }

        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        if (this.worldPosition.equals(normalizedPos) || isTowerBlock(normalizedPos)) {
            return ConnectorBindResult.fail(ConnectorBindFailure.SELF_TARGET);
        }
        if (!isWithinTowerCoverage(normalizedPos)) {
            return ConnectorBindResult.fail(ConnectorBindFailure.OUT_OF_RANGE);
        }

        boolean aeSupported = hasExposedAeNode(normalizedPos);
        boolean feSupported = canReceiveEnergy(findReceiveEnergyStorage(normalizedPos));
        if (!aeSupported && !feSupported) {
            return ConnectorBindResult.fail(ConnectorBindFailure.UNSUPPORTED);
        }

        ConnectionMode desiredMode = this.connectionMode;
        if (aeSupported && !desiredMode.allowsAeTargets()) {
            desiredMode = desiredMode.allowsFeTargets() ? ConnectionMode.AE_AND_FE : ConnectionMode.AE_ONLY;
        }
        if (feSupported && !desiredMode.allowsFeTargets()) {
            desiredMode = desiredMode.allowsAeTargets() ? ConnectionMode.AE_AND_FE : ConnectionMode.FE_ONLY;
        }
        if (desiredMode != this.connectionMode) {
            setConnectionMode(desiredMode);
        }

        this.linkGraph.addLinked(normalizedPos);
        if (aeSupported && canMaintainGridLinkTo(normalizedPos)) {
            queueLink(normalizedPos, 0);
        } else {
            this.linkGraph.removePending(normalizedPos);
            destroyTargetConnections(normalizedPos);
        }

        invalidateEndpointCache();
        invalidateClusterCache();
        this.setChanged();
        this.markForClientUpdate();
        return ConnectorBindResult.success(aeSupported, feSupported);
    }

    private void applyMemoryCardSettings(CompoundTag settings) {
        boolean changed = false;
        boolean refreshTargets = false;
        boolean invalidateTargets = false;

        if (settings.contains(SHOW_RANGE_TAG)) {
            boolean showRange = MemoryCardSettingsHelper.readBoolean(settings, SHOW_RANGE_TAG, this.showRange);
            if (this.showRange != showRange) {
                this.showRange = showRange;
                changed = true;
            }
        }

        if (settings.contains(CONNECTION_MODE_TAG)) {
            ConnectionMode connectionMode = ConnectionMode.fromSerializedName(settings.getString(CONNECTION_MODE_TAG));
            if (this.connectionMode != connectionMode) {
                this.connectionMode = connectionMode;
                changed = true;
                refreshTargets = true;
            }
        }

        if (settings.contains(RANGE_ADJUSTMENT_MODE_TAG)) {
            RangeAdjustmentMode rangeAdjustmentMode = RangeAdjustmentMode.fromSerializedName(settings.getString(RANGE_ADJUSTMENT_MODE_TAG));
            if (this.rangeAdjustmentMode != rangeAdjustmentMode) {
                this.rangeAdjustmentMode = rangeAdjustmentMode;
                changed = true;
                invalidateTargets = true;
                if (this.level != null && !this.level.isClientSide() && this.rangeAdjustmentMode == RangeAdjustmentMode.SCOPE) {
                    requestNearbyConnectableNodeScan();
                }
            }
        }

        if (settings.contains(TARGET_TRANSFER_MODES_TAG)) {
            Map<BlockPos, TargetTransferMode> targetTransferModes = readTargetTransferModes(settings);
            if (!this.targetTransferModes.equals(targetTransferModes)) {
                this.targetTransferModes.clear();
                this.targetTransferModes.putAll(targetTransferModes);
                changed = true;
                refreshTargets = true;
            }
        }

        if (refreshTargets) {
            refreshConnectionTargets();
            invalidateEndpointCache();
            invalidateClusterCache();
        } else if (invalidateTargets) {
            invalidateEndpointCache();
            invalidateClusterCache();
        }

        if (changed) {
            this.setChanged();
            this.markForClientUpdate();
        }
    }

    private ListTag createTargetTransferModesTag() {
        ListTag targetTransferModes = new ListTag();
        for (Map.Entry<BlockPos, TargetTransferMode> entry : this.targetTransferModes.entrySet()) {
            if (entry.getValue() == TargetTransferMode.AUTO) {
                continue;
            }
            CompoundTag compound = new CompoundTag();
            compound.put("pos", NbtUtils.writeBlockPos(entry.getKey()));
            compound.putString("mode", entry.getValue().getSerializedName());
            targetTransferModes.add(compound);
        }
        return targetTransferModes;
    }

    private ListTag createLinkedPositionsTag() {
        ListTag linked = new ListTag();
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>(this.linkGraph.linkedPositions());
        positions.addAll(this.linkGraph.pendingPositions());
        for (BlockPos pos : positions) {
            // LinkGraph positions are canonicalized before insertion. Saving must never resolve them through the world,
            // because loading an unloading target chunk here prevents server shutdown from converging.
            if (this.targetTransferModes.getOrDefault(pos, TargetTransferMode.AUTO) == TargetTransferMode.DISABLED) {
                continue;
            }

            CompoundTag entry = new CompoundTag();
            entry.put("pos", NbtUtils.writeBlockPos(pos));
            linked.add(entry);
        }
        return linked;
    }

    private static Map<BlockPos, TargetTransferMode> readTargetTransferModes(CompoundTag settings) {
        Map<BlockPos, TargetTransferMode> targetTransferModes = new HashMap<>();
        Tag targetTransferModesTag = settings.get(TARGET_TRANSFER_MODES_TAG);
        if (!(targetTransferModesTag instanceof ListTag list)) {
            return targetTransferModes;
        }

        for (Tag tag : list) {
            if (tag instanceof CompoundTag compound) {
                NbtUtils.readBlockPos(compound, "pos").ifPresent(pos -> {
                    TargetTransferMode transferMode = TargetTransferMode.fromSerializedName(compound.getString("mode"));
                    if (transferMode != TargetTransferMode.AUTO) {
                        targetTransferModes.put(pos.immutable(), transferMode);
                    }
                });
            }
        }
        return targetTransferModes;
    }

    public int getConfiguredChunkRadius() {
        return getChunkRadius();
    }

    public AABB getCoverageAabb() {
        int chunkRadius = getChunkRadius();
        return this.coverage.aabb(this.level, chunkRadius);
    }

    public String getEnergyDisplayText() {
        return formatFeAmount(getAvailableFeForUi());
    }

    public int getUsedChannelCount() {
        IGridNode node = this.getMainNode().getNode();
        return node == null ? 0 : node.getUsedChannels();
    }

    public int getMaxChannelCount() {
        return getMaxLinkChannels();
    }

    public long getAvailableFeForUi() {
        return getTotalExtractableEnergy(null);
    }

    public long getEnergyCapacityForUi() {
        return getTotalEnergyCapacity(null);
    }

    public TargetTransferInfo getTargetTransferInfo(BlockPos targetPos) {
        if (this.level == null) {
            return TargetTransferInfo.EMPTY;
        }

        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        int channelConnections = this.linkGraph.connectionCount(normalizedPos);
        long stored = 0L;
        long capacity = 0L;
        boolean canExtract = false;
        boolean canReceive = false;
        boolean hasEnergy = false;

        for (var direction : Direction.values()) {
            IEnergyStorage storage = getEnergyStorageAt(normalizedPos, direction);
            if (storage == null) {
                continue;
            }
            hasEnergy = true;
            stored = saturatingAdd(stored, storage.getEnergyStored());
            capacity = saturatingAdd(capacity, storage.getMaxEnergyStored());
            canExtract |= storage.canExtract();
            canReceive |= canReceiveEnergy(storage);
        }

        IEnergyStorage internal = getEnergyStorageAt(normalizedPos, null);
        if (internal != null) {
            hasEnergy = true;
            stored = saturatingAdd(stored, internal.getEnergyStored());
            capacity = saturatingAdd(capacity, internal.getMaxEnergyStored());
            canExtract |= internal.canExtract();
            canReceive |= canReceiveEnergy(internal);
        }

        boolean hasAeTarget = hasExposedAeNode(normalizedPos);
        return new TargetTransferInfo(channelConnections, hasAeTarget, hasEnergy, stored, capacity, canExtract, canReceive);
    }

    public boolean isNetworkNodeOnline() {
        if (this.level != null && this.level.isClientSide()) {
            return this.syncedOnline;
        }
        return isTowerActive();
    }

    public AppEngInternalInventory getInternalInventory() {
        return this.wirelessBoosters;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        updateIdlePowerUsage();
        invalidateEndpointCache();
        setChanged();
        markForClientUpdate();
        this.pendingRangeRefresh = true;
        requestAeTickWake();
    }

    public int getBoundTargetCount() {
        return this.targetDisplayResolver.boundTargetCount();
    }

    public List<BoundTargetSummary> getBoundTargetSummaries(int maxEntries) {
        return this.targetDisplayResolver.boundTargetSummaries(maxEntries);
    }

    public long getTargetDisplayStateRevision() {
        return this.targetDisplayStateRevision;
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof ServerLevelAccessor serverLevelAccessor) {
            Level level = serverLevelAccessor.getLevel();
            invalidateNearbyCaches(level, event.getPos());
            onPotentialNodeAdded(level, event.getPos());
            autoConnectPlacedBlockWithConnector(level, event);
        }
    }

    /**
     * Captures the selected connector at placement time so next-tick validation cannot observe a later inventory swap.
     */
    private static void autoConnectPlacedBlockWithConnector(Level level, BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getPlacedBlock().isAir()) {
            return;
        }

        Optional<ItemStack> selectedConnector = CONNECTOR_SELECTOR.select(
                player.getOffhandItem(),
                () -> findEquippedConnector(player));
        if (selectedConnector.isPresent()) {
            ItemStack connectorStack = selectedConnector.get().copy();
            DataDistributionConnectorItem connectorItem = (DataDistributionConnectorItem) connectorStack.getItem();
            MinecraftServer server = level.getServer();
            BlockPos placedPos = event.getPos().immutable();
            BlockState placedState = event.getPlacedBlock();
            ServerTickDelayQueue.runNextServerTick(server, () -> {
                if (!level.isLoaded(placedPos) || !level.getBlockState(placedPos).equals(placedState)) {
                    return;
                }

                connectorItem.autoConnectPlacedBlock(connectorStack, player, level, placedPos);
            });
        }
    }

    /**
     * Reads the dedicated optional equipment slot only after Curios presence has been confirmed.
     *
     * @param player player who placed the candidate target
     * @return the original equipped connector stack, or an empty optional when Curios is absent or the slot is empty
     */
    private static Optional<ItemStack> findEquippedConnector(Player player) {
        if (!ModFlags.isCuriosLoaded()) {
            return Optional.empty();
        }
        return CuriosDataDistributionConnectorAccess.find(player);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof ServerLevelAccessor serverLevelAccessor) {
            Level level = serverLevelAccessor.getLevel();
            invalidateNearbyCaches(level, event.getPos());
            onPotentialNodeRemoved(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ensureBound(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TOWER_CHUNK_POSITIONS.clear();
        boundServer = null;
    }

    public static void onPotentialNodeAdded(@NotNull Level level, @NotNull BlockPos targetPos) {
        IInWorldGridNodeHost targetNodeHost = GridHelper.getNodeHost(level, targetPos);
        if (targetNodeHost == null) {
            return;
        }

        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, new ChunkPos(targetPos)));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            BlockEntity blockEntity = level.getBlockEntity(towerPos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
                if (!tower.allowsAutomaticRangeConnections() && !tower.shouldReconnectTrackedTarget(targetPos)) {
                    continue;
                }
                if (!tower.canMaintainGridLinkTo(targetPos)) {
                    continue;
                }

                if (tower.queueLink(targetPos, INITIAL_PENDING_DELAY)) {
                    tower.setChanged();
                }
            }
        }
    }

    public static void onPotentialNodeRemoved(@NotNull Level level, @NotNull BlockPos targetPos) {
        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, new ChunkPos(targetPos)));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            BlockEntity blockEntity = level.getBlockEntity(towerPos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
                if (!tower.isWithinTowerCoverage(targetPos)) {
                    continue;
                }

                tower.removeTarget(targetPos);
            }
        }
    }

    private void requestNearbyConnectableNodeScan() {
        if (this.level == null || !allowsAutomaticRangeConnections()) {
            return;
        }

        int chunkRadius = getChunkRadius();
        int minChunkX = this.coverage.minChunkX(chunkRadius);
        int maxChunkX = this.coverage.maxChunkX(chunkRadius);
        int minChunkZ = this.coverage.minChunkZ(chunkRadius);
        int maxChunkZ = this.coverage.maxChunkZ(chunkRadius);
        this.rangeScanCursor = new RangeScanCursor(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
        this.rangeScanChunkCredit = 0;
        requestAeTickWake();
    }

    private boolean requestNearbyConnectableNodeScanIfIdle() {
        if (this.level == null || !allowsAutomaticRangeConnections()) {
            return false;
        }

        int chunkRadius = getChunkRadius();
        int minChunkX = this.coverage.minChunkX(chunkRadius);
        int maxChunkX = this.coverage.maxChunkX(chunkRadius);
        int minChunkZ = this.coverage.minChunkZ(chunkRadius);
        int maxChunkZ = this.coverage.maxChunkZ(chunkRadius);
        if (this.rangeScanCursor != null && this.rangeScanCursor.matches(minChunkX, maxChunkX, minChunkZ, maxChunkZ)) {
            return false;
        }

        this.rangeScanCursor = new RangeScanCursor(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
        this.rangeScanChunkCredit = 0;
        requestAeTickWake();
        return true;
    }

    private boolean processRangeScanBatch(int elapsedTicks) {
        if (this.level == null || this.level.isClientSide() || this.rangeScanCursor == null) {
            return false;
        }
        if (!allowsAutomaticRangeConnections()) {
            this.rangeScanCursor = null;
            this.rangeScanChunkCredit = 0;
            return false;
        }

        int addedCredit = Math.min(
                MAX_RANGE_SCAN_CHUNK_CREDIT - this.rangeScanChunkCredit,
                elapsedTicks * RANGE_SCAN_CHUNKS_PER_TICK);
        this.rangeScanChunkCredit += addedCredit;
        int processedChunks = 0;
        int added = 0;
        int scanLimit = Math.min(this.rangeScanChunkCredit, MAX_RANGE_SCAN_CHUNKS_PER_AE_TICK);
        while (this.rangeScanCursor != null && processedChunks < scanLimit) {
            if (this.rangeScanCursor.isComplete()) {
                this.rangeScanCursor = null;
                break;
            }

            added += scanRangeChunk(this.rangeScanCursor.chunkX(), this.rangeScanCursor.chunkZ());
            this.rangeScanCursor.advance();
            processedChunks++;
            if (this.rangeScanCursor.isComplete()) {
                this.rangeScanCursor = null;
            }
        }
        this.rangeScanChunkCredit -= processedChunks;
        if (this.rangeScanCursor == null) {
            this.rangeScanChunkCredit = 0;
        }

        if (added > 0) {
            this.setChanged();
        }
        return processedChunks > 0;
    }

    private int scanRangeChunk(int chunkX, int chunkZ) {
        if (this.level == null) {
            return 0;
        }

        LevelChunk chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            return 0;
        }

        int added = 0;
        ArrayList<BlockEntity> blockEntities = new ArrayList<>(chunk.getBlockEntities().values());
        blockEntities.sort(this::compareLinkTargetPriority);
        for (BlockEntity blockEntity : blockEntities) {
            BlockPos pos = normalizeTargetPos(blockEntity.getBlockPos());
            if (pos.equals(this.worldPosition) || !isWithinTowerCoverage(pos)) {
                continue;
            }
            if (queueLink(pos, 0)) {
                added++;
            }
        }
        return added;
    }

    private boolean processPendingLinks(IGridNode selfNode, int elapsedTicks) {
        if (this.level == null) {
            return false;
        }

        ArrayList<BlockPos> readyTargets = new ArrayList<>();
        for (Map.Entry<BlockPos, Integer> entry : this.linkGraph.pendingEntries()) {
            if (entry.getValue() > 0) {
                entry.setValue(Math.max(0, entry.getValue() - elapsedTicks));
            }
            if (entry.getValue() > 0) {
                continue;
            }

            BlockPos targetPos = entry.getKey();
            if (this.level.isLoaded(targetPos)) {
                readyTargets.add(targetPos);
            }
        }

        if (readyTargets.isEmpty()) {
            return false;
        }

        readyTargets.sort(this::compareLinkTargetPriority);
        int remainingChannels = Math.max(0, getMaxLinkChannels() - selfNode.getUsedChannels());
        if (remainingChannels <= 0) {
            return false;
        }

        boolean processed = false;
        for (BlockPos targetPos : readyTargets) {
            List<IGridNode> linkableNodes = getLinkableTargetNodes(selfNode, targetPos);
            if (linkableNodes.isEmpty()) {
                processed = true;
                reconnectTarget(selfNode, targetPos, List.of());
                if (this.linkGraph.containsLinked(targetPos.immutable()) && needsAeChannelLink(targetPos)) {
                    this.linkGraph.putPending(targetPos.immutable(), PERSISTED_LINK_RETRY_DELAY);
                } else {
                    this.linkGraph.removePending(targetPos);
                    incrementTargetDisplayStateRevision();
                }
                continue;
            }
            int missingConnections = 0;
            for (IGridNode targetNode : linkableNodes) {
                if (!this.linkGraph.hasConnection(targetPos, targetNode)) {
                    missingConnections++;
                }
            }
            if (missingConnections > remainingChannels) {
                continue;
            }

            processed = true;
            int connectedNodes = reconnectTarget(selfNode, targetPos, linkableNodes);
            if (hasAllConnections(targetPos, linkableNodes)) {
                this.linkGraph.removePending(targetPos);
            } else {
                this.linkGraph.putPending(targetPos.immutable(), PERSISTED_LINK_RETRY_DELAY);
            }
            remainingChannels -= connectedNodes;
            if (remainingChannels <= 0) {
                break;
            }
        }
        return processed;
    }

    @Override
    public boolean isTowerActive() {
        return this.getMainNode().isActive();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        boolean active = this.getMainNode().isActive();
        if (reason == IGridNodeListener.State.GRID_BOOT || active && !this.mainNodeActive) {
            this.pendingNetworkRecovery = true;
        }
        this.mainNodeActive = active;
        invalidateEndpointCache();
        invalidateClusterCache();
        syncClientOnlineState();
        requestAeTickWake();
    }

    private void syncClientOnlineState() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        boolean online = isVisualTowerActive();
        updateTowerActiveState(online);
        if (online != this.syncedOnline) {
            this.syncedOnline = online;
            this.markForClientUpdate();
        }
    }

    private boolean isVisualTowerActive() {
        if (isTowerActive()) {
            return true;
        }

        if (ModFlags.isAppFluxEnergySupportLoaded() && AE2FluxIntegration.extractEnergyFromOwnNetwork(this, 1, true) > 0) {
            return true;
        }

        for (BlockPos pos : getCachedEndpoints()) {
            if (hasStoredEnergy(pos)) {
                return true;
            }
        }

        return false;
    }

    private void updateTowerActiveState(boolean active) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        for (int part = 0; part <= 2; part++) {
            BlockPos partPos = this.worldPosition.above(part);
            BlockState state = this.level.getBlockState(partPos);
            if (!state.is(ModBlocks.DATA_DISTRIBUTION_TOWER.get()) || !state.hasProperty(DataDistributionTowerBlock.ACTIVE) || state.getValue(DataDistributionTowerBlock.ACTIVE) == active) {
                continue;
            }

            this.level.setBlock(partPos, state.setValue(DataDistributionTowerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private int getChunkRadius() {
        if (this.level != null && this.level.isClientSide()) {
            return this.syncedChunkRadius;
        }
        return computeChunkRadius();
    }

    private int computeChunkRadius() {
        ItemStack boosterStack = this.wirelessBoosters.getStackInSlot(0);
        int boosterCount = boosterStack.isEmpty() ? 0 : boosterStack.getCount();
        return this.coverage.computeChunkRadius(boosterCount);
    }

    private int getCoveredChunkCount() {
        return this.coverage.coveredChunkCount(computeChunkRadius());
    }

    private double computeIdlePowerUsage() {
        return BASE_IDLE_POWER_USAGE + Math.max(0, getCoveredChunkCount() - 1) * IDLE_POWER_USAGE_PER_ADDITIONAL_CHUNK;
    }

    private void updateIdlePowerUsage() {
        this.getMainNode().setIdlePowerUsage(computeIdlePowerUsage());
    }

    @Override
    public boolean isWithinTowerCoverage(BlockPos targetPos) {
        return this.coverage.contains(targetPos, getChunkRadius());
    }

    @Override
    public AENetworkedBlockEntity aeNetworkHost() {
        return this;
    }

    @Override
    public long bufferedTransferEnergy() {
        return this.bufferedTransferEnergy;
    }

    @Override
    public void setBufferedTransferEnergy(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Buffered tower transfer energy must be non-negative: " + amount);
        }
        if (amount == this.bufferedTransferEnergy) {
            return;
        }

        this.bufferedTransferEnergy = amount;
        this.setChanged();
        requestAeTickWake();
    }

    @Override
    public long quarantinedTransferEnergy() {
        return this.quarantinedTransferEnergy;
    }

    @Override
    public void setQuarantinedTransferEnergy(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Quarantined tower transfer energy must be non-negative: " + amount);
        }
        if (amount == this.quarantinedTransferEnergy) {
            return;
        }

        this.quarantinedTransferEnergy = amount;
        this.setChanged();
    }

    @Override
    public void markEndpointChanged(BlockPos pos) {
        if (this.level == null) {
            return;
        }

        BlockEntity blockEntity = this.level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }

    @Override
    public void recordMaxExtractEndpoints(int endpointCount) {
        this.diagnosticMaxExtractEndpoints = Math.max(this.diagnosticMaxExtractEndpoints, endpointCount);
    }

    @Override
    public void recordMaxReceiveEndpoints(int endpointCount) {
        this.diagnosticMaxReceiveEndpoints = Math.max(this.diagnosticMaxReceiveEndpoints, endpointCount);
    }

    @Override
    public void recordSimulatedCacheHit() {
        this.diagnosticSimulatedCacheHits++;
    }

    @Override
    public void recordSimulatedCacheMiss() {
        this.diagnosticSimulatedCacheMisses++;
    }

    private int getMaxLinkChannels() {
        IGridNode node = this.getMainNode().getNode();
        if (node != null) {
            return node.getMaxChannels();
        }

        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            return 32;
        }
        IPathingService pathingService = grid.getPathingService();
        ChannelMode mode = pathingService.getChannelMode();
        return mode == ChannelMode.INFINITE ? Integer.MAX_VALUE : 32 * mode.getCableCapacityFactor();
    }

    private void registerInChunkIndex() {
        ensureBound(this.level == null ? null : this.level.getServer());
        if (this.level == null) {
            return;
        }

        int chunkRadius = getChunkRadius();
        this.indexedChunkRadius = chunkRadius;
        for (int chunkX = this.coverage.minChunkX(chunkRadius); chunkX <= this.coverage.maxChunkX(chunkRadius); chunkX++) {
            for (int chunkZ = this.coverage.minChunkZ(chunkRadius); chunkZ <= this.coverage.maxChunkZ(chunkRadius); chunkZ++) {
                ChunkKey key = new ChunkKey(this.level, chunkX, chunkZ);
                TOWER_CHUNK_POSITIONS.computeIfAbsent(key, ignored -> new HashSet<>()).add(this.worldPosition.immutable());
            }
        }
    }

    private void unregisterFromChunkIndex() {
        if (this.level == null) {
            return;
        }

        int chunkRadius = this.indexedChunkRadius >= 0 ? this.indexedChunkRadius : getChunkRadius();
        for (int chunkX = this.coverage.minChunkX(chunkRadius); chunkX <= this.coverage.maxChunkX(chunkRadius); chunkX++) {
            for (int chunkZ = this.coverage.minChunkZ(chunkRadius); chunkZ <= this.coverage.maxChunkZ(chunkRadius); chunkZ++) {
                ChunkKey key = new ChunkKey(this.level, chunkX, chunkZ);
                Set<BlockPos> positions = TOWER_CHUNK_POSITIONS.get(key);
                if (positions != null) {
                    positions.remove(this.worldPosition);
                    if (positions.isEmpty()) {
                        TOWER_CHUNK_POSITIONS.remove(key);
                    }
                }
            }
        }
        this.indexedChunkRadius = -1;
    }

    private void invalidateEndpointCache() {
        this.cachedEndpoints = List.of();
        this.cachedAeDisplayTargets = List.of();
        this.endpointCacheValid = false;
        incrementTargetDisplayStateRevision();
        invalidateResolvedEnergyEndpointCache();
    }

    private void incrementTargetDisplayStateRevision() {
        this.targetDisplayStateRevision++;
    }

    private void invalidateClusterCache() {
        this.lastClusterCacheTick = Long.MIN_VALUE;
        this.cachedTowerCluster = List.of();
        this.cachedClusterCoordinatorPos = null;
        invalidateResolvedEnergyEndpointCache();
    }

    private void invalidateResolvedEnergyEndpointCache() {
        this.energyEndpointResolver.invalidateResolvedCache();
        this.energyDistributor.invalidateResolvedEndpointCache();
    }

    private void clearRuntimeCaches() {
        invalidateEndpointCache();
        invalidateClusterCache();
        this.cachedEnergyStorageViews.clear();
        this.energyEndpointResolver.clearReusableCache();
        trimCaches();
    }

    private void trimCaches() {
        if (this.cachedEnergyStorageViews.size() > MAX_ENERGY_STORAGE_VIEWS) {
            this.cachedEnergyStorageViews.clear();
        }
        this.energyDistributor.trimCaches();
    }

    private List<BlockPos> getCachedEndpoints() {
        if (this.level == null) {
            return List.of();
        }

        if (!this.endpointCacheValid) {
            refreshEndpointCache();
        }
        return this.cachedEndpoints;
    }

    private List<BlockPos> getCachedAeDisplayTargets() {
        if (this.level == null) {
            return List.of();
        }

        if (!this.endpointCacheValid) {
            refreshEndpointCache();
        }
        return this.cachedAeDisplayTargets;
    }

    private void refreshEndpointCache() {
        if (this.level == null) {
            this.cachedEndpoints = List.of();
            this.cachedAeDisplayTargets = List.of();
            return;
        }

        LinkedHashSet<BlockPos> endpoints = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> aeDisplayTargets = new LinkedHashSet<>();
        if (allowsAutomaticRangeConnections()) {
            for (BlockEntity blockEntity : getNearbyBlockEntities()) {
                BlockPos pos = normalizeTargetPos(blockEntity.getBlockPos());
                if (isTowerBlock(pos)) {
                    continue;
                }
                if (allowsFeTargets() && targetAllowsFe(pos) && hasAnyEnergyCapability(pos)) {
                    endpoints.add(pos);
                }
                BlockEntity targetBlockEntity = this.level.getBlockEntity(pos);
                if (allowsAeTargets() && targetAllowsAe(pos) && this.targetDisplayResolver.hasDisplayableAeTarget(pos, targetBlockEntity)) {
                    aeDisplayTargets.add(pos);
                }
            }
        } else {
            for (BlockPos pos : getTrackedTargetPositions()) {
                if (isTowerBlock(pos) || this.level.getBlockState(pos).isAir()) {
                    continue;
                }
                BlockEntity blockEntity = this.level.getBlockEntity(pos);
                if (allowsFeTargets() && targetAllowsFe(pos) && hasAnyEnergyCapability(pos)) {
                    endpoints.add(pos);
                }
                if (allowsAeTargets() && targetAllowsAe(pos) && this.targetDisplayResolver.hasDisplayableAeTarget(pos, blockEntity)) {
                    aeDisplayTargets.add(pos);
                }
            }
        }

        this.cachedEndpoints = List.copyOf(endpoints);
        this.cachedAeDisplayTargets = List.copyOf(aeDisplayTargets);
        this.endpointCacheValid = true;
    }

    private List<BlockPos> getTrackedTargetPositions() {
        LinkedHashSet<BlockPos> tracked = new LinkedHashSet<>(this.linkGraph.trackedPositions());
        tracked.addAll(this.targetTransferModes.keySet());
        return List.copyOf(tracked);
    }

    private List<BlockEntity> getNearbyBlockEntities() {
        if (this.level == null) {
            return List.of();
        }

        ArrayList<BlockEntity> results = new ArrayList<>();
        int chunkRadius = getChunkRadius();
        int minChunkX = this.coverage.minChunkX(chunkRadius);
        int maxChunkX = this.coverage.maxChunkX(chunkRadius);
        int minChunkZ = this.coverage.minChunkZ(chunkRadius);
        int maxChunkZ = this.coverage.maxChunkZ(chunkRadius);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (isWithinTowerCoverage(blockEntity.getBlockPos())) {
                        results.add(blockEntity);
                    }
                }
            }
        }

        return results;
    }

    private int compareLinkTargetPriority(BlockEntity left, BlockEntity right) {
        return compareLinkTargetPriority(left.getBlockPos(), right.getBlockPos());
    }

    private int compareLinkTargetPriority(BlockPos leftPos, BlockPos rightPos) {
        int leftPriority = getLinkTargetPriority(leftPos);
        int rightPriority = getLinkTargetPriority(rightPos);
        if (leftPriority != rightPriority) {
            return Integer.compare(rightPriority, leftPriority);
        }
        return compareBlockPos(leftPos, rightPos);
    }

    private int getLinkTargetPriority(BlockPos pos) {
        if (this.level == null) {
            return 0;
        }
        BlockEntity blockEntity = this.level.getBlockEntity(pos);
        return this.neoEcoAeBridge.isPreferredSubsystemHost(blockEntity) ? 1 : 0;
    }

    private boolean hasAnyEnergyCapability(BlockPos pos) {
        for (var direction : Direction.values()) {
            if (getEnergyStorageAt(pos, direction) != null) {
                return true;
            }
        }
        return getEnergyStorageAt(pos, null) != null;
    }

    private boolean hasStoredEnergy(BlockPos pos) {
        for (var direction : Direction.values()) {
            IEnergyStorage storage = getEnergyStorageAt(pos, direction);
            if (storage != null && storage.getEnergyStored() > 0) {
                return true;
            }
        }

        IEnergyStorage internal = getEnergyStorageAt(pos, null);
        return internal != null && internal.getEnergyStored() > 0;
    }

    @Override
    @Nullable
    public TargetKind preferredDisplayKind(BlockPos pos) {
        BlockEntity blockEntity = this.level == null ? null : this.level.getBlockEntity(pos);
        if (this.level == null || blockEntity instanceof DataDistributionTowerBlockEntity || this.level.getBlockState(pos).isAir()) {
            return null;
        }
        if (hasExposedAeNode(pos)) {
            return TargetKind.AE;
        }
        if (canReceiveEnergy(findReceiveEnergyStorage(pos))) {
            return TargetKind.FE;
        }
        return null;
    }

    @Override
    @Nullable
    public Level level() {
        return this.level;
    }

    @Override
    public Set<BlockPos> linkedPositions() {
        return this.linkGraph.linkedPositions();
    }

    @Override
    public List<BlockPos> trackedPositions() {
        return this.linkGraph.trackedPositions();
    }

    @Override
    public List<BlockPos> configuredTargetPositions() {
        return List.copyOf(this.targetTransferModes.keySet());
    }

    @Override
    public List<BlockPos> cachedAeDisplayTargets() {
        return getCachedAeDisplayTargets();
    }

    @Override
    public List<BlockPos> cachedEndpointPositions() {
        return getCachedEndpoints();
    }

    @Override
    public boolean allowsAeDisplayTargets() {
        return allowsAeTargets();
    }

    @Override
    public boolean hasReceiveEnergyTarget(BlockPos pos) {
        return canReceiveEnergy(findReceiveEnergyStorage(pos));
    }

    @Override
    public TargetTransferMode targetTransferMode(BlockPos pos) {
        return getTargetTransferMode(pos);
    }

    @Override
    public TargetTransferInfo targetTransferInfo(BlockPos pos) {
        return getTargetTransferInfo(pos);
    }

    @Override
    public boolean isTowerBlock(BlockPos pos) {
        return this.level != null && this.level.getBlockState(pos).is(ModBlocks.DATA_DISTRIBUTION_TOWER.get());
    }

    @Nullable
    private IEnergyStorage getEnergyStorageAt(BlockPos pos, @Nullable Direction side) {
        return this.energyEndpointResolver.getEnergyStorageAt(pos, side);
    }

    @Nullable
    private IEnergyStorage findReceiveEnergyStorage(BlockPos pos) {
        return this.energyEndpointResolver.findAccessibleEnergyStorage(pos, true);
    }

    @Override
    public List<TowerEnergyEndpoint> accessibleEnergyEndpoints(DataDistributionTowerBlockEntity tower, BlockPos pos, boolean forReceive) {
        return tower.energyEndpointResolver.findAccessibleEnergyEndpoints(pos, forReceive);
    }

    @Override
    public boolean isDedicatedAeGridTarget(BlockPos pos) {
        if (this.level == null || this.level.getBlockEntity(pos) instanceof CableBusBlockEntity) {
            return false;
        }

        if (!hasExposedAeNode(pos)) {
            return false;
        }

        for (IGridNode node : getConnectableNodes(this.level, pos)) {
            if (node != null && node.getGrid() != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isDedicatedAeGridTarget(DataDistributionTowerBlockEntity tower, BlockPos pos) {
        return tower.isDedicatedAeGridTarget(pos);
    }

    @Override
    public List<BlockPos> cachedEndpointPositions(DataDistributionTowerBlockEntity tower) {
        return tower.getCachedEndpoints();
    }

    @Override
    public boolean targetAllowsFe(DataDistributionTowerBlockEntity tower, BlockPos pos) {
        return tower.targetAllowsFe(pos);
    }

    @Override
    public List<DataDistributionTowerBlockEntity> collectTowerCluster() {
        if (this.level == null) {
            return List.of(this);
        }

        long gameTime = this.level.getGameTime();
        if (!this.cachedTowerCluster.isEmpty() && gameTime - this.lastClusterCacheTick < CLUSTER_CACHE_TICKS) {
            return this.cachedTowerCluster;
        }

        ArrayList<DataDistributionTowerBlockEntity> towers = new ArrayList<>();
        ArrayDeque<DataDistributionTowerBlockEntity> queue = new ArrayDeque<>();
        HashSet<BlockPos> visited = new HashSet<>();
        queue.add(this);
        visited.add(this.worldPosition);

        while (!queue.isEmpty()) {
            DataDistributionTowerBlockEntity tower = queue.removeFirst();
            towers.add(tower);

            for (BlockPos linkedPos : tower.linkGraph.linkedPositions()) {
                if (!visited.add(linkedPos)) {
                    continue;
                }

                BlockEntity blockEntity = this.level.getBlockEntity(linkedPos);
                if (blockEntity instanceof DataDistributionTowerBlockEntity nearbyTower) {
                    queue.add(nearbyTower);
                }
            }
        }

        this.cachedTowerCluster = List.copyOf(towers);
        this.lastClusterCacheTick = gameTime;
        this.cachedClusterCoordinatorPos = findCoordinatorPos(towers);
        return this.cachedTowerCluster;
    }

    private boolean isClusterCoordinator() {
        List<DataDistributionTowerBlockEntity> towers = collectTowerCluster();
        if (towers.isEmpty()) {
            return true;
        }
        return this.worldPosition.equals(this.cachedClusterCoordinatorPos);
    }

    private static BlockPos findCoordinatorPos(List<DataDistributionTowerBlockEntity> towers) {
        if (towers.isEmpty()) {
            return null;
        }

        BlockPos coordinatorPos = towers.getFirst().worldPosition;
        for (int i = 1; i < towers.size(); i++) {
            BlockPos candidatePos = towers.get(i).worldPosition;
            if (compareBlockPos(candidatePos, coordinatorPos) < 0) {
                coordinatorPos = candidatePos;
            }
        }
        return coordinatorPos;
    }

    private boolean performActiveRangeTransfer() {
        return this.energyDistributor.performActiveRangeTransfer();
    }

    private long readBufferedTransferEnergy(CompoundTag data) {
        if (!data.contains(BUFFERED_TRANSFER_ENERGY_TAG)) {
            return 0L;
        }
        if (!data.contains(BUFFERED_TRANSFER_ENERGY_TAG, Tag.TAG_LONG)) {
            LOGGER.error("Data Distribution Tower at {} has a non-long transfer buffer tag", this.worldPosition);
            return 0L;
        }

        long amount = data.getLong(BUFFERED_TRANSFER_ENERGY_TAG);
        if (amount < 0) {
            LOGGER.error("Data Distribution Tower at {} has negative buffered transfer energy: {}",
                    this.worldPosition, amount);
            return 0L;
        }
        return amount;
    }

    private long readQuarantinedTransferEnergy(CompoundTag data) {
        if (!data.contains(QUARANTINED_TRANSFER_ENERGY_TAG)) {
            return 0L;
        }
        if (!data.contains(QUARANTINED_TRANSFER_ENERGY_TAG, Tag.TAG_LONG)) {
            LOGGER.error("Data Distribution Tower at {} has a non-long quarantined transfer tag", this.worldPosition);
            return 0L;
        }

        long amount = data.getLong(QUARANTINED_TRANSFER_ENERGY_TAG);
        if (amount < 0) {
            LOGGER.error("Data Distribution Tower at {} has negative quarantined transfer energy: {}",
                    this.worldPosition, amount);
            return 0L;
        }
        return amount;
    }

    private long distributeEnergyInRange(long amount, boolean simulate, @Nullable BlockPos excludedPos) {
        return this.energyDistributor.distributeEnergyInRange(amount, simulate, excludedPos);
    }

    private boolean canReceiveEnergy(@Nullable IEnergyStorage storage) {
        return this.energyEndpointResolver.canReceiveEnergy(storage);
    }

    private int extractEnergyFromRange(int amount, boolean simulate, @Nullable BlockPos excludedPos) {
        return this.energyDistributor.extractEnergyFromRange(amount, simulate, excludedPos);
    }

    private long getTotalExtractableEnergy(@Nullable BlockPos excludedPos) {
        return this.energyDistributor.getTotalExtractableEnergy(excludedPos);
    }

    private long getTotalEnergyCapacity(@Nullable BlockPos excludedPos) {
        return this.energyDistributor.getTotalEnergyCapacity(excludedPos);
    }

    private boolean hasAnyReceiver(@Nullable BlockPos excludedPos) {
        return this.energyDistributor.hasAnyReceiver(excludedPos);
    }

    private boolean hasAnySource(@Nullable BlockPos excludedPos) {
        return this.energyDistributor.hasAnySource(excludedPos);
    }

    private boolean queueLink(BlockPos targetPos, int delay) {
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        if (!canMaintainGridLinkTo(normalizedPos)) {
            return false;
        }

        boolean queued = this.linkGraph.queuePending(normalizedPos, delay);
        if (queued) {
            incrementTargetDisplayStateRevision();
            requestAeTickWake();
        }
        return queued;
    }

    private void requestAeTickWake() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || !node.isActive()) {
            return;
        }

        IGrid grid = node.getGrid();
        if (grid != null) {
            grid.getTickManager().alertDevice(node);
        }
    }

    private boolean hasDeferredAeNetworkWork() {
        if (this.pendingNetworkRecovery || this.pendingRangeRefresh || this.rangeScanCursor != null || !this.linkGraph.pendingPositions().isEmpty() || this.bufferedTransferEnergy > 0 || allowsAutomaticRangeConnections()) {
            return true;
        }

        for (BlockPos targetPos : this.linkGraph.linkedPositions()) {
            if (getTargetTransferMode(targetPos) != TargetTransferMode.DISABLED) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cleanupInvalidDisplayTargets() {
        if (this.level == null) {
            return;
        }

        ArrayList<BlockPos> invalidPositions = new ArrayList<>();
        for (BlockPos pos : this.linkGraph.linkedPositions()) {
            if (this.level.getBlockState(pos).isAir()) {
                invalidPositions.add(pos);
            }
        }
        for (BlockPos pos : this.targetTransferModes.keySet()) {
            if (this.level.getBlockState(pos).isAir()) {
                invalidPositions.add(pos);
            }
        }

        for (BlockPos pos : invalidPositions) {
            removeTarget(pos);
        }
    }

    private void removeTarget(BlockPos targetPos) {
        this.linkGraph.removePending(targetPos);
        this.linkGraph.removeLinked(targetPos);
        this.targetTransferModes.remove(targetPos);
        destroyTargetConnections(targetPos);

        this.invalidateEndpointCache();
        this.invalidateClusterCache();
        this.setChanged();
    }

    private void destroyTargetConnections(BlockPos targetPos) {
        this.linkGraph.destroyTargetConnections(targetPos);
    }

    private void destroyAllConnections() {
        this.linkGraph.destroyAllConnections();
        this.invalidateClusterCache();
    }

    private int reconnectTarget(IGridNode selfNode, BlockPos targetPos, List<IGridNode> targetNodes) {
        Map<IGridNode, IGridConnection> existingConnections = this.linkGraph.connections(targetPos);
        LinkedHashMap<IGridNode, IGridConnection> reconciledConnections = new LinkedHashMap<>();
        int createdConnections = 0;
        for (IGridNode targetNode : targetNodes) {
            IGridConnection existingConnection = existingConnections.get(targetNode);
            if (existingConnection != null) {
                reconciledConnections.put(targetNode, existingConnection);
                continue;
            }
            try {
                reconciledConnections.put(targetNode, GridHelper.createConnection(selfNode, targetNode));
                createdConnections++;
            } catch (IllegalStateException exception) {
                LOGGER.debug("Failed to reconnect data distribution tower at {} to target {}", this.worldPosition, targetPos, exception);
            }
        }

        this.linkGraph.reconcileConnections(targetPos, reconciledConnections);
        this.invalidateEndpointCache();
        this.invalidateClusterCache();
        this.setChanged();
        return createdConnections;
    }

    private boolean hasAllConnections(BlockPos targetPos, List<IGridNode> targetNodes) {
        for (IGridNode targetNode : targetNodes) {
            if (!this.linkGraph.hasConnection(targetPos, targetNode)) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldReconnectTrackedTarget(BlockPos targetPos) {
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        return this.linkGraph.containsLinked(normalizedPos) || this.linkGraph.containsPending(normalizedPos);
    }

    private List<IGridNode> getLinkableTargetNodes(IGridNode selfNode, BlockPos targetPos) {
        if (!canMaintainGridLinkTo(targetPos)) {
            return List.of();
        }

        Level level = this.level;
        if (level == null) {
            return List.of();
        }

        List<IGridNode> targetNodes = getConnectableNodes(level, targetPos);
        if (targetNodes.isEmpty()) {
            return List.of();
        }

        boolean towerTarget = level.getBlockEntity(targetPos) instanceof DataDistributionTowerBlockEntity;
        ArrayList<IGridNode> linkableNodes = new ArrayList<>();
        for (IGridNode targetNode : targetNodes) {
            if (this.linkGraph.hasConnection(targetPos, targetNode) || isLinkableTargetNode(selfNode, targetNode, towerTarget)) {
                linkableNodes.add(targetNode);
            }
        }
        return List.copyOf(linkableNodes);
    }

    private boolean isLinkableTargetNode(IGridNode selfNode, @Nullable IGridNode targetNode, boolean towerTarget) {
        if (targetNode == null || targetNode == selfNode) {
            return false;
        }

        boolean bridgeableTarget = towerTarget || targetNode.getOwner() instanceof TrinityAccessHatchBlockEntity;
        IGrid targetGrid = targetNode.getGrid();
        IGrid selfGrid = selfNode.getGrid();
        if (targetGrid != null && selfGrid != null) {
            if (targetGrid == selfGrid) {
                return !targetNode.meetsChannelRequirements();
            }

            if (!bridgeableTarget && targetNode.isOnline()) {
                return false;
            }

            ControllerState targetControllerState = targetGrid.getPathingService().getControllerState();
            ControllerState selfControllerState = selfGrid.getPathingService().getControllerState();
            return targetControllerState == ControllerState.NO_CONTROLLER || selfControllerState == ControllerState.NO_CONTROLLER;
        }
        if (!bridgeableTarget && targetNode.isOnline()) {
            return false;
        }
        return true;
    }

    private void requeuePersistedLinks() {
        if (this.linkGraph.linkedPositions().isEmpty()) {
            return;
        }

        List<BlockPos> persisted = List.copyOf(this.linkGraph.linkedPositions());
        this.linkGraph.clearPending();
        destroyAllConnections();
        for (BlockPos pos : persisted) {
            queueLink(pos, 0);
        }
    }

    private void schedulePersistedLinkRequeue() {
        if (this.level == null || this.level.isClientSide() || allowsAutomaticRangeConnections() || this.linkGraph.linkedPositions().isEmpty()) {
            return;
        }
        MinecraftServer server = this.level.getServer();
        if (server == null) {
            return;
        }
        schedulePersistedLinkRequeue(server, this.recoveryEpoch, PERSISTED_LINK_REQUEUE_TICKS);
    }

    private void schedulePersistedLinkRequeue(MinecraftServer server, long expectedRecoveryEpoch, int remainingTicks) {
        if (remainingTicks <= 0) {
            return;
        }
        ServerTickDelayQueue.runNextServerTick(server, () -> {
            if (!isCurrentRecoveryEpoch(expectedRecoveryEpoch)) {
                return;
            }

            this.pendingNetworkRecovery = true;
            requestAeTickWake();
            schedulePersistedLinkRequeue(server, expectedRecoveryEpoch, remainingTicks - 1);
        });
    }

    private boolean isCurrentRecoveryEpoch(long expectedRecoveryEpoch) {
        return this.recoveryEpoch == expectedRecoveryEpoch && this.level != null && !this.level.isClientSide() && this.level.getBlockEntity(this.worldPosition) == this;
    }

    private boolean enqueuePersistedLinkReconciliation() {
        if (this.linkGraph.linkedPositions().isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (BlockPos pos : List.copyOf(this.linkGraph.linkedPositions())) {
            if (this.linkGraph.containsPending(pos)) {
                continue;
            }
            changed |= queueLink(pos, 0);
        }
        if (changed) {
            this.setChanged();
        }
        return changed;
    }

    private void resetAutoDiscoveryCooldown() {
        this.autoDiscoveryCooldown = Math.floorMod(this.worldPosition.hashCode(), INITIAL_DISCOVERY_STAGGER_TICKS);
    }

    private boolean processAutoDiscovery(int elapsedTicks) {
        if (this.level == null || this.level.isClientSide()) {
            return false;
        }

        if (!allowsAutomaticRangeConnections()) {
            return false;
        }

        if (this.autoDiscoveryCooldown > elapsedTicks) {
            this.autoDiscoveryCooldown -= elapsedTicks;
            return false;
        }

        int elapsedAfterDeadline = this.autoDiscoveryCooldown > 0 ? elapsedTicks - this.autoDiscoveryCooldown : 0;
        this.autoDiscoveryCooldown = AUTO_DISCOVERY_INTERVAL_TICKS - Math.floorMod(elapsedAfterDeadline, AUTO_DISCOVERY_INTERVAL_TICKS);
        boolean queued = enqueuePersistedLinkReconciliation();
        boolean scanRequested = requestNearbyConnectableNodeScanIfIdle();
        return queued || scanRequested;
    }

    public static List<IGridNode> getConnectableNodes(Level level, BlockPos pos) {
        IInWorldGridNodeHost nodeHost = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
        if (nodeHost == null) {
            return List.of();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        IPartHost partHost = blockEntity instanceof IPartHost host ? host : null;
        return collectConnectableNodes(partHost, nodeHost);
    }

    /**
     * Collects every distinct sided node exposed by one capability host.
     *
     * @param nodeHost host returned by the direction-neutral AE capability query
     * @return immutable nodes in AE direction iteration order, de-duplicated by node identity
     */
    static List<IGridNode> collectConnectableNodes(IInWorldGridNodeHost nodeHost) {
        return collectConnectableNodes(null, nodeHost);
    }

    private static List<IGridNode> collectConnectableNodes(@Nullable IPartHost partHost,
                                                           IInWorldGridNodeHost nodeHost) {
        Set<IGridNode> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<IGridNode> orderedNodes = new ArrayList<>();

        if (partHost != null) {
            addConnectableNode(partHost.getPart(null), nodes, orderedNodes);
            for (Direction direction : Direction.values()) {
                addConnectableNode(partHost.getPart(direction), nodes, orderedNodes);
            }
        }

        for (Direction direction : Direction.values()) {
            addConnectableNode(nodeHost.getGridNode(direction), nodes, orderedNodes);
        }

        return List.copyOf(orderedNodes);
    }

    private static void addConnectableNode(@Nullable IPart part, Set<IGridNode> nodes,
                                           List<IGridNode> orderedNodes) {
        if (part != null) {
            addConnectableNode(part.getGridNode(), nodes, orderedNodes);
        }
    }

    private static void addConnectableNode(@Nullable IGridNode node, Set<IGridNode> nodes,
                                           List<IGridNode> orderedNodes) {
        if (node != null && nodes.add(node)) {
            orderedNodes.add(node);
        }
    }

    private static void invalidateNearbyCaches(Level level, BlockPos changedPos) {
        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, new ChunkPos(changedPos)));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            BlockEntity blockEntity = level.getBlockEntity(towerPos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
                if (!tower.isWithinTowerCoverage(changedPos)) {
                    continue;
                }

                tower.invalidateEndpointCache();
            }
        }
    }

    private static void ensureBound(@Nullable MinecraftServer server) {
        if (server == null) {
            TOWER_CHUNK_POSITIONS.clear();
            boundServer = null;
            return;
        }

        if (boundServer != server) {
            TOWER_CHUNK_POSITIONS.clear();
            boundServer = server;
        }
    }

    private static int compareBlockPos(BlockPos a, BlockPos b) {
        int cmp = Integer.compare(a.getX(), b.getX());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(a.getY(), b.getY());
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    private static int clampStoredAmount(long amount) {
        if (amount <= 0) {
            return 0;
        }
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    @Nullable
    private static BlockPos normalizeExcludedPos(@Nullable BlockPos excludedPos) {
        return excludedPos == null ? null : excludedPos.immutable();
    }

    private BlockPos normalizeTargetPos(BlockPos targetPos) {
        if (this.level != null) {
            BlockPos networkPortPos = DataSanctumBlockEntity.findNetworkPortPos(this.level, targetPos);
            if (networkPortPos != null) {
                return networkPortPos.immutable();
            }
        }
        return targetPos.immutable();
    }

    private void emitDiagnosticLogIfNeeded() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        long gameTime = this.level.getGameTime();
        if (this.diagnosticWindowStartTick == Long.MIN_VALUE) {
            this.diagnosticWindowStartTick = gameTime;
            return;
        }

        if (gameTime - this.diagnosticWindowStartTick < DIAGNOSTIC_LOG_INTERVAL_TICKS) {
            return;
        }

        if (hasDiagnosticActivity()) {
            LOGGER.info(
                    "DDT diag pos={} dim={} window={}t realExtractCalls={} realExtractReq={} realExtractOut={} simExtractCalls={} simExtractReq={} simExtractOut={} receiveCalls={} receiveReq={} receiveIn={} getStoredCalls={} getMaxStoredCalls={} canExtractCalls={} canReceiveCalls={} simCacheHits={} simCacheMisses={} maxExtractEndpoints={} maxReceiveEndpoints={}",
                    this.worldPosition,
                    this.level.dimension().location(),
                    gameTime - this.diagnosticWindowStartTick,
                    this.diagnosticRealExtractCalls,
                    this.diagnosticRequestedRealExtract,
                    this.diagnosticReturnedRealExtract,
                    this.diagnosticSimulatedExtractCalls,
                    this.diagnosticRequestedSimulatedExtract,
                    this.diagnosticReturnedSimulatedExtract,
                    this.diagnosticReceiveCalls,
                    this.diagnosticRequestedReceive,
                    this.diagnosticReturnedReceive,
                    this.diagnosticGetStoredCalls,
                    this.diagnosticGetMaxStoredCalls,
                    this.diagnosticCanExtractCalls,
                    this.diagnosticCanReceiveCalls,
                    this.diagnosticSimulatedCacheHits,
                    this.diagnosticSimulatedCacheMisses,
                    this.diagnosticMaxExtractEndpoints,
                    this.diagnosticMaxReceiveEndpoints);
        }

        resetDiagnosticCounters(gameTime);
    }

    private boolean hasDiagnosticActivity() {
        return this.diagnosticRealExtractCalls > 0 || this.diagnosticSimulatedExtractCalls > 0 || this.diagnosticReceiveCalls > 0 || this.diagnosticGetStoredCalls > 0 || this.diagnosticGetMaxStoredCalls > 0 || this.diagnosticCanExtractCalls > 0 || this.diagnosticCanReceiveCalls > 0;
    }

    private void resetDiagnosticCounters(long gameTime) {
        this.diagnosticWindowStartTick = gameTime;
        this.diagnosticRealExtractCalls = 0;
        this.diagnosticSimulatedExtractCalls = 0;
        this.diagnosticReceiveCalls = 0;
        this.diagnosticGetStoredCalls = 0;
        this.diagnosticGetMaxStoredCalls = 0;
        this.diagnosticCanExtractCalls = 0;
        this.diagnosticCanReceiveCalls = 0;
        this.diagnosticSimulatedCacheHits = 0;
        this.diagnosticSimulatedCacheMisses = 0;
        this.diagnosticRequestedRealExtract = 0L;
        this.diagnosticReturnedRealExtract = 0L;
        this.diagnosticRequestedSimulatedExtract = 0L;
        this.diagnosticReturnedSimulatedExtract = 0L;
        this.diagnosticRequestedReceive = 0L;
        this.diagnosticReturnedReceive = 0L;
        this.diagnosticMaxExtractEndpoints = 0;
        this.diagnosticMaxReceiveEndpoints = 0;
    }

    private static long saturatingAdd(long current, long delta) {
        if (delta <= 0) {
            return current;
        }
        if (Long.MAX_VALUE - current < delta) {
            return Long.MAX_VALUE;
        }
        return current + delta;
    }

    private static String formatFeAmount(long amount) {
        if (amount >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fG", amount / 1_000_000_000.0);
        }
        if (amount >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", amount / 1_000.0);
        }
        return Long.toString(amount);
    }

    private void applyPendingRangeRefresh() {
        this.pendingRangeRefresh = false;

        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        int oldChunkRadius = this.indexedChunkRadius >= 0 ? this.indexedChunkRadius : getChunkRadius();
        int newChunkRadius = getChunkRadius();

        if (oldChunkRadius != newChunkRadius) {
            unregisterFromChunkIndex();
            registerInChunkIndex();
        }

        pruneTargetsOutsideRange();
        invalidateEndpointCache();
        if (allowsAutomaticRangeConnections()) {
            requestNearbyConnectableNodeScan();
        }
    }

    private void refreshConnectionTargets() {
        if (this.level == null || this.level.isClientSide()) {
            invalidateEndpointCache();
            invalidateClusterCache();
            return;
        }

        ArrayList<BlockPos> retainedTargets = new ArrayList<>(this.linkGraph.linkedPositions());
        for (BlockPos pos : this.linkGraph.pendingPositions()) {
            if (!retainedTargets.contains(pos)) {
                retainedTargets.add(pos);
            }
        }

        this.linkGraph.clearPending();
        destroyAllConnections();
        invalidateEndpointCache();
        invalidateClusterCache();

        for (BlockPos pos : retainedTargets) {
            if (canMaintainGridLinkTo(pos)) {
                queueLink(pos, 0);
            }
        }

        if (allowsAutomaticRangeConnections()) {
            requestNearbyConnectableNodeScan();
        }
    }

    private static final class RangeScanCursor {

        private final int minChunkX;
        private final int maxChunkX;
        private final int minChunkZ;
        private final int maxChunkZ;
        private int nextChunkX;
        private int nextChunkZ;

        private RangeScanCursor(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
            this.minChunkX = minChunkX;
            this.maxChunkX = maxChunkX;
            this.minChunkZ = minChunkZ;
            this.maxChunkZ = maxChunkZ;
            this.nextChunkX = minChunkX;
            this.nextChunkZ = minChunkZ;
        }

        private boolean matches(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
            return this.minChunkX == minChunkX && this.maxChunkX == maxChunkX && this.minChunkZ == minChunkZ && this.maxChunkZ == maxChunkZ;
        }

        private boolean isComplete() {
            return this.nextChunkZ > this.maxChunkZ;
        }

        private int chunkX() {
            return this.nextChunkX;
        }

        private int chunkZ() {
            return this.nextChunkZ;
        }

        private void advance() {
            if (this.nextChunkX < this.maxChunkX) {
                this.nextChunkX++;
                return;
            }

            this.nextChunkX = this.minChunkX;
            this.nextChunkZ++;
        }
    }

    private boolean canMaintainGridLinkTo(BlockPos targetPos) {
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        if (this.level == null || this.worldPosition.equals(normalizedPos) || !isWithinTowerCoverage(normalizedPos)) {
            return false;
        }
        if (getTargetTransferMode(normalizedPos) == TargetTransferMode.DISABLED) {
            return false;
        }

        BlockEntity blockEntity = this.level.getBlockEntity(normalizedPos);
        if (blockEntity instanceof DataDistributionTowerBlockEntity) {
            return true;
        }

        return allowsAeTargets() && hasExposedAeNode(normalizedPos) && (this.linkGraph.containsLinked(normalizedPos) || this.linkGraph.containsPending(normalizedPos) || needsAeChannelLink(normalizedPos));
    }

    private boolean allowsAutomaticRangeConnections() {
        return this.rangeAdjustmentMode == RangeAdjustmentMode.SCOPE;
    }

    private boolean allowsAeTargets() {
        return this.connectionMode.allowsAeTargets();
    }

    private boolean allowsFeTargets() {
        return this.connectionMode.allowsFeTargets();
    }

    @Override
    public boolean targetAllowsAe(BlockPos targetPos) {
        if (getTargetTransferMode(targetPos) == TargetTransferMode.DISABLED) {
            return false;
        }
        return hasExposedAeNode(targetPos);
    }

    @Override
    public boolean targetAllowsFe(BlockPos targetPos) {
        if (getTargetTransferMode(targetPos) == TargetTransferMode.DISABLED) {
            return false;
        }
        return hasAnyEnergyCapability(targetPos);
    }

    @Override
    public boolean hasExposedAeNode(BlockPos targetPos) {
        return this.level != null && !getConnectableNodes(this.level, normalizeTargetPos(targetPos)).isEmpty();
    }

    private boolean needsAeChannelLink(BlockPos targetPos) {
        if (this.level == null || !hasExposedAeNode(targetPos)) {
            return false;
        }

        for (IGridNode node : getConnectableNodes(this.level, targetPos)) {
            if (node != null && (!node.isOnline() || !node.meetsChannelRequirements())) {
                return true;
            }
        }
        return false;
    }

    private void pruneTargetsOutsideRange() {
        ArrayList<BlockPos> toRemove = new ArrayList<>();
        for (BlockPos pos : this.linkGraph.linkedPositions()) {
            if (!isWithinTowerCoverage(pos)) {
                toRemove.add(pos);
            }
        }
        for (BlockPos pos : this.linkGraph.pendingPositions()) {
            if (!isWithinTowerCoverage(pos) && !toRemove.contains(pos)) {
                toRemove.add(pos);
            }
        }
        for (BlockPos pos : this.targetTransferModes.keySet()) {
            if (!isWithinTowerCoverage(pos) && !toRemove.contains(pos)) {
                toRemove.add(pos);
            }
        }
        for (BlockPos pos : toRemove) {
            removeTarget(pos);
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack boosters = this.wirelessBoosters.getStackInSlot(0);
        if (!boosters.isEmpty()) {
            drops.add(boosters.copy());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.wirelessBoosters.setItemDirect(0, ItemStack.EMPTY);
        updateIdlePowerUsage();
    }

    private record ChunkKey(Object dimension, int x, int z) {

        private ChunkKey(Level level, int x, int z) {
            this(level.dimension(), x, z);
        }

        private ChunkKey(Level level, ChunkPos pos) {
            this(level, pos.x, pos.z);
        }
    }

    public record BoundTargetSummary(ResourceLocation itemId, String displayName, int count,
                                     ResourceLocation dimensionId, BlockPos pos, TargetKind kind,
                                     TargetTransferMode transferMode, TargetTransferInfo transferInfo) {}

    public record TargetTransferInfo(int channelConnections, boolean hasAeTarget, boolean hasEnergyTarget,
                                     long storedFe, long capacityFe, boolean canExtractFe, boolean canReceiveFe) {

        private static final TargetTransferInfo EMPTY = new TargetTransferInfo(0, false, false, 0L, 0L, false, false);
    }

    public record ConnectorBindResult(boolean success, ConnectorBindFailure failure, boolean aeSupported,
                                      boolean feSupported) {

        public static ConnectorBindResult success(boolean aeSupported, boolean feSupported) {
            return new ConnectorBindResult(true, null, aeSupported, feSupported);
        }

        public static ConnectorBindResult fail(ConnectorBindFailure failure) {
            return new ConnectorBindResult(false, failure, false, false);
        }
    }

    public enum ConnectorBindFailure {
        NOT_POINT_MODE,
        OUT_OF_RANGE,
        SELF_TARGET,
        UNSUPPORTED
    }

    @Getter
    public enum TargetTransferMode {

        AUTO("auto"),
        DISABLED("off");

        private final String serializedName;

        TargetTransferMode(String serializedName) {
            this.serializedName = serializedName;
        }

        public static TargetTransferMode fromOrdinal(int ordinal) {
            TargetTransferMode[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return AUTO;
            }
            return values[ordinal];
        }

        public static TargetTransferMode fromSerializedName(@Nullable String serializedName) {
            if (serializedName != null) {
                if ("af".equalsIgnoreCase(serializedName) || "ae".equalsIgnoreCase(serializedName) || "fe".equalsIgnoreCase(serializedName)) {
                    return AUTO;
                }
                for (TargetTransferMode value : values()) {
                    if (value.serializedName.equalsIgnoreCase(serializedName)) {
                        return value;
                    }
                }
            }
            return AUTO;
        }
    }

    @Getter
    public enum ConnectionMode {

        AE_ONLY("ae"),
        FE_ONLY("fe"),
        AE_AND_FE("af");

        private final String serializedName;

        ConnectionMode(String serializedName) {
            this.serializedName = serializedName;
        }

        public boolean allowsAeTargets() {
            return this != FE_ONLY;
        }

        public boolean allowsFeTargets() {
            return this != AE_ONLY;
        }

        public ConnectionMode next() {
            return switch (this) {
                case AE_ONLY -> FE_ONLY;
                case FE_ONLY -> AE_AND_FE;
                case AE_AND_FE -> AE_ONLY;
            };
        }

        public static ConnectionMode fromOrdinal(int ordinal) {
            ConnectionMode[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return AE_AND_FE;
            }
            return values[ordinal];
        }

        public static ConnectionMode fromSerializedName(@Nullable String serializedName) {
            if (serializedName != null) {
                for (ConnectionMode value : values()) {
                    if (value.serializedName.equalsIgnoreCase(serializedName)) {
                        return value;
                    }
                }
            }
            return AE_AND_FE;
        }
    }

    @Getter
    public enum RangeAdjustmentMode {

        POINT("point"),
        SCOPE("scope");

        private final String serializedName;

        RangeAdjustmentMode(String serializedName) {
            this.serializedName = serializedName;
        }

        public static RangeAdjustmentMode fromOrdinal(int ordinal) {
            RangeAdjustmentMode[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return POINT;
            }
            return values[ordinal];
        }

        public static RangeAdjustmentMode fromSerializedName(@Nullable String serializedName) {
            if (serializedName != null) {
                for (RangeAdjustmentMode value : values()) {
                    if (value.serializedName.equalsIgnoreCase(serializedName)) {
                        return value;
                    }
                }
            }
            return POINT;
        }
    }

    public enum TargetKind {
        AE,
        FE
    }

    private class TowerEnergyStorage implements IEnergyStorage {

        @Nullable
        private final BlockPos excludedPos;

        private TowerEnergyStorage(@Nullable BlockPos excludedPos) {
            this.excludedPos = excludedPos;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            diagnosticReceiveCalls++;
            diagnosticRequestedReceive += maxReceive;
            int received = clampStoredAmount(distributeEnergyInRange(maxReceive, simulate, this.excludedPos));
            diagnosticReturnedReceive += received;
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (simulate) {
                diagnosticSimulatedExtractCalls++;
                diagnosticRequestedSimulatedExtract += maxExtract;
            } else {
                diagnosticRealExtractCalls++;
                diagnosticRequestedRealExtract += maxExtract;
            }

            int extracted = extractEnergyFromRange(maxExtract, simulate, this.excludedPos);
            if (simulate) {
                diagnosticReturnedSimulatedExtract += extracted;
            } else {
                diagnosticReturnedRealExtract += extracted;
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            diagnosticGetStoredCalls++;
            return clampStoredAmount(getTotalExtractableEnergy(this.excludedPos));
        }

        @Override
        public int getMaxEnergyStored() {
            diagnosticGetMaxStoredCalls++;
            return clampStoredAmount(getTotalEnergyCapacity(this.excludedPos));
        }

        @Override
        public boolean canExtract() {
            diagnosticCanExtractCalls++;
            return hasAnySource(this.excludedPos);
        }

        @Override
        public boolean canReceive() {
            diagnosticCanReceiveCalls++;
            return hasAnyReceiver(this.excludedPos);
        }
    }
}
