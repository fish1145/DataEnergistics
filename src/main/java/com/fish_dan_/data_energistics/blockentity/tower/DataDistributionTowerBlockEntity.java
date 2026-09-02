package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.grid.TowerMountedGridNodeHost;
import com.fish_dan_.data_energistics.block.tower.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.sanctum.DataSanctumBlockEntity;
import com.fish_dan_.data_energistics.blockentity.tower.energy.CachedTowerEnergyEndpointResolver;
import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDistributorContext;
import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyEndpoint;
import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyEndpointResolver;
import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyEndpointResolverContext;
import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyTransferEngine;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegrationRegistry;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBinding;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBindingKind;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBindingRuntimeSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBindingSource;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerRuntimeKey;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.VersionedTowerBindingCodec;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerChannelOverview;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerDeviceKey;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerGridOwnershipRegistry;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerNetworkDomain;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerNetworkDomainChange;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerNetworkParticipant;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerNetworkTowerSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerVirtualDeviceSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerVirtualDeviceState;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.TowerEnergyAccessSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.TowerEnergyLocation;
import com.fish_dan_.data_energistics.blockentity.tower.topology.TowerCoverageGeometry;
import com.fish_dan_.data_energistics.blockentity.tower.topology.TowerLinkStateGraph;
import com.fish_dan_.data_energistics.blockentity.tower.topology.TowerLinkStateGraph.TargetLinkFailure;
import com.fish_dan_.data_energistics.blockentity.tower.topology.TowerLinkStateGraph.TargetLinkState;
import com.fish_dan_.data_energistics.blockentity.tower.topology.TowerLinkStateGraph.TargetLinkStatus;
import com.fish_dan_.data_energistics.blockentity.tower.topology.TowerTargetDisplayResolverContext;
import com.fish_dan_.data_energistics.blockentity.tower.topology.TowerTargetSummaryResolver;
import com.fish_dan_.data_energistics.common.memorycard.MemoryCardSettingsHelper;
import com.fish_dan_.data_energistics.common.tick.ServerTickDelayQueue;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.curios.CuriosDataDistributionConnectorAccess;
import com.fish_dan_.data_energistics.integration.tower.crafting.AeCraftingDisplayBridge;
import com.fish_dan_.data_energistics.integration.tower.energy.appflux.AE2FluxIntegration;
import com.fish_dan_.data_energistics.integration.tower.energy.neoecoae.NeoEcoAeTowerBridge;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItem;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorSelector;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import appeng.api.AECapabilities;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.AEItemDefinitionFilter;
import lombok.Getter;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

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
import java.util.OptionalLong;
import java.util.Set;

@EventBusSubscriber(modid = Data_Energistics.MODID)
public class DataDistributionTowerBlockEntity extends AENetworkedBlockEntity implements
                                              InternalInventoryHost, TowerEnergyEndpointResolverContext, TowerEnergyDistributorContext,
                                              TowerTargetDisplayResolverContext, TowerNetworkParticipant, IGridTickable {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final VersionedTowerBindingCodec TOWER_BINDING_PERSISTENCE = new VersionedTowerBindingCodec();
    /**
     * Selects the connector source captured when a player places a potentially compatible target.
     */
    private static final DataDistributionConnectorSelector CONNECTOR_SELECTOR = DataDistributionConnectorSelector.create();
    private static final String SHOW_RANGE_TAG = "show_range";
    private static final String CONNECTION_MODE_TAG = "connection_mode";
    private static final String TARGET_TRANSFER_MODES_TAG = "target_transfer_modes";
    private static final String BUFFERED_TRANSFER_ENERGY_TAG = "buffered_transfer_energy";
    private static final String QUARANTINED_TRANSFER_ENERGY_TAG = "quarantined_transfer_energy";
    private static final int LINK_RETRY_INITIAL_DELAY_TICKS = 20;
    private static final int LINK_RETRY_MAX_DELAY_TICKS = 200;
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
    private static final int TARGET_ENERGY_SNAPSHOT_FAILURE_LOG_INTERVAL_TICKS = 100;
    private static final int CACHE_CLEANUP_INTERVAL_TICKS = 6000;
    private static final int MAX_ENERGY_STORAGE_VIEWS = 256;
    private static final double BASE_IDLE_POWER_USAGE = 4.0;
    private static final double IDLE_POWER_USAGE_PER_ADDITIONAL_CHUNK = 8.0;
    private static final Map<ChunkKey, Set<BlockPos>> TOWER_CHUNK_POSITIONS = new HashMap<>();
    private static final Map<Level, Map<BlockPos, DataDistributionTowerBlockEntity>> LOADED_TOWERS = new IdentityHashMap<>();
    private static MinecraftServer boundServer;

    private final TowerCoverageGeometry coverage;
    private final TowerLinkStateGraph linkGraph = new TowerLinkStateGraph();
    private final Map<BlockPos, TowerBinding> towerBindings = new LinkedHashMap<>();
    private final NeoEcoAeTowerBridge neoEcoAeBridge = new NeoEcoAeTowerBridge();
    private final TowerEnergyEndpointIntegrationRegistry energyIntegrations;
    private final TowerEnergyEndpointResolver energyEndpointResolver;
    private final TowerEnergyTransferEngine energyDistributor;
    private final TowerTargetSummaryResolver targetDisplayResolver;
    private final Map<BlockPos, TargetTransferMode> targetTransferModes = new HashMap<>();
    private final Map<TargetEnergyFailureKey, Long> targetEnergySnapshotFailureLogTicks = new HashMap<>();
    private final Map<BlockPos, TowerEnergyStorage> cachedEnergyStorageViews = new HashMap<>();
    private final AppEngInternalInventory wirelessBoosters = new AppEngInternalInventory(this, 1);
    private long bufferedTransferEnergy;
    private long quarantinedTransferEnergy;
    private long nextBindingFifoSequence;
    private TowerNetworkTowerSnapshot towerNetworkSnapshot = new TowerNetworkTowerSnapshot(
            0,
            new TowerChannelOverview(OptionalLong.of(0), 0, 0, OptionalLong.of(0)),
            List.of());
    @Nullable
    private TowerNetworkDomain registeredTowerDomain;
    private long lastClusterCacheTick = Long.MIN_VALUE;
    private List<BlockPos> cachedEndpoints = List.of();
    private List<BlockPos> cachedAeDisplayTargets = List.of();
    private List<DataDistributionTowerBlockEntity> cachedTowerCluster = List.of();
    private boolean endpointCacheValid;
    private long targetDisplayStateRevision;
    /**
     * Reuses one immutable full target snapshot across menus during the same level tick.
     */
    private long cachedBoundTargetSummariesTick = Long.MIN_VALUE;
    private long cachedBoundTargetSummariesRevision = Long.MIN_VALUE;
    private List<BoundTargetSummary> cachedBoundTargetSummaries = List.of();
    private long cachedTowerNetworkTargetSummariesTick = Long.MIN_VALUE;
    private long cachedTowerNetworkTargetSummariesRevision = Long.MIN_VALUE;
    private List<BoundTargetSummary> cachedTowerNetworkTargetSummaries = List.of();
    private boolean verboseRuntimeLoggingEnabled;
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
    private boolean linkReconciliationInProgress;
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

    public DataDistributionTowerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.coverage = new TowerCoverageGeometry(blockPos);
        this.energyIntegrations = TowerEnergyEndpointIntegrationRegistry.createDefault();
        AeCraftingDisplayBridge aeCraftingDisplayBridge = new AeCraftingDisplayBridge();
        this.energyEndpointResolver = new CachedTowerEnergyEndpointResolver(
                this,
                this.energyIntegrations);
        this.energyDistributor = new TowerEnergyTransferEngine(
                this, this.energyEndpointResolver, this.energyIntegrations);
        this.targetDisplayResolver = new TowerTargetSummaryResolver(this, this.neoEcoAeBridge, aeCraftingDisplayBridge);
        this.wirelessBoosters.setFilter(new AEItemDefinitionFilter(AEItems.WIRELESS_BOOSTER));
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(BASE_IDLE_POWER_USAGE)
                .addService(IGridTickable.class, this);
    }

    @Override
    public void onReady() {
        super.onReady();
        updateIdlePowerUsage();
        if (!(this.level instanceof ServerLevel)) {
            return;
        }
        registerLoadedTower();
        registerInChunkIndex();
        invalidateEndpointCache();
        resetPersistedLinkRuntimeState();
        resetAutoDiscoveryCooldown();
        this.pendingNetworkRecovery = true;
        this.mainNodeActive = this.getMainNode().isActive();
        syncTowerDomainRegistration();
        requestAeTickWake();
    }

    @Override
    public void setRemoved() {
        this.pendingNetworkRecovery = false;
        if (this.level instanceof ServerLevel) {
            unregisterTowerDomain();
            unregisterLoadedTower();
            unregisterFromChunkIndex();
            clearRuntimeCaches();
        }
        super.setRemoved();
    }

    /**
     * Removes this tower's server-wide ownership candidates when the block is actually destroyed.
     * Chunk unload deliberately does not call this hook so the tower keeps its original FIFO position.
     */
    public void onPermanentlyRemoved() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        TowerGridOwnershipRegistry.removeTower(serverLevel.getServer(), towerKey());
    }

    @Override
    public void onChunkUnloaded() {
        this.pendingNetworkRecovery = false;
        if (this.level instanceof ServerLevel) {
            unregisterTowerDomain();
            unregisterLoadedTower();
            unregisterFromChunkIndex();
        }
        invalidateClusterCache();
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
        this.towerBindings.clear();
        this.nextBindingFifoSequence = 0;
        this.targetTransferModes.clear();

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

        readTowerBindings(data);

        clearRuntimeCaches();
    }

    /**
     * Restores versioned bindings without requiring a loaded level.
     */
    private void readTowerBindings(CompoundTag data) {
        for (TowerBinding binding : TOWER_BINDING_PERSISTENCE.read(data)) {
            this.towerBindings.put(binding.anchor(), binding);
            this.linkGraph.addLinked(binding.anchor());
            if (!binding.enabled()) {
                this.targetTransferModes.put(binding.anchor(), TargetTransferMode.DISABLED);
            }
            this.nextBindingFifoSequence = Math.max(
                    this.nextBindingFifoSequence,
                    Math.incrementExact(binding.fifoSequence()));
        }
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

        TOWER_BINDING_PERSISTENCE.write(data, towerBindings());

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
                builder.set(DEDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get(), settings);
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
        builder.set(DEDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get(), settings);
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        CompoundTag settings = input.get(DEDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get());
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
        if (!(this.level instanceof ServerLevel)) {
            return;
        }

        emitDiagnosticLogIfNeeded();
        syncTowerDomainRegistration();

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
            completedNetworkWork |= requeuePersistedLinks();
        }
        if (this.pendingRangeRefresh) {
            applyPendingRangeRefresh();
            completedNetworkWork = true;
        }

        completedNetworkWork |= processAutoDiscovery(elapsedTicks);
        completedNetworkWork |= processRangeScanBatch(elapsedTicks);

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

        TowerBinding binding = this.towerBindings.get(normalizedPos);
        if (binding != null) {
            this.towerBindings.put(normalizedPos, binding.withEnabled(normalizedMode != TargetTransferMode.DISABLED));
        }

        if (normalizedMode == TargetTransferMode.DISABLED) {
            this.linkGraph.transition(
                    normalizedPos, TargetLinkState.DISABLED, TargetLinkFailure.NONE, 0);
        } else if (this.linkGraph.containsLinked(normalizedPos)) {
            this.linkGraph.transition(
                    normalizedPos,
                    allowsAeTargets() ? TargetLinkState.PENDING : TargetLinkState.BOUND,
                    TargetLinkFailure.NONE,
                    0);
        }

        invalidateEndpointCache();
        invalidateClusterCache();
        invalidateTowerDomain(TowerNetworkDomainChange.BINDING);
        this.setChanged();
        this.markForClientUpdate();
    }

    public boolean setTowerNetworkTargetTransferMode(
                                                     TowerRuntimeKey ownerTower,
                                                     ResourceLocation dimensionId,
                                                     BlockPos targetPos,
                                                     @Nullable TargetTransferMode mode) {
        BlockPos normalizedPos = targetPos.immutable();
        for (DataDistributionTowerBlockEntity tower : collectTowerCluster()) {
            Level towerLevel = tower.level;
            TowerBinding binding = tower.towerBindings.get(normalizedPos);
            if (towerLevel != null && ownerTower.dimensionId().equals(towerLevel.dimension().location()) && ownerTower.position().equals(tower.worldPosition) && dimensionId.equals(towerLevel.dimension().location()) && binding != null && binding.kind() == TowerBindingKind.TARGET) {
                tower.setTargetTransferMode(normalizedPos, mode);
                return true;
            }
        }
        return false;
    }

    /**
     * Enables or disables one resolved virtual device without affecting sibling devices in the same subnet.
     *
     * @param targetPos anchor owning the device
     * @param deviceKey stable device key received from the server snapshot
     * @param disabled  desired disabled state
     * @return whether the binding exists
     */
    public boolean setVirtualDeviceDisabled(BlockPos targetPos, TowerDeviceKey deviceKey, boolean disabled) {
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        TowerBinding binding = this.towerBindings.get(normalizedPos);
        if (binding == null) {
            return false;
        }
        boolean knownDevice = this.towerNetworkSnapshot.bindings().stream()
                .filter(snapshot -> snapshot.binding().anchor().equals(normalizedPos))
                .flatMap(snapshot -> snapshot.devices().stream())
                .anyMatch(snapshot -> snapshot.deviceKey().equals(deviceKey));
        if (!knownDevice) {
            return false;
        }
        this.towerBindings.put(normalizedPos, binding.withDeviceDisabled(deviceKey, disabled));
        invalidateEndpointCache();
        invalidateTowerDomain(TowerNetworkDomainChange.BINDING);
        this.setChanged();
        this.markForClientUpdate();
        return true;
    }

    public boolean setTowerNetworkVirtualDeviceDisabled(
                                                        TowerRuntimeKey ownerTower,
                                                        ResourceLocation dimensionId,
                                                        BlockPos targetPos,
                                                        TowerDeviceKey deviceKey,
                                                        boolean disabled) {
        for (DataDistributionTowerBlockEntity tower : collectTowerCluster()) {
            Level towerLevel = tower.level;
            if (towerLevel != null && ownerTower.dimensionId().equals(towerLevel.dimension().location()) && ownerTower.position().equals(tower.worldPosition) && dimensionId.equals(towerLevel.dimension().location()) && tower.setVirtualDeviceDisabled(
                    targetPos, deviceKey, disabled)) {
                return true;
            }
        }
        return false;
    }

    public void setConnectionMode(@Nullable ConnectionMode connectionMode) {
        ConnectionMode normalizedMode = connectionMode == null ? ConnectionMode.AE_AND_FE : connectionMode;
        if (this.connectionMode == normalizedMode) {
            return;
        }

        this.connectionMode = normalizedMode;
        refreshConnectionTargets();
        invalidateTowerDomain(TowerNetworkDomainChange.MODE);
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
        invalidateTowerDomain(TowerNetworkDomainChange.MODE);
        this.setChanged();
        this.markForClientUpdate();
    }

    public ConnectorBindResult bindTargetFromConnector(BlockPos targetPos) {
        if (this.level == null || this.level.isClientSide()) {
            return ConnectorBindResult.fail(ConnectorBindFailure.UNSUPPORTED);
        }

        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        if (this.worldPosition.equals(normalizedPos)) {
            return ConnectorBindResult.fail(ConnectorBindFailure.SELF_TARGET);
        }
        if (!isWithinTowerCoverage(normalizedPos)) {
            return ConnectorBindResult.fail(ConnectorBindFailure.OUT_OF_RANGE);
        }

        if (isLoadedTowerTarget(normalizedPos)) {
            addTowerBinding(normalizedPos, TowerBindingSource.MANUAL);
            transitionTargetState(normalizedPos, TargetLinkState.BOUND, TargetLinkFailure.NONE, 0);
            invalidateEndpointCache();
            invalidateTowerDomain(TowerNetworkDomainChange.BINDING);
            this.setChanged();
            this.markForClientUpdate();
            return ConnectorBindResult.success(true, false);
        }

        if (!isPointToPointMode()) {
            return ConnectorBindResult.fail(ConnectorBindFailure.NOT_POINT_MODE);
        }

        boolean aeSupported = hasExposedAeNode(normalizedPos);
        boolean feSupported = hasAnyEnergyCapability(normalizedPos);
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

        addTowerBinding(normalizedPos, TowerBindingSource.MANUAL);
        if (getTargetTransferMode(normalizedPos) == TargetTransferMode.DISABLED) {
            this.linkGraph.transition(
                    normalizedPos, TargetLinkState.DISABLED, TargetLinkFailure.NONE, 0);
        } else {
            this.linkGraph.transition(
                    normalizedPos,
                    aeSupported && allowsAeTargets() ? TargetLinkState.PENDING : TargetLinkState.BOUND,
                    TargetLinkFailure.NONE,
                    0);
        }

        invalidateEndpointCache();
        invalidateClusterCache();
        invalidateTowerDomain(TowerNetworkDomainChange.BINDING);
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
        TowerChannelOverview channels = towerNetworkChannelOverview();
        return Math.toIntExact(Math.min(
                Integer.MAX_VALUE,
                Math.addExact(
                        channels.physicalUsage(),
                        channels.virtualUsage())));
    }

    public int getMaxChannelCount() {
        TowerChannelOverview channels = towerNetworkChannelOverview();
        return Math.toIntExact(Math.min(
                Integer.MAX_VALUE,
                channels.totalCapacity().orElse(Integer.MAX_VALUE)));
    }

    public long getPhysicalChannelCount() {
        return towerNetworkChannelOverview().physicalUsage();
    }

    public long getVirtualChannelCount() {
        return towerNetworkChannelOverview().virtualUsage();
    }

    public OptionalLong getRemainingChannelCount() {
        return towerNetworkChannelOverview().remainingCapacity();
    }

    public long getAvailableFeForUi() {
        return towerNetworkEnergySnapshotForUi().stored();
    }

    public long getEnergyCapacityForUi() {
        return towerNetworkEnergySnapshotForUi().sourceCapacity();
    }

    public boolean isTowerNetworkOnlineForUi() {
        for (DataDistributionTowerBlockEntity tower : collectTowerCluster()) {
            if (tower.isNetworkNodeOnline()) {
                return true;
            }
        }
        return false;
    }

    private TowerChannelOverview towerNetworkChannelOverview() {
        Set<TowerNetworkDomain> domains = Collections.newSetFromMap(new IdentityHashMap<>());
        long totalCapacity = 0;
        long physicalUsage = 0;
        long virtualUsage = 0;
        long remainingCapacity = 0;
        boolean finite = true;
        for (DataDistributionTowerBlockEntity tower : collectTowerCluster()) {
            TowerNetworkDomain domain = tower.registeredTowerDomain;
            if (domain == null || !tower.isTowerNetworkActive() || !domains.add(domain)) {
                continue;
            }
            TowerChannelOverview channels = tower.towerNetworkSnapshot.channels();
            physicalUsage = Math.addExact(physicalUsage, channels.physicalUsage());
            virtualUsage = Math.addExact(virtualUsage, channels.virtualUsage());
            if (channels.totalCapacity().isEmpty() || channels.remainingCapacity().isEmpty()) {
                finite = false;
            } else if (finite) {
                totalCapacity = Math.addExact(totalCapacity, channels.totalCapacity().orElseThrow());
                remainingCapacity = Math.addExact(remainingCapacity, channels.remainingCapacity().orElseThrow());
            }
        }
        if (domains.isEmpty()) {
            return this.towerNetworkSnapshot.channels();
        }
        return new TowerChannelOverview(
                finite ? OptionalLong.of(totalCapacity) : OptionalLong.empty(),
                physicalUsage,
                virtualUsage,
                finite ? OptionalLong.of(remainingCapacity) : OptionalLong.empty());
    }

    private TowerEnergyAccessSnapshot towerNetworkEnergySnapshotForUi() {
        Set<TowerNetworkDomain> domains = Collections.newSetFromMap(new IdentityHashMap<>());
        long stored = 0;
        long sourceCapacity = 0;
        long receivable = 0;
        boolean canExtract = false;
        boolean canReceive = false;
        boolean hasRegisteredDomain = false;
        List<DataDistributionTowerBlockEntity> towers = collectTowerCluster();
        for (DataDistributionTowerBlockEntity tower : towers) {
            stored = saturatingAdd(stored, tower.bufferedTransferEnergy);
            sourceCapacity = saturatingAdd(sourceCapacity, tower.bufferedTransferEnergy);
            TowerNetworkDomain domain = tower.registeredTowerDomain;
            hasRegisteredDomain |= domain != null;
            if (domain == null || !tower.isTowerNetworkActive() || !domains.add(domain)) {
                continue;
            }
            TowerEnergyAccessSnapshot snapshot = domain.energySnapshot(tower.towerKey(), null);
            stored = saturatingAdd(stored, snapshot.stored());
            sourceCapacity = saturatingAdd(sourceCapacity, snapshot.sourceCapacity());
            receivable = saturatingAdd(receivable, snapshot.receivable());
            canExtract |= snapshot.canExtract();
            canReceive |= snapshot.canReceive();
        }
        if (domains.isEmpty() && !hasRegisteredDomain) {
            stored = saturatingAdd(stored, this.energyDistributor.getTotalExtractableEnergy(null));
            sourceCapacity = saturatingAdd(sourceCapacity, this.energyDistributor.getTotalEnergyCapacity(null));
            receivable = this.energyDistributor.getTotalReceivableEnergy(null);
            canExtract = stored > 0;
            canReceive = receivable > 0;
        }
        return new TowerEnergyAccessSnapshot(stored, sourceCapacity, receivable, canExtract, canReceive);
    }

    private TowerRuntimeKey displayTowerKey() {
        Level currentLevel = this.level;
        if (currentLevel == null) {
            throw new IllegalStateException("Cannot resolve a tower display owner before the level is assigned");
        }
        return new TowerRuntimeKey(currentLevel.dimension().location(), this.worldPosition);
    }

    public TargetTransferInfo getTargetTransferInfo(BlockPos targetPos) {
        if (this.level == null) {
            return TargetTransferInfo.EMPTY;
        }

        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        Optional<TowerBindingRuntimeSnapshot> bindingSnapshot = this.towerNetworkSnapshot.bindings().stream()
                .filter(binding -> binding.binding().anchor().equals(normalizedPos))
                .findFirst();
        if (bindingSnapshot.isPresent()) {
            TowerBindingRuntimeSnapshot snapshot = bindingSnapshot.orElseThrow();
            return new TargetTransferInfo(
                    Math.toIntExact(snapshot.grantedChannels()),
                    !snapshot.devices().isEmpty(),
                    snapshot.capacityFe() > 0 || snapshot.canExtractFe() || snapshot.canReceiveFe(),
                    snapshot.storedFe(),
                    snapshot.capacityFe(),
                    snapshot.canExtractFe(),
                    snapshot.canReceiveFe(),
                    Math.toIntExact(snapshot.requestedChannels()),
                    snapshot.state(),
                    snapshot.failure(),
                    displayTowerKey(),
                    snapshot.binding().dimensionId(),
                    snapshot.binding().anchor(),
                    null);
        }
        long stored = 0L;
        long capacity = 0L;
        boolean canExtract = false;
        boolean canReceive = false;
        boolean hasEnergy = false;

        for (Direction direction : Direction.values()) {
            TargetEnergySnapshot snapshot = readTargetEnergySnapshot(normalizedPos, direction);
            if (snapshot == null) {
                continue;
            }
            hasEnergy = true;
            stored = saturatingAdd(stored, snapshot.stored());
            capacity = saturatingAdd(capacity, snapshot.capacity());
            canExtract |= snapshot.canExtract();
            canReceive |= snapshot.canReceive();
        }

        TargetEnergySnapshot internalSnapshot = readTargetEnergySnapshot(normalizedPos, null);
        if (internalSnapshot != null) {
            hasEnergy = true;
            stored = saturatingAdd(stored, internalSnapshot.stored());
            capacity = saturatingAdd(capacity, internalSnapshot.capacity());
            canExtract |= internalSnapshot.canExtract();
            canReceive |= internalSnapshot.canReceive();
        }

        boolean hasAeTarget = hasExposedAeNode(normalizedPos);
        return new TargetTransferInfo(
                0,
                hasAeTarget,
                hasEnergy,
                stored,
                capacity,
                canExtract,
                canReceive,
                0,
                TowerVirtualDeviceState.ALLOCATED,
                "",
                displayTowerKey(),
                this.level.dimension().location(),
                normalizedPos,
                null);
    }

    /**
     * Reads one external FE capability for a display-only target snapshot.
     *
     * <p>
     * An invalid or failing third-party capability is omitted from this one display update. Transfer code keeps its
     * own endpoint resolution and is not affected.
     * </p>
     */
    @Nullable
    private TargetEnergySnapshot readTargetEnergySnapshot(BlockPos targetPos, @Nullable Direction side) {
        TargetEnergyFailureKey failureKey = new TargetEnergyFailureKey(targetPos, side);
        IEnergyStorage storage;
        try {
            storage = getEnergyStorageAt(targetPos, side);
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            logTargetEnergySnapshotFailure(failureKey, "<capability lookup>", "looking up the FE capability", exception);
            return null;
        }

        if (storage == null) {
            this.targetEnergySnapshotFailureLogTicks.remove(failureKey);
            return null;
        }

        String storageType = storage.getClass().getName();
        try {
            int stored = storage.getEnergyStored();
            int capacity = storage.getMaxEnergyStored();
            if (stored < 0 || capacity < stored) {
                logInvalidTargetEnergySnapshot(failureKey, storageType, stored, capacity);
                return null;
            }

            TargetEnergySnapshot snapshot = new TargetEnergySnapshot(
                    stored, capacity, storage.canExtract(), canReceiveEnergy(storage));
            this.targetEnergySnapshotFailureLogTicks.remove(failureKey);
            return snapshot;
        } catch (Throwable exception) {
            ThrowableIsolation.rethrowIfFatal(exception);
            logTargetEnergySnapshotFailure(failureKey, storageType, "reading the FE snapshot", exception);
            return null;
        }
    }

    private void logInvalidTargetEnergySnapshot(TargetEnergyFailureKey failureKey, String storageType,
                                                int stored, int capacity) {
        ServerLevel level = claimTargetEnergySnapshotFailureLog(failureKey);
        if (level == null) {
            return;
        }
        LOGGER.error(
                "Data Distribution Tower at {} in {} skipped invalid FE display snapshot for target {} side {} storage {}: stored={} capacity={}",
                this.worldPosition,
                level.dimension().location(),
                failureKey.targetPos(),
                failureKey.side(),
                storageType,
                stored,
                capacity);
    }

    private void logTargetEnergySnapshotFailure(TargetEnergyFailureKey failureKey, String storageType,
                                                String operation, Throwable exception) {
        ServerLevel level = claimTargetEnergySnapshotFailureLog(failureKey);
        if (level == null) {
            return;
        }
        LOGGER.error(
                "Data Distribution Tower at {} in {} skipped FE display snapshot for target {} side {} storage {} while {}",
                this.worldPosition,
                level.dimension().location(),
                failureKey.targetPos(),
                failureKey.side(),
                storageType,
                operation,
                exception);
    }

    @Nullable
    private ServerLevel claimTargetEnergySnapshotFailureLog(TargetEnergyFailureKey failureKey) {
        if (!(this.level instanceof ServerLevel level)) {
            return null;
        }

        long gameTime = level.getGameTime();
        Long lastLogTick = this.targetEnergySnapshotFailureLogTicks.get(failureKey);
        if (lastLogTick != null && gameTime - lastLogTick < TARGET_ENERGY_SNAPSHOT_FAILURE_LOG_INTERVAL_TICKS) {
            return null;
        }
        this.targetEnergySnapshotFailureLogTicks.put(failureKey, gameTime);
        return level;
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
        return getBoundTargetSummaries(Integer.MAX_VALUE).size();
    }

    public List<BoundTargetSummary> getBoundTargetSummaries(int maxEntries) {
        if (maxEntries <= 0) {
            return List.of();
        }
        List<BoundTargetSummary> summaries = towerNetworkTargetSummariesForCurrentTick();
        if (maxEntries >= summaries.size()) {
            return summaries;
        }
        return List.copyOf(summaries.subList(0, maxEntries));
    }

    private List<BoundTargetSummary> towerNetworkTargetSummariesForCurrentTick() {
        Level currentLevel = this.level;
        if (currentLevel == null) {
            return localBoundTargetSummariesForCurrentTick();
        }

        long gameTime = currentLevel.getGameTime();
        long stateRevision = towerNetworkDisplayStateRevision();
        if (this.cachedTowerNetworkTargetSummariesTick == gameTime && this.cachedTowerNetworkTargetSummariesRevision == stateRevision) {
            return this.cachedTowerNetworkTargetSummaries;
        }

        ArrayList<BoundTargetSummary> summaries = new ArrayList<>();
        for (DataDistributionTowerBlockEntity tower : collectTowerCluster()) {
            summaries.addAll(tower.localBoundTargetSummariesForCurrentTick());
        }
        this.cachedTowerNetworkTargetSummariesTick = gameTime;
        this.cachedTowerNetworkTargetSummariesRevision = towerNetworkDisplayStateRevision();
        this.cachedTowerNetworkTargetSummaries = List.copyOf(summaries);
        return this.cachedTowerNetworkTargetSummaries;
    }

    /**
     * Resolves this tower's complete target snapshot at most once per level tick and local display-state revision.
     */
    private List<BoundTargetSummary> localBoundTargetSummariesForCurrentTick() {
        Level currentLevel = this.level;
        if (currentLevel == null) {
            return resolveBoundTargetSummaries();
        }

        long gameTime = currentLevel.getGameTime();
        long stateRevision = this.targetDisplayStateRevision;
        if (this.cachedBoundTargetSummariesTick == gameTime && this.cachedBoundTargetSummariesRevision == stateRevision) {
            return this.cachedBoundTargetSummaries;
        }

        List<BoundTargetSummary> summaries = resolveBoundTargetSummaries();
        this.cachedBoundTargetSummariesTick = gameTime;
        this.cachedBoundTargetSummariesRevision = this.targetDisplayStateRevision;
        this.cachedBoundTargetSummaries = summaries;
        return summaries;
    }

    /**
     * Builds the immutable complete target snapshot from current discovery and network-domain state.
     */
    private List<BoundTargetSummary> resolveBoundTargetSummaries() {
        List<BoundTargetSummary> baseSummaries = this.targetDisplayResolver.boundTargetSummaries(Integer.MAX_VALUE);
        Map<DisplayTargetKey, List<BoundTargetSummary>> baseByLocation = new LinkedHashMap<>();
        for (BoundTargetSummary summary : baseSummaries) {
            DisplayTargetKey key = new DisplayTargetKey(summary.dimensionId(), summary.pos());
            baseByLocation.computeIfAbsent(key, ignored -> new ArrayList<>()).add(summary);
        }

        ArrayList<BoundTargetSummary> result = new ArrayList<>();
        Set<DisplayTargetKey> consumedBindings = new HashSet<>();
        for (TowerBindingRuntimeSnapshot bindingSnapshot : this.towerNetworkSnapshot.bindings()) {
            TowerBinding binding = bindingSnapshot.binding();
            DisplayTargetKey bindingKey = new DisplayTargetKey(binding.dimensionId(), binding.anchor());
            consumedBindings.add(bindingKey);
            if (bindingSnapshot.devices().isEmpty()) {
                List<BoundTargetSummary> summaries = baseByLocation.get(bindingKey);
                if (summaries == null || summaries.isEmpty()) {
                    result.add(createBindingSummary(bindingSnapshot));
                } else {
                    for (BoundTargetSummary summary : summaries) {
                        result.add(withBindingRuntime(summary, bindingSnapshot));
                    }
                }
            } else {
                for (TowerVirtualDeviceSnapshot device : bindingSnapshot.devices()) {
                    result.add(createDeviceSummary(bindingSnapshot, device));
                }
            }
        }

        for (BoundTargetSummary summary : baseSummaries) {
            if (!consumedBindings.contains(new DisplayTargetKey(summary.dimensionId(), summary.pos()))) {
                result.add(summary);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Adds aggregate binding state to an existing FE or block-display row.
     */
    private BoundTargetSummary withBindingRuntime(
                                                  BoundTargetSummary summary, TowerBindingRuntimeSnapshot bindingSnapshot) {
        TargetTransferInfo info = new TargetTransferInfo(
                Math.toIntExact(bindingSnapshot.grantedChannels()),
                !bindingSnapshot.devices().isEmpty() || summary.transferInfo().hasAeTarget(),
                bindingSnapshot.capacityFe() > 0 || bindingSnapshot.canExtractFe() || bindingSnapshot.canReceiveFe(),
                bindingSnapshot.storedFe(),
                bindingSnapshot.capacityFe(),
                bindingSnapshot.canExtractFe(),
                bindingSnapshot.canReceiveFe(),
                Math.toIntExact(bindingSnapshot.requestedChannels()),
                bindingSnapshot.state(),
                bindingSnapshot.failure(),
                displayTowerKey(),
                bindingSnapshot.binding().dimensionId(),
                bindingSnapshot.binding().anchor(),
                null);
        return new BoundTargetSummary(
                summary.itemId(),
                summary.displayName(),
                summary.count(),
                summary.dimensionId(),
                summary.pos(),
                summary.kind(),
                bindingSnapshot.state() == TowerVirtualDeviceState.DISABLED ? TargetTransferMode.DISABLED : TargetTransferMode.AUTO,
                info);
    }

    /**
     * Creates a fallback row for an unloaded, conflicting, or FE-only binding.
     */
    private BoundTargetSummary createBindingSummary(TowerBindingRuntimeSnapshot bindingSnapshot) {
        TowerBinding binding = bindingSnapshot.binding();
        DeviceDisplay display = resolveBindingDisplay(binding);
        TargetTransferInfo info = new TargetTransferInfo(
                Math.toIntExact(bindingSnapshot.grantedChannels()),
                !bindingSnapshot.devices().isEmpty(),
                bindingSnapshot.capacityFe() > 0 || bindingSnapshot.canExtractFe() || bindingSnapshot.canReceiveFe(),
                bindingSnapshot.storedFe(),
                bindingSnapshot.capacityFe(),
                bindingSnapshot.canExtractFe(),
                bindingSnapshot.canReceiveFe(),
                Math.toIntExact(bindingSnapshot.requestedChannels()),
                bindingSnapshot.state(),
                bindingSnapshot.failure(),
                displayTowerKey(),
                binding.dimensionId(),
                binding.anchor(),
                null);
        return new BoundTargetSummary(
                display.itemId(),
                display.displayName(),
                1,
                binding.dimensionId(),
                binding.anchor(),
                info.hasAeTarget() ? TargetKind.AE : TargetKind.FE,
                bindingSnapshot.state() == TowerVirtualDeviceState.DISABLED ? TargetTransferMode.DISABLED : TargetTransferMode.AUTO,
                info);
    }

    /**
     * Creates one protocol row for one virtual AE node, including logical-node grouping.
     */
    private BoundTargetSummary createDeviceSummary(
                                                   TowerBindingRuntimeSnapshot bindingSnapshot, TowerVirtualDeviceSnapshot device) {
        TowerDeviceKey deviceKey = device.deviceKey();
        boolean logical = deviceKey.position() == null;
        ResourceLocation displayDimension = logical ? bindingSnapshot.binding().dimensionId() : deviceKey.dimensionId();
        BlockPos displayPosition = logical ? bindingSnapshot.binding().anchor() : deviceKey.position();
        DeviceDisplay display = resolveVirtualDeviceDisplay(device);
        TargetTransferInfo info = new TargetTransferInfo(
                device.grantedChannels(),
                true,
                device.capacityFe() > 0 || device.canExtractFe() || device.canReceiveFe(),
                device.storedFe(),
                device.capacityFe(),
                device.canExtractFe(),
                device.canReceiveFe(),
                device.requestedChannels(),
                device.state(),
                device.failure(),
                displayTowerKey(),
                bindingSnapshot.binding().dimensionId(),
                bindingSnapshot.binding().anchor(),
                deviceKey);
        return new BoundTargetSummary(
                display.itemId(),
                display.displayName(),
                1,
                displayDimension,
                displayPosition,
                TargetKind.AE,
                device.state() == TowerVirtualDeviceState.DISABLED ? TargetTransferMode.DISABLED : TargetTransferMode.AUTO,
                info);
    }

    /**
     * Uses AE's node-provided visual identity so a cable-bus device is not rendered as its host block.
     */
    private DeviceDisplay resolveVirtualDeviceDisplay(TowerVirtualDeviceSnapshot device) {
        Item item = BuiltInRegistries.ITEM.get(device.displayItemId());
        String displayName = device.displayName();
        if (displayName.isBlank()) {
            displayName = item == Items.AIR ? Component.translatable("screen.data_energistics.data_distribution_tower.unknown_device").getString() : item.getDescription().getString();
        }
        if (device.deviceKey().side() >= 0) {
            displayName += " [" + Direction.values()[device.deviceKey().side()].getName() + "]";
        }
        ResourceLocation itemId = item == Items.AIR ? BuiltInRegistries.ITEM.getKey(Items.BARRIER) : device.displayItemId();
        return new DeviceDisplay(itemId, displayName);
    }

    /**
     * Resolves one same-dimension binding anchor without forcing its chunk to load.
     */
    private DeviceDisplay resolveBindingDisplay(TowerBinding binding) {
        Level towerLevel = towerLevel();
        if (!towerLevel.isLoaded(binding.anchor())) {
            return new DeviceDisplay(
                    BuiltInRegistries.ITEM.getKey(Items.BARRIER),
                    Component.translatable("screen.data_energistics.data_distribution_tower.unknown_device").getString());
        }
        BlockState state = towerLevel.getBlockState(binding.anchor());
        BlockEntity blockEntity = towerLevel.getBlockEntity(binding.anchor());
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return new DeviceDisplay(
                    BuiltInRegistries.ITEM.getKey(Items.BARRIER),
                    Component.translatable("screen.data_energistics.data_distribution_tower.unknown_device").getString());
        }
        String displayName = item.getDescription().getString();
        if (blockEntity instanceof Nameable nameable && !nameable.getDisplayName().getString().isBlank()) {
            displayName = nameable.getDisplayName().getString();
        }
        return new DeviceDisplay(BuiltInRegistries.ITEM.getKey(item), displayName);
    }

    private long towerNetworkDisplayStateRevision() {
        long revision = 0xcbf29ce484222325L;
        for (DataDistributionTowerBlockEntity tower : collectTowerCluster()) {
            revision ^= tower.worldPosition.asLong();
            revision *= 0x100000001b3L;
            revision ^= tower.targetDisplayStateRevision;
            revision *= 0x100000001b3L;
        }
        return revision;
    }

    public long getTargetDisplayStateRevision() {
        return towerNetworkDisplayStateRevision();
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
    public static void onChunkLoad(ChunkEvent.Load event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof Level level && !level.isClientSide()) {
            notifyTargetChunkLoaded(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof Level level && !level.isClientSide()) {
            notifyTargetChunkUnloaded(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ensureBound(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TOWER_CHUNK_POSITIONS.clear();
        LOADED_TOWERS.clear();
        TowerGridOwnershipRegistry.clear(event.getServer());
        boundServer = null;
    }

    public static void onPotentialNodeAdded(Level level, BlockPos targetPos) {
        IInWorldGridNodeHost targetNodeHost = level.getCapability(
                AECapabilities.IN_WORLD_GRID_NODE_HOST, targetPos);
        if (targetNodeHost == null) {
            return;
        }

        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, new ChunkPos(targetPos)));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            DataDistributionTowerBlockEntity tower = getLoadedTower(level, towerPos);
            if (tower == null) {
                continue;
            }

            BlockPos normalizedPos = tower.normalizeTargetPos(targetPos);
            boolean trackedTarget = tower.shouldReconnectTrackedTarget(normalizedPos);
            if (!tower.allowsAutomaticRangeConnections() && !trackedTarget) {
                continue;
            }
            if (!trackedTarget && !tower.canAutomaticallyTrackGridLink(normalizedPos)) {
                continue;
            }

            if (!trackedTarget) {
                tower.addTowerBinding(normalizedPos, TowerBindingSource.AUTOMATIC);
                boolean towerPeer = tower.isTowerPeerBinding(normalizedPos);
                tower.transitionTargetState(
                        normalizedPos,
                        towerPeer ? TargetLinkState.BOUND : TargetLinkState.PENDING,
                        TargetLinkFailure.NONE,
                        towerPeer ? 0 : INITIAL_PENDING_DELAY);
                tower.setChanged();
            }
        }
    }

    public static void onPotentialNodeRemoved(Level level, BlockPos targetPos) {
        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, new ChunkPos(targetPos)));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            DataDistributionTowerBlockEntity tower = getLoadedTower(level, towerPos);
            if (tower == null || !tower.isWithinTowerCoverage(targetPos)) {
                continue;
            }

            tower.removeTarget(targetPos);
        }
    }

    private static void notifyTargetChunkLoaded(Level level, ChunkPos targetChunk) {
        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, targetChunk));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            DataDistributionTowerBlockEntity tower = getLoadedTower(level, towerPos);
            if (tower != null) {
                tower.onTargetChunkLoaded(targetChunk);
            }
        }
    }

    private static void notifyTargetChunkUnloaded(Level level, ChunkPos targetChunk) {
        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, targetChunk));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            DataDistributionTowerBlockEntity tower = getLoadedTower(level, towerPos);
            if (tower != null) {
                tower.onTargetChunkUnloaded(targetChunk);
            }
        }
    }

    private void onTargetChunkLoaded(ChunkPos targetChunk) {
        boolean changed = false;
        for (BlockPos targetPos : this.linkGraph.linkedPositions()) {
            if (!new ChunkPos(targetPos).equals(targetChunk) || this.linkGraph.status(targetPos).state() != TargetLinkState.WAITING_TARGET) {
                continue;
            }
            changed |= queueLink(targetPos);
        }
        if (changed) {
            this.setChanged();
        }
        invalidateEndpointCache();
        invalidateTowerDomain(TowerNetworkDomainChange.CHUNK);
    }

    private void onTargetChunkUnloaded(ChunkPos targetChunk) {
        boolean changed = false;
        for (BlockPos targetPos : this.linkGraph.linkedPositions()) {
            if (new ChunkPos(targetPos).equals(targetChunk) && getTargetTransferMode(targetPos) != TargetTransferMode.DISABLED) {
                changed |= scheduleTargetUnavailableRetry(targetPos);
            }
        }
        if (changed) {
            invalidateEndpointCache();
            invalidateClusterCache();
        }
        invalidateEndpointCache();
        invalidateTowerDomain(TowerNetworkDomainChange.CHUNK);
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
            if (this.linkGraph.containsLinked(pos)) {
                continue;
            }
            if (!canAutomaticallyTrackGridLink(pos)) {
                continue;
            }
            addTowerBinding(pos, TowerBindingSource.AUTOMATIC);
            transitionTargetState(
                    pos,
                    isTowerPeerBinding(pos) ? TargetLinkState.BOUND : TargetLinkState.PENDING,
                    TargetLinkFailure.NONE,
                    0);
            added++;
        }
        return added;
    }

    @Override
    public boolean isTowerActive() {
        return this.getMainNode().isActive();
    }

    @Override
    public TowerRuntimeKey towerKey() {
        return new TowerRuntimeKey(towerLevel().dimension().location(), this.worldPosition);
    }

    @Override
    public ServerLevel towerLevel() {
        return (ServerLevel) this.level;
    }

    @Override
    public IGrid towerGrid() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            throw new IllegalStateException("Data distribution tower grid is not ready");
        }
        return grid;
    }

    @Override
    public boolean isTowerNetworkActive() {
        return isTowerActive();
    }

    @Override
    public boolean towerAllowsAe() {
        return allowsAeTargets();
    }

    @Override
    public boolean towerAllowsFe() {
        return allowsFeTargets();
    }

    @Override
    public List<TowerBinding> towerBindings() {
        return this.towerBindings.values().stream()
                .filter(binding -> binding.kind() == TowerBindingKind.TARGET && !isLoadedTowerTarget(binding.anchor()))
                .toList();
    }

    @Override
    public List<TowerEnergyLocation> towerEnergyLocations() {
        ServerLevel level = towerLevel();
        ArrayList<TowerEnergyLocation> locations = new ArrayList<>();
        for (BlockPos position : getCachedEndpoints()) {
            if (level.isLoaded(position)) {
                locations.add(new TowerEnergyLocation(level, position));
            }
        }
        return List.copyOf(locations);
    }

    @Override
    public AENetworkedBlockEntity towerEnergyHost() {
        return this;
    }

    @Override
    public long towerQuarantinedEnergy() {
        return this.quarantinedTransferEnergy;
    }

    @Override
    public void setTowerQuarantinedEnergy(long amount) {
        setQuarantinedTransferEnergy(amount);
    }

    @Override
    public void applyTowerNetworkSnapshot(TowerNetworkTowerSnapshot snapshot) {
        if (snapshot.equals(this.towerNetworkSnapshot)) {
            return;
        }
        this.towerNetworkSnapshot = snapshot;
        for (TowerBindingRuntimeSnapshot binding : snapshot.bindings()) {
            TargetLinkState state = switch (binding.state()) {
                case ALLOCATED -> TargetLinkState.ALLOCATED;
                case WAITING_CHANNEL -> TargetLinkState.WAITING_CHANNEL;
                case CONFLICT -> TargetLinkState.CONFLICT;
                case BRIDGE_ERROR -> TargetLinkState.BRIDGE_ERROR;
                case DISABLED -> TargetLinkState.DISABLED;
                case WAITING_TARGET -> TargetLinkState.WAITING_TARGET;
            };
            TargetLinkFailure failure = switch (binding.state()) {
                case WAITING_CHANNEL -> TargetLinkFailure.CHANNEL_UNAVAILABLE;
                case CONFLICT -> TargetLinkFailure.OWNERSHIP_CONFLICT;
                case BRIDGE_ERROR -> TargetLinkFailure.GRID_SERVICE_REGISTRATION;
                case ALLOCATED, DISABLED, WAITING_TARGET -> TargetLinkFailure.NONE;
            };
            transitionTargetState(binding.binding().anchor(), state, failure, 0);
        }
        incrementTargetDisplayStateRevision();
        this.markForClientUpdate();
    }

    private void syncTowerDomainRegistration() {
        if (!(this.level instanceof ServerLevel)) {
            return;
        }
        IGridNode node = this.getMainNode().getNode();
        if (node == null) {
            unregisterTowerDomain();
            return;
        }
        IGrid grid = node.getGrid();
        TowerNetworkDomain nextDomain = grid.getService(TowerNetworkDomain.class);
        if (nextDomain != null && this.registeredTowerDomain == nextDomain) {
            return;
        }
        if (this.registeredTowerDomain != null) {
            this.registeredTowerDomain.unregisterTower(this);
        }
        this.registeredTowerDomain = nextDomain;
        nextDomain.registerTower(this);
    }

    private void unregisterTowerDomain() {
        if (this.registeredTowerDomain != null) {
            this.registeredTowerDomain.unregisterTower(this);
            this.registeredTowerDomain = null;
        }
    }

    private void invalidateTowerDomain(TowerNetworkDomainChange reason) {
        if (this.registeredTowerDomain != null) {
            this.registeredTowerDomain.invalidate(reason);
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (!(this.level instanceof ServerLevel)) {
            return;
        }

        boolean active = this.getMainNode().isActive();
        boolean activeChanged = active != this.mainNodeActive;
        if (!this.linkReconciliationInProgress && (reason == IGridNodeListener.State.GRID_BOOT || activeChanged)) {
            this.pendingNetworkRecovery = true;
        } else if (!this.linkReconciliationInProgress) {
            wakeWaitingGridTargets();
        }
        this.mainNodeActive = active;
        invalidateEndpointCache();
        invalidateClusterCache();
        if (activeChanged) {
            invalidateTowerDomain(TowerNetworkDomainChange.TOWER);
        }
        syncClientOnlineState();
        requestAeTickWake();
    }

    private void syncClientOnlineState() {
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
        ServerLevel level = towerLevel();
        for (int part = 0; part <= 2; part++) {
            BlockPos partPos = this.worldPosition.above(part);
            BlockState state = level.getBlockState(partPos);
            if (!state.is(DEBlocks.DATA_DISTRIBUTION_TOWER.get()) || !state.hasProperty(DataDistributionTowerBlock.ACTIVE) || state.getValue(DataDistributionTowerBlock.ACTIVE) == active) {
                continue;
            }

            level.setBlock(partPos, state.setValue(DataDistributionTowerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
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
        if (this.verboseRuntimeLoggingEnabled) {
            this.diagnosticMaxExtractEndpoints = Math.max(this.diagnosticMaxExtractEndpoints, endpointCount);
        }
    }

    @Override
    public void recordMaxReceiveEndpoints(int endpointCount) {
        if (this.verboseRuntimeLoggingEnabled) {
            this.diagnosticMaxReceiveEndpoints = Math.max(this.diagnosticMaxReceiveEndpoints, endpointCount);
        }
    }

    @Override
    public void recordSimulatedCacheHit() {
        if (this.verboseRuntimeLoggingEnabled) {
            this.diagnosticSimulatedCacheHits++;
        }
    }

    @Override
    public void recordSimulatedCacheMiss() {
        if (this.verboseRuntimeLoggingEnabled) {
            this.diagnosticSimulatedCacheMisses++;
        }
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
        for (BlockPos pos : getTrackedTargetPositions()) {
            addLoadedEndpoint(pos, endpoints, aeDisplayTargets);
        }
        if (allowsAutomaticRangeConnections()) {
            for (BlockEntity blockEntity : getNearbyBlockEntities()) {
                addLoadedEndpoint(blockEntity.getBlockPos(), endpoints, aeDisplayTargets);
            }
        }

        this.cachedEndpoints = List.copyOf(endpoints);
        this.cachedAeDisplayTargets = List.copyOf(aeDisplayTargets);
        this.endpointCacheValid = true;
    }

    private void addLoadedEndpoint(BlockPos targetPos,
                                   Set<BlockPos> endpoints,
                                   Set<BlockPos> aeDisplayTargets) {
        if (this.level == null) {
            return;
        }
        BlockPos pos = normalizeTargetPos(targetPos);
        if (!this.level.isLoaded(pos) || isTowerBlock(pos) || this.level.getBlockState(pos).isAir()) {
            return;
        }
        BlockEntity blockEntity = this.level.getBlockEntity(pos);
        if (allowsFeTargets() && targetAllowsFe(pos) && hasAnyEnergyCapability(pos)) {
            endpoints.add(pos);
        }
        if (allowsAeTargets() && targetAllowsAe(pos) && this.targetDisplayResolver.hasDisplayableAeTarget(pos, blockEntity)) {
            aeDisplayTargets.add(pos);
        }
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
        return this.level != null && this.level.getBlockState(pos).is(DEBlocks.DATA_DISTRIBUTION_TOWER.get());
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

            for (DataDistributionTowerBlockEntity nearbyTower : tower.loadedPeerTowers()) {
                if (visited.add(nearbyTower.worldPosition)) {
                    queue.addLast(nearbyTower);
                }
            }
        }

        towers.sort((left, right) -> compareBlockPos(left.worldPosition, right.worldPosition));
        this.cachedTowerCluster = List.copyOf(towers);
        this.lastClusterCacheTick = gameTime;
        return this.cachedTowerCluster;
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
        TowerNetworkDomain domain = this.registeredTowerDomain;
        if (domain == null) {
            return this.energyDistributor.distributeEnergyInRange(amount, simulate, excludedPos);
        }
        return domain.insertEnergy(towerKey(), amount, simulate, excludedPos);
    }

    private boolean canReceiveEnergy(@Nullable IEnergyStorage storage) {
        return this.energyEndpointResolver.canReceiveEnergy(storage);
    }

    private int extractEnergyFromRange(int amount, boolean simulate, @Nullable BlockPos excludedPos) {
        TowerNetworkDomain domain = this.registeredTowerDomain;
        if (domain == null) {
            return this.energyDistributor.extractEnergyFromRange(amount, simulate, excludedPos);
        }

        long bufferedExtracted = Math.min(amount, this.bufferedTransferEnergy);
        if (!simulate && bufferedExtracted > 0) {
            setBufferedTransferEnergy(this.bufferedTransferEnergy - bufferedExtracted);
        }
        long sharedExtracted = domain.extractEnergy(towerKey(), amount - bufferedExtracted, simulate, excludedPos);
        return clampStoredAmount(saturatingAdd(bufferedExtracted, sharedExtracted));
    }

    private long getTotalExtractableEnergy(@Nullable BlockPos excludedPos) {
        TowerEnergyAccessSnapshot sharedSnapshot = sharedEnergySnapshot(excludedPos);
        return sharedSnapshot == null ? this.energyDistributor.getTotalExtractableEnergy(excludedPos) : saturatingAdd(
                this.bufferedTransferEnergy, sharedSnapshot.stored());
    }

    private long getTotalReceivableEnergy(@Nullable BlockPos excludedPos) {
        TowerEnergyAccessSnapshot sharedSnapshot = sharedEnergySnapshot(excludedPos);
        return sharedSnapshot == null ? this.energyDistributor.getTotalReceivableEnergy(excludedPos) : sharedSnapshot.receivable();
    }

    private boolean hasAnyReceiver(@Nullable BlockPos excludedPos) {
        TowerEnergyAccessSnapshot sharedSnapshot = sharedEnergySnapshot(excludedPos);
        return sharedSnapshot == null ? this.energyDistributor.hasAnyReceiver(excludedPos) : sharedSnapshot.canReceive();
    }

    private boolean hasAnySource(@Nullable BlockPos excludedPos) {
        TowerEnergyAccessSnapshot sharedSnapshot = sharedEnergySnapshot(excludedPos);
        return sharedSnapshot == null ? this.energyDistributor.hasAnySource(excludedPos) : this.bufferedTransferEnergy > 0 || sharedSnapshot.canExtract();
    }

    @Nullable
    private TowerEnergyAccessSnapshot sharedEnergySnapshot(@Nullable BlockPos excludedPos) {
        TowerNetworkDomain domain = this.registeredTowerDomain;
        return domain == null ? null : domain.energySnapshot(towerKey(), excludedPos);
    }

    private boolean queueLink(BlockPos targetPos) {
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        TowerBinding binding = this.towerBindings.get(normalizedPos);
        if (binding == null) {
            return false;
        }

        if (binding.kind() == TowerBindingKind.TOWER_PEER || isLoadedTowerTarget(normalizedPos)) {
            boolean promoted = binding.kind() != TowerBindingKind.TOWER_PEER;
            if (promoted || !binding.enabled()) {
                this.towerBindings.put(
                        normalizedPos,
                        binding.withKind(TowerBindingKind.TOWER_PEER).withEnabled(true));
                this.targetTransferModes.remove(normalizedPos);
                this.setChanged();
            }
            if (!isLoadedTowerTarget(normalizedPos)) {
                return scheduleTargetUnavailableRetry(normalizedPos);
            }
            boolean changed = transitionTargetState(
                    normalizedPos, TargetLinkState.BOUND, TargetLinkFailure.NONE, 0);
            if (changed || promoted) {
                invalidateTowerDomain(TowerNetworkDomainChange.BINDING);
                invalidateConnectedTowerNetwork();
            }
            return changed;
        }
        if (getTargetTransferMode(normalizedPos) == TargetTransferMode.DISABLED) {
            return false;
        }
        if (!allowsAeTargets()) {
            return false;
        }

        boolean queued = transitionTargetState(
                normalizedPos, TargetLinkState.PENDING, TargetLinkFailure.NONE, 0);
        if (queued) {
            invalidateTowerDomain(TowerNetworkDomainChange.BINDING);
            requestAeTickWake();
        }
        return queued;
    }

    private boolean transitionTargetState(BlockPos targetPos, TargetLinkState state, TargetLinkFailure failure,
                                          int retryTicks) {
        TargetLinkStatus previousStatus = this.linkGraph.status(targetPos);
        boolean changed = this.linkGraph.transition(targetPos, state, failure, retryTicks);
        if (changed && (previousStatus.state() != state || previousStatus.failure() != failure)) {
            incrementTargetDisplayStateRevision();
        }
        return changed;
    }

    private boolean scheduleTargetUnavailableRetry(BlockPos targetPos) {
        TargetLinkStatus previousStatus = this.linkGraph.status(targetPos);
        boolean changed = this.linkGraph.scheduleRetry(
                targetPos,
                TargetLinkState.WAITING_TARGET,
                TargetLinkFailure.TARGET_UNAVAILABLE,
                LINK_RETRY_INITIAL_DELAY_TICKS,
                LINK_RETRY_MAX_DELAY_TICKS);
        if (changed && (previousStatus.state() != TargetLinkState.WAITING_TARGET || previousStatus.failure() != TargetLinkFailure.TARGET_UNAVAILABLE)) {
            incrementTargetDisplayStateRevision();
        }
        if (changed) {
            requestAeTickWake();
        }
        return changed;
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
        return this.pendingNetworkRecovery || this.pendingRangeRefresh || this.rangeScanCursor != null || this.linkGraph.hasRetryableTargets() || this.bufferedTransferEnergy > 0 || allowsAutomaticRangeConnections();
    }

    @Override
    public void cleanupInvalidDisplayTargets() {
        if (this.level == null) {
            return;
        }

        LinkedHashSet<BlockPos> invalidPositions = new LinkedHashSet<>();
        for (BlockPos pos : this.linkGraph.linkedPositions()) {
            if (this.level.isLoaded(pos) && this.level.getBlockState(pos).isAir()) {
                invalidPositions.add(pos);
            }
        }
        for (BlockPos pos : this.targetTransferModes.keySet()) {
            if (this.level.isLoaded(pos) && this.level.getBlockState(pos).isAir()) {
                invalidPositions.add(pos);
            }
        }

        for (BlockPos pos : invalidPositions) {
            removeTarget(pos);
        }
    }

    private void removeTarget(BlockPos targetPos) {
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        TowerBinding binding = this.towerBindings.get(normalizedPos);
        List<DataDistributionTowerBlockEntity> previousNetwork = binding != null && binding.kind() == TowerBindingKind.TOWER_PEER ? collectTowerCluster() : List.of();
        transitionTargetState(normalizedPos, TargetLinkState.INVALID, TargetLinkFailure.NONE, 0);
        this.linkGraph.removeLinked(normalizedPos);
        this.towerBindings.remove(normalizedPos);
        this.targetTransferModes.remove(normalizedPos);
        this.invalidateEndpointCache();
        this.invalidateClusterCache();
        invalidateTowerNetworkTopology(previousNetwork);
        invalidateTowerDomain(TowerNetworkDomainChange.BINDING);
        this.setChanged();
    }

    private void addTowerBinding(BlockPos targetPos, TowerBindingSource source) {
        if (this.level == null) {
            throw new IllegalStateException("Cannot bind a tower target without a level");
        }
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        TowerBinding existing = this.towerBindings.get(normalizedPos);
        TowerBindingKind bindingKind = isLoadedTowerTarget(normalizedPos) ? TowerBindingKind.TOWER_PEER : existing == null ? TowerBindingKind.TARGET : existing.kind();
        if (existing != null && (existing.source() == TowerBindingSource.MANUAL || source == TowerBindingSource.AUTOMATIC)) {
            if (existing.kind() != bindingKind || bindingKind == TowerBindingKind.TOWER_PEER && !existing.enabled()) {
                this.towerBindings.put(
                        normalizedPos,
                        existing.withKind(bindingKind).withEnabled(true));
                this.targetTransferModes.remove(normalizedPos);
                invalidateTowerDomain(TowerNetworkDomainChange.BINDING);
                invalidateConnectedTowerNetwork();
                this.setChanged();
            }
            return;
        }

        boolean enabled = bindingKind == TowerBindingKind.TOWER_PEER || getTargetTransferMode(normalizedPos) != TargetTransferMode.DISABLED;
        Set<TowerDeviceKey> disabledDevices = existing == null ? Set.of() : existing.disabledDeviceKeys();
        TowerBinding binding = new TowerBinding(
                this.level.dimension().location(),
                normalizedPos,
                bindingKind,
                source,
                this.nextBindingFifoSequence,
                enabled,
                disabledDevices);
        this.nextBindingFifoSequence = Math.incrementExact(this.nextBindingFifoSequence);
        this.towerBindings.put(normalizedPos, binding);
        this.linkGraph.addLinked(normalizedPos);
        if (bindingKind == TowerBindingKind.TOWER_PEER) {
            invalidateConnectedTowerNetwork();
        }
        invalidateTowerDomain(TowerNetworkDomainChange.BINDING);
    }

    private boolean shouldReconnectTrackedTarget(BlockPos targetPos) {
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        return this.linkGraph.containsLinked(normalizedPos);
    }

    private boolean requeuePersistedLinks() {
        if (this.linkGraph.linkedPositions().isEmpty()) {
            return false;
        }

        this.linkReconciliationInProgress = true;
        try {
            resetPersistedLinkRuntimeState();
            boolean changed = false;
            for (BlockPos pos : this.linkGraph.linkedPositions()) {
                if (getTargetTransferMode(pos) == TargetTransferMode.DISABLED) {
                    continue;
                }
                if (isTowerPeerBinding(pos) || allowsAeTargets()) {
                    changed |= queueLink(pos);
                }
            }
            return changed;
        } finally {
            this.linkReconciliationInProgress = false;
        }
    }

    private void resetPersistedLinkRuntimeState() {
        this.linkGraph.resetRuntimeState();
        for (BlockPos pos : this.linkGraph.linkedPositions()) {
            if (getTargetTransferMode(pos) == TargetTransferMode.DISABLED) {
                transitionTargetState(pos, TargetLinkState.DISABLED, TargetLinkFailure.NONE, 0);
            }
        }
        this.invalidateEndpointCache();
        this.invalidateClusterCache();
    }

    private boolean enqueuePersistedLinkReconciliation() {
        if (this.linkGraph.linkedPositions().isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (BlockPos pos : List.copyOf(this.linkGraph.linkedPositions())) {
            if (this.linkGraph.status(pos).state() != TargetLinkState.BOUND) {
                continue;
            }
            changed |= queueLink(pos);
        }
        if (changed) {
            this.setChanged();
        }
        return changed;
    }

    private void wakeWaitingGridTargets() {
        for (BlockPos targetPos : this.linkGraph.linkedPositions()) {
            TargetLinkState state = this.linkGraph.status(targetPos).state();
            if (state == TargetLinkState.WAITING_CHANNEL || state == TargetLinkState.CONFLICT || state == TargetLinkState.BRIDGE_ERROR) {
                queueLink(targetPos);
            }
        }
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
        if (!level.isLoaded(pos)) {
            return List.of();
        }
        IInWorldGridNodeHost nodeHost = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos);
        if (nodeHost == null) {
            return List.of();
        }
        return collectConnectableNodes(nodeHost);
    }

    /**
     * Collects every distinct node reachable from the six faces of one capability-authorized AE host.
     *
     * <p>
     * The standard exposed node remains authoritative. When a face has no physical exposure, the typed cable-bus
     * bridge may supply its mounted device node for virtual binding without changing AE's physical connection rules.
     * </p>
     *
     * @param nodeHost host returned by the direction-neutral AE capability query
     * @return immutable nodes in AE direction iteration order, de-duplicated by node identity
     */
    static List<IGridNode> collectConnectableNodes(IInWorldGridNodeHost nodeHost) {
        Set<IGridNode> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<IGridNode> orderedNodes = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            IGridNode exposedNode = nodeHost.getGridNode(direction);
            addConnectableNode(exposedNode, nodes, orderedNodes);
            if (exposedNode == null && nodeHost instanceof TowerMountedGridNodeHost mountedNodeHost) {
                addConnectableNode(mountedNodeHost.dataEnergistics$mountedGridNode(direction), nodes, orderedNodes);
            }
        }

        return List.copyOf(orderedNodes);
    }

    private static void addConnectableNode(@Nullable IGridNode node, Set<IGridNode> nodes,
                                           List<IGridNode> orderedNodes) {
        if (node != null && nodes.add(node)) {
            orderedNodes.add(node);
        }
    }

    private boolean isTowerPeerBinding(BlockPos targetPos) {
        TowerBinding binding = this.towerBindings.get(normalizeTargetPos(targetPos));
        return binding != null && binding.kind() == TowerBindingKind.TOWER_PEER;
    }

    private boolean isLoadedTowerTarget(BlockPos targetPos) {
        return this.level != null && getLoadedTower(this.level, targetPos) != null;
    }

    private List<DataDistributionTowerBlockEntity> loadedPeerTowers() {
        Level currentLevel = this.level;
        if (currentLevel == null) {
            return List.of();
        }

        LinkedHashSet<DataDistributionTowerBlockEntity> peers = new LinkedHashSet<>();
        for (TowerBinding binding : this.towerBindings.values()) {
            if (binding.kind() != TowerBindingKind.TOWER_PEER) {
                continue;
            }
            DataDistributionTowerBlockEntity peer = getLoadedTower(currentLevel, binding.anchor());
            if (peer != null) {
                peers.add(peer);
            }
        }

        Map<BlockPos, DataDistributionTowerBlockEntity> loadedTowers = LOADED_TOWERS.get(currentLevel);
        if (loadedTowers != null) {
            for (DataDistributionTowerBlockEntity candidate : List.copyOf(loadedTowers.values())) {
                if (candidate == this) {
                    continue;
                }
                TowerBinding incomingBinding = candidate.towerBindings.get(this.worldPosition);
                if (incomingBinding != null && incomingBinding.kind() == TowerBindingKind.TOWER_PEER) {
                    peers.add(candidate);
                }
            }
        }

        ArrayList<DataDistributionTowerBlockEntity> orderedPeers = new ArrayList<>(peers);
        orderedPeers.sort((left, right) -> compareBlockPos(left.worldPosition, right.worldPosition));
        return List.copyOf(orderedPeers);
    }

    private void invalidateConnectedTowerNetwork() {
        invalidateClusterCache();
        invalidateTowerNetworkTopology(collectTowerCluster());
    }

    private static void invalidateTowerNetworkTopology(List<DataDistributionTowerBlockEntity> towers) {
        for (DataDistributionTowerBlockEntity tower : towers) {
            tower.invalidateClusterCache();
            tower.incrementTargetDisplayStateRevision();
            tower.markForClientUpdate();
        }
    }

    private void registerLoadedTower() {
        Level currentLevel = this.level;
        if (currentLevel != null) {
            LOADED_TOWERS.computeIfAbsent(currentLevel, ignored -> new LinkedHashMap<>())
                    .put(this.worldPosition.immutable(), this);
            invalidateConnectedTowerNetwork();
        }
    }

    private void unregisterLoadedTower() {
        Level currentLevel = this.level;
        if (currentLevel == null) {
            return;
        }
        Map<BlockPos, DataDistributionTowerBlockEntity> loadedTowers = LOADED_TOWERS.get(currentLevel);
        if (loadedTowers == null || loadedTowers.get(this.worldPosition) != this) {
            return;
        }
        List<DataDistributionTowerBlockEntity> previousNetwork = collectTowerCluster();
        loadedTowers.remove(this.worldPosition);
        if (loadedTowers.isEmpty()) {
            LOADED_TOWERS.remove(currentLevel);
        }
        invalidateTowerNetworkTopology(previousNetwork);
    }

    @Nullable
    private static DataDistributionTowerBlockEntity getLoadedTower(Level level, BlockPos towerPos) {
        if (!level.isLoaded(towerPos)) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(towerPos);
        return blockEntity instanceof DataDistributionTowerBlockEntity tower ? tower : null;
    }

    private static void invalidateNearbyCaches(Level level, BlockPos changedPos) {
        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, new ChunkPos(changedPos)));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            DataDistributionTowerBlockEntity tower = getLoadedTower(level, towerPos);
            if (tower == null || !tower.isWithinTowerCoverage(changedPos)) {
                continue;
            }

            tower.invalidateEndpointCache();
            tower.invalidateTowerDomain(TowerNetworkDomainChange.CAPABILITY);
        }
    }

    private static void ensureBound(@Nullable MinecraftServer server) {
        if (server == null) {
            TOWER_CHUNK_POSITIONS.clear();
            LOADED_TOWERS.clear();
            boundServer = null;
            return;
        }

        if (boundServer != server) {
            TOWER_CHUNK_POSITIONS.clear();
            LOADED_TOWERS.clear();
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
            if (this.level.isLoaded(targetPos)) {
                BlockState targetState = this.level.getBlockState(targetPos);
                if (targetState.is(DEBlocks.DATA_DISTRIBUTION_TOWER.get())) {
                    return DataDistributionTowerBlock.getBasePos(targetPos, targetState).immutable();
                }
            }
            BlockPos networkPortPos = DataSanctumBlockEntity.findNetworkPortPos(this.level, targetPos);
            if (networkPortPos != null) {
                return networkPortPos.immutable();
            }
        }
        return targetPos.immutable();
    }

    private void emitDiagnosticLogIfNeeded() {
        boolean enabled = DataEnergisticsConfiguration.INSTANCE.developer.verboseRuntimeLogging;
        if (enabled != this.verboseRuntimeLoggingEnabled) {
            this.verboseRuntimeLoggingEnabled = enabled;
            resetDiagnosticCounters(Long.MIN_VALUE);
        }
        if (!enabled) {
            return;
        }

        ServerLevel level = towerLevel();
        long gameTime = level.getGameTime();
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
                    level.dimension().location(),
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

    private void resetDiagnosticCounters(long windowStartTick) {
        this.diagnosticWindowStartTick = windowStartTick;
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

        resetPersistedLinkRuntimeState();
        this.pendingNetworkRecovery = true;

        if (allowsAutomaticRangeConnections()) {
            requestNearbyConnectableNodeScan();
        }
        requestAeTickWake();
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

    private boolean canAutomaticallyTrackGridLink(BlockPos targetPos) {
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        if (this.level == null || this.worldPosition.equals(normalizedPos) || !isWithinTowerCoverage(normalizedPos)) {
            return false;
        }
        if (getTargetTransferMode(normalizedPos) == TargetTransferMode.DISABLED) {
            return false;
        }

        if (isLoadedTowerTarget(normalizedPos)) {
            return true;
        }

        return allowsAeTargets() && hasExposedAeNode(normalizedPos);
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

    /**
     * Stable dimension-aware key used to merge legacy display rows with virtual-device snapshots.
     */
    private record DisplayTargetKey(ResourceLocation dimensionId, BlockPos position) {

        private DisplayTargetKey {
            position = position.immutable();
        }
    }

    /**
     * Item and label resolved without loading the target chunk.
     */
    private record DeviceDisplay(ResourceLocation itemId, String displayName) {}

    private record TargetEnergySnapshot(int stored, int capacity, boolean canExtract, boolean canReceive) {}

    private record TargetEnergyFailureKey(BlockPos targetPos, @Nullable Direction side) {

        private TargetEnergyFailureKey {
            targetPos = targetPos.immutable();
        }
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

    public record TargetTransferInfo(int channelConnections,
                                     boolean hasAeTarget,
                                     boolean hasEnergyTarget,
                                     long storedFe,
                                     long capacityFe,
                                     boolean canExtractFe,
                                     boolean canReceiveFe,
                                     int requestedChannels,
                                     TowerVirtualDeviceState state,
                                     String failure,
                                     TowerRuntimeKey ownerTower,
                                     ResourceLocation bindingDimensionId,
                                     BlockPos bindingAnchor,
                                     @Nullable TowerDeviceKey deviceKey) {

        private static final TargetTransferInfo EMPTY = new TargetTransferInfo(
                0,
                false,
                false,
                0L,
                0L,
                false,
                false,
                0,
                TowerVirtualDeviceState.WAITING_TARGET,
                "TARGET_UNAVAILABLE",
                new TowerRuntimeKey(Level.OVERWORLD.location(), BlockPos.ZERO),
                Level.OVERWORLD.location(),
                BlockPos.ZERO,
                null);

        /**
         * Creates a legacy aggregate row without a virtual-device identity.
         */
        public TargetTransferInfo(int channelConnections,
                                  boolean hasAeTarget,
                                  boolean hasEnergyTarget,
                                  long storedFe,
                                  long capacityFe,
                                  boolean canExtractFe,
                                  boolean canReceiveFe) {
            this(
                    channelConnections,
                    hasAeTarget,
                    hasEnergyTarget,
                    storedFe,
                    capacityFe,
                    canExtractFe,
                    canReceiveFe,
                    channelConnections,
                    TowerVirtualDeviceState.ALLOCATED,
                    "",
                    new TowerRuntimeKey(Level.OVERWORLD.location(), BlockPos.ZERO),
                    Level.OVERWORLD.location(),
                    BlockPos.ZERO,
                    null);
        }

        /**
         * Validates and freezes one per-binding or per-device payload snapshot.
         */
        public TargetTransferInfo {
            if (channelConnections < 0 || requestedChannels < 0 || channelConnections > requestedChannels) {
                throw new IllegalArgumentException("Tower target channel counters are invalid");
            }
            if (storedFe < 0 || capacityFe < storedFe) {
                throw new IllegalArgumentException("Tower target FE snapshot is invalid");
            }
            bindingAnchor = bindingAnchor.immutable();
        }

        /**
         * @return whether this row represents a logical node grouped at its binding anchor
         */
        public boolean logicalDevice() {
            return this.deviceKey != null && this.deviceKey.position() == null;
        }
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
            if (verboseRuntimeLoggingEnabled) {
                diagnosticReceiveCalls++;
                diagnosticRequestedReceive += maxReceive;
            }
            int received = clampStoredAmount(distributeEnergyInRange(maxReceive, simulate, this.excludedPos));
            if (verboseRuntimeLoggingEnabled) {
                diagnosticReturnedReceive += received;
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (verboseRuntimeLoggingEnabled) {
                if (simulate) {
                    diagnosticSimulatedExtractCalls++;
                    diagnosticRequestedSimulatedExtract += maxExtract;
                } else {
                    diagnosticRealExtractCalls++;
                    diagnosticRequestedRealExtract += maxExtract;
                }
            }

            int extracted = extractEnergyFromRange(maxExtract, simulate, this.excludedPos);
            if (verboseRuntimeLoggingEnabled) {
                if (simulate) {
                    diagnosticReturnedSimulatedExtract += extracted;
                } else {
                    diagnosticReturnedRealExtract += extracted;
                }
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            if (verboseRuntimeLoggingEnabled) {
                diagnosticGetStoredCalls++;
            }
            return clampStoredAmount(getTotalExtractableEnergy(this.excludedPos));
        }

        @Override
        public int getMaxEnergyStored() {
            if (verboseRuntimeLoggingEnabled) {
                diagnosticGetMaxStoredCalls++;
            }
            int stored = clampStoredAmount(getTotalExtractableEnergy(this.excludedPos));
            long receivable = getTotalReceivableEnergy(this.excludedPos);
            return clampStoredAmount(saturatingAdd(stored, receivable));
        }

        @Override
        public boolean canExtract() {
            if (verboseRuntimeLoggingEnabled) {
                diagnosticCanExtractCalls++;
            }
            return hasAnySource(this.excludedPos);
        }

        @Override
        public boolean canReceive() {
            if (verboseRuntimeLoggingEnabled) {
                diagnosticCanReceiveCalls++;
            }
            return hasAnyReceiver(this.excludedPos);
        }
    }
}
