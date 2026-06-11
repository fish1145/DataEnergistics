package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.CustomAdHocChannelHost;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.config.Config;
import com.fish_dan_.data_energistics.integration.AE2FluxIntegration;
import com.fish_dan_.data_energistics.integration.OritechEnergyIntegration;
import com.fish_dan_.data_energistics.item.DataDistributionConnectorItem;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.util.MemoryCardSettingsHelper;
import com.fish_dan_.data_energistics.util.ReflectionAccess;
import com.fish_dan_.data_energistics.util.ServerTickDelayQueue;

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
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.level.BlockEvent;

import appeng.api.AECapabilities;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingService;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.parts.CableBusContainer;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.AEItemDefinitionFilter;
import com.mojang.logging.LogUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Data_Energistics.MODID)
public class DataDistributionTowerBlockEntity extends AENetworkedBlockEntity implements CustomAdHocChannelHost, InternalInventoryHost {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NEOECOAE_BLOCK_ENTITY_PREFIX = "cn.dancingsnow.neoecoae.blocks.entity.";
    private static final Set<String> PREFERRED_ECO_SUBSYSTEM_HOST_CLASSES = Set.of(
            "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity",
            "cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity",
            "cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity");
    private static final String SHOW_RANGE_TAG = "show_range";
    private static final String LINKED_POSITIONS_TAG = "linked_positions";
    private static final String CONNECTION_MODE_TAG = "connection_mode";
    private static final String TARGET_TRANSFER_MODES_TAG = "target_transfer_modes";
    private static final String RANGE_ADJUSTMENT_MODE_TAG = "range_adjustment_mode";
    private static final int INITIAL_PENDING_DELAY = 2;
    private static final int INITIAL_DISCOVERY_STAGGER_TICKS = 10;
    private static final int AUTO_DISCOVERY_INTERVAL_TICKS = 20;
    private static final int TRANSFER_SUBSTEPS_PER_TICK = 5;
    private static final int TRANSFER_SCAN_CACHE_TICKS = 5;
    private static final int CLUSTER_CACHE_TICKS = 10;
    private static final int DIAGNOSTIC_LOG_INTERVAL_TICKS = 100;
    private static final int CACHE_CLEANUP_INTERVAL_TICKS = 6000;
    private static final int MAX_ENERGY_STORAGE_VIEWS = 256;
    private static final int MAX_CURSOR_ENTRIES = 128;
    private static final double BASE_IDLE_POWER_USAGE = 4.0;
    private static final double IDLE_POWER_USAGE_PER_ADDITIONAL_CHUNK = 8.0;
    private static final int BOOSTERS_PER_CHUNK_RING = 8;
    private static final int VERTICAL_RANGE_ABOVE = 256;
    private static final int VERTICAL_RANGE_BELOW = 128;
    private static final long DIRECT_ENERGY_INSERT_UNAVAILABLE = Long.MIN_VALUE;
    private static final List<String> DIRECT_ENERGY_FIELD_NAMES = List.of("energy", "storedEnergy", "energyStored", "stored", "amount");
    private static final List<String> DIRECT_ENERGY_CAPACITY_FIELD_NAMES = List.of("capacity", "maxEnergy", "maxEnergyStored", "maxStored", "maxStorage");
    private static final List<String> DIRECT_ENERGY_WRAPPER_FIELD_NAMES = List.of("container", "storage", "delegate", "wrapped", "backingStorage");
    private static final Map<ChunkKey, Set<BlockPos>> TOWER_CHUNK_POSITIONS = new HashMap<>();
    private static final Map<Class<?>, Optional<DirectEnergyStorageAccess>> DIRECT_ENERGY_STORAGE_ACCESS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<VarHandle>> DIRECT_ENERGY_WRAPPER_FIELD_CACHE = new ConcurrentHashMap<>();
    private static MinecraftServer boundServer;

    private final Map<BlockPos, Integer> pendingLinkPositions = new LinkedHashMap<>();
    private final Set<BlockPos> linkedPositions = new LinkedHashSet<>();
    private final Map<BlockPos, List<IGridConnection>> linkedConnections = new HashMap<>();
    private final Map<BlockPos, TargetTransferMode> targetTransferModes = new HashMap<>();
    private final Map<BlockPos, EnergyQuerySummary> cachedExtractQuerySummaries = new HashMap<>();
    private final Map<BlockPos, ReceiverQuerySummary> cachedReceiveQuerySummaries = new HashMap<>();
    private final Map<BlockPos, Integer> extractRoundRobinCursor = new HashMap<>();
    private final Map<BlockPos, Integer> receiveRoundRobinCursor = new HashMap<>();
    private final Map<BlockPos, TowerEnergyStorage> cachedEnergyStorageViews = new HashMap<>();
    private final Map<ExtractSimulationKey, Integer> cachedSimulatedExtracts = new HashMap<>();
    private final AppEngInternalInventory wirelessBoosters = new AppEngInternalInventory(this, 1);
    private final ArrayList<EnergyEndpoint> reusableEndpointFilter = new ArrayList<>();
    private long lastEndpointCacheTick = Long.MIN_VALUE;
    private long lastClusterCacheTick = Long.MIN_VALUE;
    private List<BlockPos> cachedEndpoints = List.of();
    private List<BlockPos> cachedAeDisplayTargets = List.of();
    private List<DataDistributionTowerBlockEntity> cachedTowerCluster = List.of();
    private List<EnergyEndpoint> cachedReceiveEnergyEndpoints = List.of();
    private List<EnergyEndpoint> cachedExtractEnergyEndpoints = List.of();
    private TransferScanSnapshot cachedTransferScanSnapshot = TransferScanSnapshot.EMPTY;
    private boolean endpointCacheValid;
    private boolean receiveEndpointResolutionValid;
    private boolean extractEndpointResolutionValid;
    private BlockPos cachedClusterCoordinatorPos;
    private long cachedTransferBudgetHint = 0L;
    private long cachedSimulatedExtractTick = Long.MIN_VALUE;
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
    private int cacheCleanupCooldown;
    @Getter
    private ConnectionMode connectionMode = ConnectionMode.AE_AND_FE;
    @Getter
    private RangeAdjustmentMode rangeAdjustmentMode = RangeAdjustmentMode.POINT;
    private int autoDiscoveryCooldown;
    private int indexedChunkRadius = -1;
    private int syncedChunkRadius = 0;

    public DataDistributionTowerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.wirelessBoosters.setFilter(new AEItemDefinitionFilter(AEItems.WIRELESS_BOOSTER));
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
                .setIdlePowerUsage(BASE_IDLE_POWER_USAGE);
    }

    @Override
    public void onReady() {
        super.onReady();
        updateIdlePowerUsage();
        if (this.level != null && !this.level.isClientSide()) {
            registerInChunkIndex();
            invalidateEndpointCache();
            requeuePersistedLinks();
            resetAutoDiscoveryCooldown();
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide()) {
            unregisterFromChunkIndex();
            destroyAllConnections();
            clearRuntimeCaches();
        }
        super.setRemoved();
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.showRange = data.getBoolean(SHOW_RANGE_TAG);
        this.connectionMode = ConnectionMode.fromSerializedName(data.getString(CONNECTION_MODE_TAG));
        this.rangeAdjustmentMode = RangeAdjustmentMode.fromSerializedName(data.getString(RANGE_ADJUSTMENT_MODE_TAG));
        this.wirelessBoosters.readFromNBT(data, "wireless_boosters", registries);
        this.syncedChunkRadius = computeChunkRadius();
        updateIdlePowerUsage();
        this.pendingLinkPositions.clear();
        this.linkedPositions.clear();
        this.linkedConnections.clear();
        this.targetTransferModes.clear();

        Tag root = data.get(LINKED_POSITIONS_TAG);
        if (root instanceof ListTag list) {
            for (Tag tag : list) {
                if (tag instanceof CompoundTag compound) {
                    NbtUtils.readBlockPos(compound, "pos").ifPresent(pos -> this.linkedPositions.add(pos.immutable()));
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
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putBoolean(SHOW_RANGE_TAG, this.showRange);
        data.putString(CONNECTION_MODE_TAG, this.connectionMode.getSerializedName());
        data.putString(RANGE_ADJUSTMENT_MODE_TAG, this.rangeAdjustmentMode.getSerializedName());
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
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = input.get(ModDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get());
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

    public void serverTick() {
        if (this.level == null) {
            return;
        }

        emitDiagnosticLogIfNeeded();

        syncClientOnlineState();

        if (--this.cacheCleanupCooldown <= 0) {
            this.cacheCleanupCooldown = CACHE_CLEANUP_INTERVAL_TICKS;
            trimCaches();
        }

        if (this.pendingRangeRefresh) {
            applyPendingRangeRefresh();
        }

        IGridNode selfNode = this.getMainNode().getNode();
        if (selfNode == null || !selfNode.isActive()) {
            return;
        }

        processAutoDiscovery();

        if (selfNode.getUsedChannels() < getMaxLinkChannels()) {
            processPendingLinks(selfNode);
        }

        if (isClusterCoordinator()) {
            performActiveRangeTransfer();
        }
    }

    public IEnergyStorage getEnergyStorageForQuery(BlockPos accessPos, @Nullable net.minecraft.core.Direction side) {
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

    public TargetTransferMode cycleTargetTransferMode(BlockPos targetPos) {
        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        TargetTransferMode nextMode = getTargetTransferMode(normalizedPos).next();
        setTargetTransferMode(normalizedPos, nextMode);
        return nextMode;
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
            this.pendingLinkPositions.remove(normalizedPos);
            this.linkedPositions.remove(normalizedPos);
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
            scanNearbyConnectableNodes();
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

        boolean aeSupported = hasAeNodeCapability(normalizedPos);
        boolean feSupported = canReceiveEnergy(findAccessibleEnergyStorage(normalizedPos, true));
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

        this.linkedPositions.add(normalizedPos);
        if (aeSupported && canMaintainGridLinkTo(normalizedPos)) {
            queueLink(normalizedPos, 0);
        } else {
            this.pendingLinkPositions.remove(normalizedPos);
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
                    scanNearbyConnectableNodes();
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
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>(this.linkedPositions);
        positions.addAll(this.pendingLinkPositions.keySet());
        for (BlockPos pos : positions) {
            if (getTargetTransferMode(pos) == TargetTransferMode.DISABLED) {
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
        double minX = getCoverageMinX(chunkRadius);
        double minZ = getCoverageMinZ(chunkRadius);
        double maxX = getCoverageMaxX(chunkRadius);
        double maxZ = getCoverageMaxZ(chunkRadius);
        int minY = this.worldPosition.getY() - VERTICAL_RANGE_BELOW;
        int maxY = this.worldPosition.getY() + VERTICAL_RANGE_ABOVE + 1;

        if (this.level == null) {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return new AABB(
                minX,
                Math.max(this.level.getMinBuildHeight(), minY),
                minZ,
                maxX,
                Math.min(this.level.getMaxBuildHeight(), maxY),
                maxZ);
    }

    public String getChannelDisplayText() {
        IGridNode node = this.getMainNode().getNode();
        int used = node == null ? 0 : node.getUsedChannels();
        return used + "/" + getMaxLinkChannels();
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

    public int getAvailableFeForUi() {
        return clampStoredAmount(getTotalExtractableEnergy(null));
    }

    public TargetTransferInfo getTargetTransferInfo(BlockPos targetPos) {
        if (this.level == null) {
            return TargetTransferInfo.EMPTY;
        }

        BlockPos normalizedPos = normalizeTargetPos(targetPos);
        int channelConnections = this.linkedConnections.getOrDefault(normalizedPos, List.of()).size();
        long stored = 0L;
        long capacity = 0L;
        boolean canExtract = false;
        boolean canReceive = false;
        boolean hasEnergy = false;

        for (var direction : net.minecraft.core.Direction.values()) {
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

        boolean hasAeTarget = this.level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, normalizedPos, null) != null;
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
    }

    public int getBoundTargetCount() {
        return getBoundTargetSummaries(Integer.MAX_VALUE).size();
    }

    public List<String> getBoundTargetDisplayLines(int maxLines) {
        if (this.level == null || maxLines <= 0) {
            return List.of();
        }

        ArrayList<String> lines = new ArrayList<>();
        for (BlockPos pos : this.linkedPositions) {
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity) {
                continue;
            }

            BlockState state = this.level.getBlockState(pos);
            Block block = state.getBlock();
            String name = block.getName().getString();
            lines.add(name);
        }

        lines.sort(String::compareToIgnoreCase);
        if (lines.size() > maxLines) {
            return List.copyOf(lines.subList(0, maxLines));
        }
        return List.copyOf(lines);
    }

    public List<BoundTargetSummary> getBoundTargetSummaries(int maxEntries) {
        if (this.level == null || maxEntries <= 0) {
            return List.of();
        }

        cleanupInvalidBoundTargets();

        ArrayList<BoundTargetSummary> results = new ArrayList<>();
        for (DisplayTarget target : collectDisplayTargets()) {
            BlockPos pos = target.pos();
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            if (shouldHideFromBoundTargetDisplay(blockEntity)) {
                continue;
            }
            if (appendCableBusSummaries(results, blockEntity, pos, target.kind(), maxEntries)) {
                if (results.size() >= maxEntries) {
                    break;
                }
                continue;
            }

            BlockState state = this.level.getBlockState(pos);
            Block block = state.getBlock();
            Item item = block.asItem();
            if (item == Items.AIR) {
                item = Items.BARRIER;
            }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            String displayName = resolveTargetDisplayName(state, blockEntity);
            results.add(new BoundTargetSummary(itemId, displayName, 1, this.level.dimension().location(), pos.immutable(), target.kind(), getTargetTransferMode(pos), getTargetTransferInfo(pos)));
            if (results.size() >= maxEntries) {
                break;
            }
        }

        if (results.size() > maxEntries) {
            return List.copyOf(results.subList(0, maxEntries));
        }
        return List.copyOf(results);
    }

    private boolean appendCableBusSummaries(List<BoundTargetSummary> results, @Nullable BlockEntity blockEntity, BlockPos pos,
                                            TargetKind kind, int maxEntries) {
        if (!(blockEntity instanceof CableBusBlockEntity cableBusBlockEntity)) {
            return false;
        }

        CableBusContainer cableBus = cableBusBlockEntity.getCableBus();
        boolean appended = false;
        IPart centerPart = cableBus.getPart(null);
        if (centerPart != null) {
            appended = appendPartSummary(results, centerPart, pos, kind, maxEntries, null, "");
            if (results.size() >= maxEntries) {
                return appended;
            }
        }

        ArrayList<CableBusDisplayPart> sideParts = new ArrayList<>();
        for (var direction : net.minecraft.core.Direction.values()) {
            IPart part = cableBus.getPart(direction);
            if (part != null) {
                sideParts.add(new CableBusDisplayPart(part, direction));
            }
        }

        for (int i = 0; i < sideParts.size() && results.size() < maxEntries; i++) {
            CableBusDisplayPart sidePart = sideParts.get(i);
            String prefix = centerPart != null ? (i == sideParts.size() - 1 ? "└" : "├") : "";
            if (appendPartSummary(results, sidePart.part(), pos, kind, maxEntries, sidePart.direction(), prefix)) {
                appended = true;
            }
        }

        return appended;
    }

    private boolean appendPartSummary(List<BoundTargetSummary> results, @Nullable IPart part, BlockPos pos, TargetKind kind,
                                      int maxEntries, @Nullable Direction direction,
                                      String prefix) {
        if (part == null || this.level == null || results.size() >= maxEntries) {
            return false;
        }

        Item item = resolvePartItem(part);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String displayName = resolvePartDisplayName(part, item, direction, prefix, "");
        results.add(new BoundTargetSummary(itemId, displayName, 1, this.level.dimension().location(), pos.immutable(), kind, getTargetTransferMode(pos), getTargetTransferInfo(pos)));
        return true;
    }

    private Item resolvePartItem(IPart part) {
        IPartItem<?> partItem = part.getPartItem();
        if (partItem instanceof Item item) {
            return item;
        }
        if (partItem instanceof ItemLike itemLike) {
            Item item = itemLike.asItem();
            if (item != Items.AIR) {
                return item;
            }
        }
        return Items.BARRIER;
    }

    private String resolvePartDisplayName(IPart part, Item item, @Nullable net.minecraft.core.Direction direction,
                                          String prefix, String groupSuffix) {
        String directionSuffix = direction == null ? "" : " [" + formatDirection(direction) + "]";
        String suffix = directionSuffix + groupSuffix;
        if (part instanceof Nameable nameable) {
            Component displayName = nameable.getDisplayName();
            if (displayName != null) {
                String resolved = displayName.getString();
                if (!resolved.isBlank()) {
                    return prefix + resolved + suffix;
                }
            }
        }

        if (item != Items.AIR) {
            String itemName = new ItemStack(item).getHoverName().getString();
            if (!itemName.isBlank()) {
                return prefix + itemName + suffix;
            }
        }

        return prefix + part.getClass().getSimpleName() + suffix;
    }

    private String formatDirection(net.minecraft.core.Direction direction) {
        return switch (direction) {
            case NORTH -> "\u5317";
            case SOUTH -> "\u5357";
            case WEST -> "\u897f";
            case EAST -> "\u4e1c";
            case UP -> "\u4e0a";
            case DOWN -> "\u4e0b";
        };
    }

    private List<DisplayTarget> collectDisplayTargets() {
        if (this.level == null) {
            return List.of();
        }

        cleanupInvalidBoundTargets();

        LinkedHashMap<BlockPos, TargetKind> positions = new LinkedHashMap<>();

        for (BlockPos pos : this.linkedPositions) {
            if (allowsAeTargets() && getTargetTransferMode(pos) != TargetTransferMode.DISABLED && !this.level.getBlockState(pos).isAir()) {
                BlockEntity blockEntity = this.level.getBlockEntity(pos);
                if (!(blockEntity instanceof DataDistributionTowerBlockEntity)) {
                    positions.put(pos.immutable(), TargetKind.AE);
                }
            }
        }

        for (BlockPos pos : getCachedAeDisplayTargets()) {
            if (allowsAeTargets() && targetAllowsAe(pos) && !this.level.getBlockState(pos).isAir()) {
                BlockEntity blockEntity = this.level.getBlockEntity(pos);
                if (!(blockEntity instanceof DataDistributionTowerBlockEntity)) {
                    positions.putIfAbsent(pos.immutable(), TargetKind.AE);
                }
            }
        }

        for (BlockPos pos : getCachedEndpoints()) {
            if (this.level.getBlockState(pos).isAir()) {
                continue;
            }
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity) {
                continue;
            }
            if (!targetAllowsFe(pos)) {
                continue;
            }
            IEnergyStorage storage = findAccessibleEnergyStorage(pos, true);
            if (canReceiveEnergy(storage)) {
                positions.putIfAbsent(pos.immutable(), TargetKind.FE);
            }
        }

        for (BlockPos pos : this.targetTransferModes.keySet()) {
            if (!isWithinTowerCoverage(pos) || this.level.getBlockState(pos).isAir()) {
                continue;
            }
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity || shouldHideFromBoundTargetDisplay(blockEntity)) {
                continue;
            }
            positions.putIfAbsent(pos.immutable(), hasDisplayableAeTarget(blockEntity) ? TargetKind.AE : TargetKind.FE);
        }

        collapseAeCraftingDisplayTargets(positions);

        ArrayList<DisplayTarget> results = new ArrayList<>(positions.size());
        positions.forEach((pos, kind) -> results.add(new DisplayTarget(pos, kind)));
        return List.copyOf(results);
    }

    private void collapseAeCraftingDisplayTargets(LinkedHashMap<BlockPos, TargetKind> positions) {
        if (this.level == null || positions.isEmpty()) {
            return;
        }

        ArrayList<BlockPos> craftingPositions = new ArrayList<>();
        HashMap<BlockPos, BlockPos> clusterRepresentatives = new HashMap<>();
        for (Map.Entry<BlockPos, TargetKind> entry : positions.entrySet()) {
            if (entry.getValue() != TargetKind.AE) {
                continue;
            }

            BlockPos pos = entry.getKey();
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            if (!isAeCraftingClusterComponent(blockEntity)) {
                continue;
            }

            BlockPos representativePos = findAeCraftingClusterRepresentative(blockEntity);
            if (representativePos == null) {
                continue;
            }

            craftingPositions.add(pos);
            clusterRepresentatives.put(pos, representativePos);
        }

        if (craftingPositions.size() <= 1) {
            return;
        }

        for (BlockPos pos : craftingPositions) {
            BlockPos representativePos = clusterRepresentatives.get(pos);
            if (representativePos != null && !pos.equals(representativePos)) {
                positions.remove(pos);
            }
        }
    }

    private int compareAeCraftingDisplayTargets(BlockPos leftPos, BlockPos rightPos) {
        if (this.level == null) {
            return compareBlockPos(leftPos, rightPos);
        }

        int leftPriority = getAeCraftingDisplayPriority(this.level.getBlockEntity(leftPos));
        int rightPriority = getAeCraftingDisplayPriority(this.level.getBlockEntity(rightPos));
        if (leftPriority != rightPriority) {
            return Integer.compare(rightPriority, leftPriority);
        }

        return compareBlockPos(leftPos, rightPos);
    }

    private int getAeCraftingDisplayPriority(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof MolecularAssemblerBlockEntity) {
            return 1;
        }
        if (blockEntity instanceof CraftingBlockEntity craftingBlockEntity) {
            return craftingBlockEntity.isCoreBlock() ? 3 : 2;
        }
        if (isReflectiveAeCraftingDisplayComponent(blockEntity)) {
            return isReflectiveAeCraftingCoreBlock(blockEntity) ? 3 : 2;
        }
        return 0;
    }

    private boolean isAeCraftingClusterComponent(@Nullable BlockEntity blockEntity) {
        return isAeCraftingDisplayComponent(blockEntity) && !(blockEntity instanceof MolecularAssemblerBlockEntity);
    }

    private String resolveTargetDisplayName(BlockState state, @Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof Nameable nameable) {
            Component displayName = nameable.getDisplayName();
            if (displayName != null) {
                String resolved = displayName.getString();
                if (!resolved.isBlank() && !isFallbackAirName(resolved)) {
                    return resolved;
                }
            }
        }

        Block block = state.getBlock();
        Item item = block.asItem();
        if (item != Items.AIR) {
            String itemName = new ItemStack(item).getHoverName().getString();
            if (!itemName.isBlank()) {
                return itemName;
            }
        }

        return block.getName().getString();
    }

    private boolean isFallbackAirName(String displayName) {
        return displayName.equals(Items.AIR.getDescription().getString()) || displayName.equals(Blocks.AIR.getName().getString());
    }

    @Override
    public int getCustomAdHocChannels() {
        ChannelMode mode = AEConfig.instance().getChannelMode();
        if (mode == ChannelMode.INFINITE) {
            return Integer.MAX_VALUE;
        }
        return 32 * mode.getCableCapacityFactor();
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof ServerLevelAccessor serverLevelAccessor) {
            Level level = serverLevelAccessor.getLevel();
            invalidateNearbyCaches(level, event.getPos());
            onPotentialNodeAdded(level, event.getPos());
            autoConnectPlacedBlockWithOffhandConnector(level, event);
        }
    }

    private static void autoConnectPlacedBlockWithOffhandConnector(Level level, BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getPlacedBlock().isAir()) {
            return;
        }

        ItemStack offhandStack = player.getOffhandItem();
        if (offhandStack.getItem() instanceof DataDistributionConnectorItem connectorItem) {
            MinecraftServer server = level.getServer();
            BlockPos placedPos = event.getPos().immutable();
            BlockState placedState = event.getPlacedBlock();
            ItemStack connectorStack = offhandStack.copy();
            ServerTickDelayQueue.runNextServerTick(server, () -> {
                if (!level.isLoaded(placedPos) || !level.getBlockState(placedPos).equals(placedState)) {
                    return;
                }

                connectorItem.autoConnectPlacedBlock(connectorStack, player, level, placedPos);
            });
        }
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
    public static void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        ensureBound(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
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
                if (!tower.allowsAutomaticRangeConnections()) {
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

    public int scanNearbyConnectableNodes() {
        if (this.level == null || !allowsAutomaticRangeConnections()) {
            return 0;
        }

        int added = 0;
        ArrayList<BlockEntity> nearbyBlockEntities = new ArrayList<>(getNearbyBlockEntities());
        nearbyBlockEntities.sort(this::compareLinkTargetPriority);
        for (BlockEntity blockEntity : nearbyBlockEntities) {
            BlockPos pos = blockEntity.getBlockPos().immutable();
            if (pos.equals(this.worldPosition)) {
                continue;
            }
            if (queueLink(pos, 0)) {
                added++;
            }
        }

        if (added > 0) {
            this.setChanged();
        }
        return added;
    }

    private void processPendingLinks(IGridNode selfNode) {
        ArrayList<BlockPos> readyTargets = new ArrayList<>();
        for (Map.Entry<BlockPos, Integer> entry : this.pendingLinkPositions.entrySet()) {
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
                continue;
            }

            BlockPos targetPos = entry.getKey();
            if (this.level.isLoaded(targetPos)) {
                readyTargets.add(targetPos);
            }
        }

        if (readyTargets.isEmpty()) {
            return;
        }

        readyTargets.sort(this::compareLinkTargetPriority);
        int remainingChannels = Math.max(0, getMaxLinkChannels() - selfNode.getUsedChannels());
        if (remainingChannels <= 0) {
            return;
        }

        for (BlockPos targetPos : readyTargets) {
            List<IGridNode> linkableNodes = getLinkableTargetNodes(selfNode, targetPos);
            if (linkableNodes.isEmpty()) {
                this.pendingLinkPositions.remove(targetPos);
                continue;
            }
            if (linkableNodes.size() > remainingChannels) {
                continue;
            }

            int connectedNodes = reconnectTarget(selfNode, targetPos, linkableNodes);
            this.pendingLinkPositions.remove(targetPos);
            remainingChannels -= connectedNodes;
            if (remainingChannels <= 0) {
                break;
            }
        }
    }

    private boolean isTowerActive() {
        return this.getMainNode().isActive();
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

        if (AE2FluxIntegration.isAvailable() && AE2FluxIntegration.extractEnergyFromOwnNetwork(this, 1, true) > 0) {
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
        return Math.max(0, Config.dataDistributionTowerRange - 1 + boosterCount / BOOSTERS_PER_CHUNK_RING);
    }

    private int getCoveredChunkCount() {
        int diameter = computeChunkRadius() * 2 + 1;
        return diameter * diameter;
    }

    private double computeIdlePowerUsage() {
        return BASE_IDLE_POWER_USAGE + Math.max(0, getCoveredChunkCount() - 1) * IDLE_POWER_USAGE_PER_ADDITIONAL_CHUNK;
    }

    private void updateIdlePowerUsage() {
        this.getMainNode().setIdlePowerUsage(computeIdlePowerUsage());
    }

    private boolean isWithinTowerCoverage(BlockPos targetPos) {
        return isWithinCenteredHorizontalRange(targetPos, getChunkRadius()) && targetPos.getY() >= this.worldPosition.getY() - VERTICAL_RANGE_BELOW && targetPos.getY() <= this.worldPosition.getY() + VERTICAL_RANGE_ABOVE;
    }

    private int getTransferBudgetPerTick() {
        return Config.dataDistributionTowerTransferPerTick;
    }

    private int getMaxLinkChannels() {
        IGridNode node = this.getMainNode().getNode();
        if (node != null) {
            return node.getMaxChannels();
        }

        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            return getCustomAdHocChannels();
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
        for (int chunkX = getCoverageMinChunkX(chunkRadius); chunkX <= getCoverageMaxChunkX(chunkRadius); chunkX++) {
            for (int chunkZ = getCoverageMinChunkZ(chunkRadius); chunkZ <= getCoverageMaxChunkZ(chunkRadius); chunkZ++) {
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
        for (int chunkX = getCoverageMinChunkX(chunkRadius); chunkX <= getCoverageMaxChunkX(chunkRadius); chunkX++) {
            for (int chunkZ = getCoverageMinChunkZ(chunkRadius); chunkZ <= getCoverageMaxChunkZ(chunkRadius); chunkZ++) {
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

    private boolean isWithinCenteredHorizontalRange(BlockPos targetPos, int chunkRadius) {
        double targetCenterX = targetPos.getX() + 0.5D;
        double targetCenterZ = targetPos.getZ() + 0.5D;
        return targetCenterX >= getCoverageMinX(chunkRadius) && targetCenterX < getCoverageMaxX(chunkRadius) && targetCenterZ >= getCoverageMinZ(chunkRadius) && targetCenterZ < getCoverageMaxZ(chunkRadius);
    }

    private double getCoverageMinX(int chunkRadius) {
        return this.worldPosition.getX() + 0.5D - getCoverageHalfWidth(chunkRadius);
    }

    private double getCoverageMinZ(int chunkRadius) {
        return this.worldPosition.getZ() + 0.5D - getCoverageHalfWidth(chunkRadius);
    }

    private double getCoverageMaxX(int chunkRadius) {
        return this.worldPosition.getX() + 0.5D + getCoverageHalfWidth(chunkRadius);
    }

    private double getCoverageMaxZ(int chunkRadius) {
        return this.worldPosition.getZ() + 0.5D + getCoverageHalfWidth(chunkRadius);
    }

    private double getCoverageHalfWidth(int chunkRadius) {
        return getCoverageDiameterBlocks(chunkRadius) / 2.0D;
    }

    private int getCoverageDiameterBlocks(int chunkRadius) {
        return (chunkRadius * 2 + 1) * 16;
    }

    private int getCoverageMinChunkX(int chunkRadius) {
        return Math.floorDiv((int) Math.floor(getCoverageMinX(chunkRadius)), 16);
    }

    private int getCoverageMinChunkZ(int chunkRadius) {
        return Math.floorDiv((int) Math.floor(getCoverageMinZ(chunkRadius)), 16);
    }

    private int getCoverageMaxChunkX(int chunkRadius) {
        return Math.floorDiv((int) Math.ceil(getCoverageMaxX(chunkRadius)) - 1, 16);
    }

    private int getCoverageMaxChunkZ(int chunkRadius) {
        return Math.floorDiv((int) Math.ceil(getCoverageMaxZ(chunkRadius)) - 1, 16);
    }

    private void invalidateEndpointCache() {
        this.lastEndpointCacheTick = Long.MIN_VALUE;
        this.cachedEndpoints = List.of();
        this.cachedAeDisplayTargets = List.of();
        this.endpointCacheValid = false;
        invalidateResolvedEnergyEndpointCache();
    }

    private void invalidateClusterCache() {
        this.lastClusterCacheTick = Long.MIN_VALUE;
        this.cachedTowerCluster = List.of();
        this.cachedClusterCoordinatorPos = null;
        invalidateResolvedEnergyEndpointCache();
    }

    private void invalidateResolvedEnergyEndpointCache() {
        this.cachedReceiveEnergyEndpoints = List.of();
        this.cachedExtractEnergyEndpoints = List.of();
        this.receiveEndpointResolutionValid = false;
        this.extractEndpointResolutionValid = false;
        this.extractRoundRobinCursor.clear();
        this.receiveRoundRobinCursor.clear();
        invalidateEnergyQueryCache();
    }

    private void invalidateEnergyQueryCache() {
        this.cachedExtractQuerySummaries.clear();
        this.cachedReceiveQuerySummaries.clear();
        this.cachedSimulatedExtracts.clear();
        this.cachedSimulatedExtractTick = Long.MIN_VALUE;
        this.cachedTransferScanSnapshot = TransferScanSnapshot.EMPTY;
    }

    private void clearRuntimeCaches() {
        invalidateEndpointCache();
        invalidateClusterCache();
        this.cachedEnergyStorageViews.clear();
        this.reusableEndpointFilter.clear();
        this.cachedTransferBudgetHint = 0L;
        trimCaches();
    }

    private void trimCaches() {
        if (this.cachedEnergyStorageViews.size() > MAX_ENERGY_STORAGE_VIEWS) {
            this.cachedEnergyStorageViews.clear();
        }
        if (this.extractRoundRobinCursor.size() > MAX_CURSOR_ENTRIES) {
            this.extractRoundRobinCursor.clear();
        }
        if (this.receiveRoundRobinCursor.size() > MAX_CURSOR_ENTRIES) {
            this.receiveRoundRobinCursor.clear();
        }
        if (this.cachedExtractQuerySummaries.size() > MAX_CURSOR_ENTRIES) {
            this.cachedExtractQuerySummaries.clear();
        }
        if (this.cachedReceiveQuerySummaries.size() > MAX_CURSOR_ENTRIES) {
            this.cachedReceiveQuerySummaries.clear();
        }
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
                BlockPos pos = blockEntity.getBlockPos().immutable();
                if (isTowerBlock(pos)) {
                    continue;
                }
                if (allowsFeTargets() && targetAllowsFe(pos) && hasAnyEnergyCapability(pos)) {
                    endpoints.add(pos);
                }
                if (allowsAeTargets() && targetAllowsAe(pos) && hasDisplayableAeTarget(blockEntity)) {
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
                if (allowsAeTargets() && targetAllowsAe(pos) && hasDisplayableAeTarget(blockEntity)) {
                    aeDisplayTargets.add(pos);
                }
            }
        }

        this.cachedEndpoints = List.copyOf(endpoints);
        this.cachedAeDisplayTargets = List.copyOf(aeDisplayTargets);
        this.lastEndpointCacheTick = this.level.getGameTime();
        this.endpointCacheValid = true;
    }

    private List<BlockPos> getTrackedTargetPositions() {
        LinkedHashSet<BlockPos> tracked = new LinkedHashSet<>(this.linkedPositions);
        tracked.addAll(this.pendingLinkPositions.keySet());
        tracked.addAll(this.targetTransferModes.keySet());
        return List.copyOf(tracked);
    }

    private List<BlockEntity> getNearbyBlockEntities() {
        if (this.level == null) {
            return List.of();
        }

        ArrayList<BlockEntity> results = new ArrayList<>();
        int chunkRadius = getChunkRadius();
        int minChunkX = getCoverageMinChunkX(chunkRadius);
        int maxChunkX = getCoverageMaxChunkX(chunkRadius);
        int minChunkZ = getCoverageMinChunkZ(chunkRadius);
        int maxChunkZ = getCoverageMaxChunkZ(chunkRadius);

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
        return isPreferredEcoSubsystemHost(blockEntity) ? 1 : 0;
    }

    private boolean isPreferredEcoSubsystemHost(@Nullable BlockEntity blockEntity) {
        return blockEntity != null && PREFERRED_ECO_SUBSYSTEM_HOST_CLASSES.contains(blockEntity.getClass().getName());
    }

    private boolean isEcoSubsystemComponent(@Nullable BlockEntity blockEntity) {
        return blockEntity != null && blockEntity.getClass().getName().startsWith(NEOECOAE_BLOCK_ENTITY_PREFIX);
    }

    private boolean isAeCraftingDisplayComponent(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity instanceof PatternProviderBlockEntity) {
            return false;
        }
        if (blockEntity instanceof CraftingBlockEntity || blockEntity instanceof MolecularAssemblerBlockEntity) {
            return true;
        }
        Block block = blockEntity.getBlockState().getBlock();
        if (block.getClass().getName().contains("CraftingUnitBlock")) {
            return true;
        }
        return isReflectiveAeCraftingDisplayComponent(blockEntity);
    }

    private boolean isAeCraftingClusterBridge(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof PatternProviderBlockEntity;
    }

    private boolean isAeCraftingClusterNode(@Nullable BlockEntity blockEntity) {
        return isAeCraftingDisplayComponent(blockEntity) || isAeCraftingClusterBridge(blockEntity);
    }

    private boolean isReflectiveAeCraftingDisplayComponent(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        Class<?> type = blockEntity.getClass();
        String className = type.getName();
        return className.contains("Crafting") && hasZeroArgMethod(type, "isCoreBlock") && hasZeroArgMethod(type, "getStorageBytes") && hasZeroArgMethod(type, "getAcceleratorThreads");
    }

    private boolean isReflectiveAeCraftingCoreBlock(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || !hasZeroArgMethod(blockEntity.getClass(), "isCoreBlock")) {
            return false;
        }

        Object value = ReflectionAccess.invokeNoArg(blockEntity, "isCoreBlock");
        return value instanceof Boolean bool && bool;
    }

    private boolean hasZeroArgMethod(Class<?> type, String methodName) {
        return ReflectionAccess.hasNoArgMethod(type, methodName);
    }

    @Nullable
    private BlockPos findAeCraftingClusterRepresentative(@Nullable BlockEntity blockEntity) {
        if (!isAeCraftingClusterComponent(blockEntity) || this.level == null) {
            return null;
        }

        BlockPos startPos = blockEntity.getBlockPos();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<BlockPos> visited = new HashSet<>();
        queue.add(startPos);
        visited.add(startPos);
        BlockPos representative = startPos;

        while (!queue.isEmpty()) {
            BlockPos currentPos = queue.removeFirst();
            if (compareAeCraftingDisplayTargets(currentPos, representative) < 0) {
                representative = currentPos;
            }

            for (var direction : net.minecraft.core.Direction.values()) {
                BlockPos neighborPos = currentPos.relative(direction);
                if (!visited.add(neighborPos)) {
                    continue;
                }

                BlockEntity neighbor = this.level.getBlockEntity(neighborPos);
                if (!isAeCraftingClusterComponent(neighbor) && !isAeCraftingClusterBridge(neighbor)) {
                    continue;
                }

                queue.addLast(neighborPos);
            }
        }

        return representative;
    }

    private boolean isRepresentativeAeCraftingComponent(@Nullable BlockEntity blockEntity) {
        if (!isAeCraftingClusterComponent(blockEntity) || this.level == null) {
            return false;
        }

        BlockPos representative = findAeCraftingClusterRepresentative(blockEntity);
        return representative != null && representative.equals(blockEntity.getBlockPos());
    }

    private boolean shouldHideFromBoundTargetDisplay(@Nullable BlockEntity blockEntity) {
        if (isEcoSubsystemComponent(blockEntity)) {
            return !isPreferredEcoSubsystemHost(blockEntity);
        }
        if (isAeCraftingNoiseTarget(blockEntity)) {
            return true;
        }
        return false;
    }

    private boolean isAeCraftingNoiseTarget(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || this.level == null) {
            return false;
        }
        if (isAeCraftingDisplayComponent(blockEntity) || blockEntity instanceof PatternProviderBlockEntity) {
            return false;
        }
        if (blockEntity instanceof CableBusBlockEntity cableBusBlockEntity) {
            return false;
        } else if (this.level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, blockEntity.getBlockPos(), null) == null) {
            return false;
        }

        BlockPos pos = blockEntity.getBlockPos();
        for (var direction : net.minecraft.core.Direction.values()) {
            BlockEntity neighbor = this.level.getBlockEntity(pos.relative(direction));
            if (isAeCraftingClusterNode(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDisplayableCableBusPart(CableBusContainer cableBus) {
        return hasAnyCableBusPart(cableBus);
    }

    private boolean hasAnyEnergyCapability(BlockPos pos) {
        for (var direction : net.minecraft.core.Direction.values()) {
            if (getEnergyStorageAt(pos, direction) != null) {
                return true;
            }
        }
        return getEnergyStorageAt(pos, null) != null;
    }

    private boolean hasStoredEnergy(BlockPos pos) {
        for (var direction : net.minecraft.core.Direction.values()) {
            IEnergyStorage storage = getEnergyStorageAt(pos, direction);
            if (storage != null && storage.getEnergyStored() > 0) {
                return true;
            }
        }

        IEnergyStorage internal = getEnergyStorageAt(pos, null);
        return internal != null && internal.getEnergyStored() > 0;
    }

    private boolean hasDisplayableAeTarget(BlockEntity blockEntity) {
        if (this.level == null) {
            return false;
        }

        if (shouldHideFromBoundTargetDisplay(blockEntity)) {
            return false;
        }

        if (blockEntity instanceof CableBusBlockEntity cableBusBlockEntity) {
            return hasAnyCableBusPart(cableBusBlockEntity.getCableBus());
        }

        if (isAeCraftingDisplayComponent(blockEntity)) {
            return true;
        }

        return blockEntity instanceof PatternProviderBlockEntity;
    }

    private boolean hasWhitelistedCableBusDisplayPart(CableBusContainer cableBus) {
        if (cableBus.getPart(null) != null) {
            return true;
        }

        for (var direction : net.minecraft.core.Direction.values()) {
            if (cableBus.getPart(direction) != null) {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyCableBusPart(CableBusContainer cableBus) {
        if (cableBus.getPart(null) != null) {
            return true;
        }

        for (var direction : net.minecraft.core.Direction.values()) {
            if (cableBus.getPart(direction) != null) {
                return true;
            }
        }

        return false;
    }

    private boolean isTowerBlock(BlockPos pos) {
        return this.level != null && this.level.getBlockState(pos).is(ModBlocks.DATA_DISTRIBUTION_TOWER.get());
    }

    @Nullable
    private IEnergyStorage getEnergyStorageAt(BlockPos pos, @Nullable net.minecraft.core.Direction side) {
        if (this.level == null || isTowerBlock(pos)) {
            return null;
        }
        IEnergyStorage storage = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        return storage != null ? storage : OritechEnergyIntegration.findEnergyStorage(this.level, pos, side);
    }

    @Nullable
    private IEnergyStorage findAccessibleEnergyStorage(BlockPos pos, boolean forReceive) {
        List<EnergyEndpoint> endpoints = findAccessibleEnergyEndpoints(pos, forReceive);
        return endpoints.isEmpty() ? null : endpoints.getFirst().storage();
    }

    private List<EnergyEndpoint> findAccessibleEnergyEndpoints(BlockPos pos, boolean forReceive) {
        if (this.level == null) {
            return List.of();
        }

        ArrayList<EnergyEndpoint> endpoints = new ArrayList<>();
        Set<IEnergyStorage> seenStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean collectAllSides = this.level.getBlockEntity(pos) instanceof CableBusBlockEntity;

        for (var direction : net.minecraft.core.Direction.values()) {
            IEnergyStorage storage = getEnergyStorageAt(pos, direction);
            if (isUsableEnergyStorage(storage, forReceive) && seenStorages.add(storage)) {
                endpoints.add(new EnergyEndpoint(pos.immutable(), direction, storage));
                if (!collectAllSides) {
                    return List.copyOf(endpoints);
                }
            }
        }

        IEnergyStorage internal = getEnergyStorageAt(pos, null);
        if (isUsableEnergyStorage(internal, forReceive) && seenStorages.add(internal)) {
            endpoints.add(new EnergyEndpoint(pos.immutable(), null, internal));
        }
        return List.copyOf(endpoints);
    }

    private static boolean isUsableEnergyStorage(@Nullable IEnergyStorage storage, boolean forReceive) {
        return storage != null && (forReceive ? canReceiveEnergy(storage) : storage.canExtract());
    }

    private List<DataDistributionTowerBlockEntity> collectTowerCluster() {
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

            for (BlockPos linkedPos : tower.linkedPositions) {
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

    private List<EnergyEndpoint> collectEnergyEndpoints(boolean forReceive, @Nullable BlockPos excludedPos) {
        return excludeEnergyEndpoint(getCachedResolvedEnergyEndpoints(forReceive), excludedPos);
    }

    private List<EnergyEndpoint> collectEnergyEndpoints(List<DataDistributionTowerBlockEntity> towers, boolean forReceive) {
        return resolveEnergyEndpoints(towers, forReceive);
    }

    private List<EnergyEndpoint> getCachedResolvedEnergyEndpoints(boolean forReceive) {
        if (this.level == null) {
            return List.of();
        }

        if (forReceive) {
            if (!this.receiveEndpointResolutionValid) {
                this.cachedReceiveEnergyEndpoints = List.copyOf(resolveEnergyEndpoints(collectTowerCluster(), true));
                this.receiveEndpointResolutionValid = true;
            }
            return this.cachedReceiveEnergyEndpoints;
        }

        if (!this.extractEndpointResolutionValid) {
            this.cachedExtractEnergyEndpoints = List.copyOf(resolveEnergyEndpoints(collectTowerCluster(), false));
            this.extractEndpointResolutionValid = true;
        }
        return this.cachedExtractEnergyEndpoints;
    }

    private List<EnergyEndpoint> resolveEnergyEndpoints(List<DataDistributionTowerBlockEntity> towers, boolean forReceive) {
        LinkedHashMap<EnergyEndpointKey, EnergyEndpoint> endpoints = new LinkedHashMap<>();
        for (DataDistributionTowerBlockEntity tower : towers) {
            for (BlockPos pos : tower.getCachedEndpoints()) {
                if (!tower.targetAllowsFe(pos)) {
                    continue;
                }

                for (EnergyEndpoint endpoint : tower.findAccessibleEnergyEndpoints(pos, forReceive)) {
                    endpoints.putIfAbsent(new EnergyEndpointKey(endpoint.pos(), endpoint.side()), endpoint);
                }
            }
        }

        return List.copyOf(endpoints.values());
    }

    private List<EnergyEndpoint> excludeEnergyEndpoint(List<EnergyEndpoint> endpoints, @Nullable BlockPos excludedPos) {
        if (excludedPos == null || endpoints.isEmpty()) {
            return endpoints;
        }

        this.reusableEndpointFilter.clear();
        for (EnergyEndpoint endpoint : endpoints) {
            if (!excludedPos.equals(endpoint.pos())) {
                this.reusableEndpointFilter.add(endpoint);
            }
        }
        return this.reusableEndpointFilter;
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

    private void performActiveRangeTransfer() {
        long transferBudget = getTransferBudgetPerTick();
        this.cachedTransferBudgetHint = transferBudget * TRANSFER_SUBSTEPS_PER_TICK;
        if (transferBudget <= 0) {
            return;
        }

        TransferScanSnapshot transferScanSnapshot = getTransferScanSnapshot();
        if (transferScanSnapshot.receiveEndpoints().isEmpty()) {
            return;
        }

        long remainingBudget = transferBudget;
        for (int substep = 0; substep < TRANSFER_SUBSTEPS_PER_TICK; substep++) {
            if (remainingBudget <= 0) {
                break;
            }

            long stepBudget = divideCeil(remainingBudget, TRANSFER_SUBSTEPS_PER_TICK - substep);
            long simulatedExtract = simulateCachedTransferExtract(stepBudget, transferScanSnapshot);
            if (simulatedExtract <= 0) {
                break;
            }

            long simulatedInsert = distributeEnergyInRange(simulatedExtract, true, null, transferScanSnapshot.receiveEndpoints());
            if (simulatedInsert <= 0) {
                break;
            }

            long transferAmount = Math.min(simulatedExtract, Math.min(simulatedInsert, stepBudget));
            long actuallyExtracted = 0;
            long remainingExtraction = transferAmount;

            if (AE2FluxIntegration.isAvailable()) {
                long extracted = AE2FluxIntegration.extractEnergyFromOwnNetwork(this, remainingExtraction, false);
                if (extracted > 0) {
                    actuallyExtracted += extracted;
                    remainingExtraction -= extracted;
                }
            }
            if (remainingExtraction > 0) {
                actuallyExtracted += extractFromEndpointsRoundRobin(remainingExtraction, false, transferScanSnapshot.extractEndpoints());
            }
            if (actuallyExtracted <= 0) {
                break;
            }

            long actuallyInserted = distributeEnergyInRange(actuallyExtracted, false, null, transferScanSnapshot.receiveEndpoints());
            if (actuallyInserted <= 0) {
                LOGGER.warn("Active range transfer extracted {} FE but failed to insert it; aborting to avoid further loss.", actuallyExtracted);
                break;
            }

            if (actuallyInserted < actuallyExtracted) {
                LOGGER.warn("Active range transfer inserted only {} / {} FE after simulation; stopping to avoid desync.", actuallyInserted, actuallyExtracted);
                break;
            }

            remainingBudget -= actuallyInserted;
        }
    }

    private TransferScanSnapshot getTransferScanSnapshot() {
        if (this.level == null) {
            return TransferScanSnapshot.EMPTY;
        }

        long gameTime = this.level.getGameTime();
        TransferScanSnapshot cached = this.cachedTransferScanSnapshot;
        if (cached.tick() != Long.MIN_VALUE && gameTime - cached.tick() < TRANSFER_SCAN_CACHE_TICKS) {
            return cached;
        }

        List<EnergyEndpoint> extractEndpoints = getCachedResolvedEnergyEndpoints(false);
        List<EnergyEndpoint> receiveEndpoints = getCachedResolvedEnergyEndpoints(true);

        long aeExtractable = 0;
        if (AE2FluxIntegration.isAvailable()) {
            aeExtractable = Math.max(0L, AE2FluxIntegration.extractEnergyFromOwnNetwork(this, Long.MAX_VALUE, true));
        }

        ArrayList<EnergyEndpoint> activeExtractEndpoints = new ArrayList<>(extractEndpoints.size());
        for (EnergyEndpoint endpoint : extractEndpoints) {
            IEnergyStorage storage = endpoint.storage();
            if (storage.canExtract() && storage.getEnergyStored() > 0) {
                activeExtractEndpoints.add(endpoint);
            }
        }

        ArrayList<EnergyEndpoint> activeReceiveEndpoints = new ArrayList<>(receiveEndpoints.size());
        for (EnergyEndpoint endpoint : receiveEndpoints) {
            IEnergyStorage storage = endpoint.storage();
            if (canReceiveEnergy(storage)) {
                activeReceiveEndpoints.add(endpoint);
            }
        }

        TransferScanSnapshot snapshot = new TransferScanSnapshot(
                gameTime,
                aeExtractable,
                List.copyOf(activeExtractEndpoints),
                List.copyOf(activeReceiveEndpoints));
        this.cachedTransferScanSnapshot = snapshot;
        return snapshot;
    }

    private long simulateCachedTransferExtract(long amount, TransferScanSnapshot transferScanSnapshot) {
        if (amount <= 0) {
            return 0;
        }

        long totalExtractable = 0;
        if (transferScanSnapshot.aeExtractable() > 0) {
            totalExtractable = Math.min(amount, transferScanSnapshot.aeExtractable());
        }

        long remaining = amount - totalExtractable;
        if (remaining > 0) {
            totalExtractable += extractFromEndpointsRoundRobin(remaining, true, transferScanSnapshot.extractEndpoints());
        }
        return Math.min(amount, totalExtractable);
    }

    private long extractFromEndpointsRoundRobin(long amount, boolean simulate, List<EnergyEndpoint> extractEndpoints) {
        if (amount <= 0 || extractEndpoints.isEmpty()) {
            return 0;
        }

        long totalExtracted = 0;
        long remaining = amount;
        int endpointCount = extractEndpoints.size();
        int startIndex = getExtractStartIndex(null, endpointCount);
        int lastSuccessfulIndex = -1;

        for (int offset = 0; offset < endpointCount; offset++) {
            if (remaining <= 0) {
                break;
            }

            int endpointIndex = (startIndex + offset) % endpointCount;
            IEnergyStorage storage = extractEndpoints.get(endpointIndex).storage();
            if (!storage.canExtract() || storage.getEnergyStored() <= 0) {
                continue;
            }

            int extracted = storage.extractEnergy(clampEnergyRequest(remaining), simulate);
            if (extracted > 0) {
                totalExtracted += extracted;
                remaining -= extracted;
                lastSuccessfulIndex = endpointIndex;
            }
        }

        if (lastSuccessfulIndex >= 0) {
            this.extractRoundRobinCursor.put(null, lastSuccessfulIndex);
        }

        return totalExtracted;
    }

    private long distributeEnergyInRange(long amount, boolean simulate, @Nullable BlockPos excludedPos,
                                         List<EnergyEndpoint> receiveEndpoints) {
        if (!isTowerActive() || amount <= 0) {
            return 0;
        }

        BlockPos normalizedExcludedPos = normalizeReceiveExcludedPos(excludedPos);
        List<EnergyEndpoint> endpoints = excludeEnergyEndpoint(receiveEndpoints, normalizedExcludedPos);
        this.diagnosticMaxReceiveEndpoints = Math.max(this.diagnosticMaxReceiveEndpoints, endpoints.size());
        if (endpoints.isEmpty()) {
            return 0;
        }

        long totalInserted = 0;
        long remaining = amount;
        int endpointCount = endpoints.size();
        int startIndex = getReceiveStartIndex(normalizedExcludedPos, endpointCount);
        int lastSuccessfulIndex = -1;
        for (int offset = 0; offset < endpointCount; offset++) {
            if (remaining <= 0) {
                break;
            }

            int endpointIndex = (startIndex + offset) % endpointCount;
            EnergyEndpoint endpoint = endpoints.get(endpointIndex);
            IEnergyStorage storage = endpoint.storage();
            if (!canReceiveEnergy(storage)) {
                continue;
            }

            long inserted = insertEnergyIntoEndpoint(endpoint, remaining, simulate);
            if (inserted > 0) {
                totalInserted += inserted;
                remaining -= inserted;
                lastSuccessfulIndex = endpointIndex;
            }
        }

        if (lastSuccessfulIndex >= 0) {
            this.receiveRoundRobinCursor.put(normalizedExcludedPos, lastSuccessfulIndex);
        }

        if (!simulate && totalInserted > 0) {
            invalidateEnergyQueryCache();
        }
        return totalInserted;
    }

    private long distributeEnergyInRange(long amount, boolean simulate, @Nullable BlockPos excludedPos) {
        return distributeEnergyInRange(amount, simulate, excludedPos, collectEnergyEndpoints(collectTowerCluster(), true));
    }

    private long insertEnergyIntoEndpoint(EnergyEndpoint endpoint, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }

        IEnergyStorage storage = endpoint.storage();
        long directInserted = insertEnergyDirectly(storage, amount, simulate);
        if (directInserted != DIRECT_ENERGY_INSERT_UNAVAILABLE) {
            if (directInserted > 0 || !storage.canReceive()) {
                if (!simulate && directInserted > 0) {
                    notifyDirectEnergyStorageChanged(endpoint);
                }
                return directInserted;
            }
        }
        return storage.receiveEnergy(clampEnergyRequest(amount), simulate);
    }

    private void notifyDirectEnergyStorageChanged(EnergyEndpoint endpoint) {
        ReflectionAccess.invokeNoArgBestEffort(endpoint.storage(), "onEnergyChanged");
        ReflectionAccess.invokeNoArgBestEffort(endpoint.storage(), "onContentsChanged");
        if (this.level == null) {
            return;
        }

        BlockEntity blockEntity = this.level.getBlockEntity(endpoint.pos());
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }

    private static boolean canReceiveEnergy(@Nullable IEnergyStorage storage) {
        return storage != null && (storage.canReceive() || getDirectEnergyStorageTarget(storage).isPresent());
    }

    private static long insertEnergyDirectly(IEnergyStorage storage, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }

        Optional<DirectEnergyStorageTarget> target = getDirectEnergyStorageTarget(storage);
        return target.map(directEnergyStorageTarget -> directEnergyStorageTarget.insert(storage, amount, simulate)).orElse(DIRECT_ENERGY_INSERT_UNAVAILABLE);
    }

    private static Optional<DirectEnergyStorageTarget> getDirectEnergyStorageTarget(IEnergyStorage storage) {
        Optional<DirectEnergyStorageTarget> unwrapped = getUnwrappedDirectEnergyStorageTarget(storage);
        if (unwrapped.isPresent()) {
            return unwrapped;
        }

        return getDirectEnergyStorageAccess(storage.getClass())
                .map(access -> new DirectEnergyStorageTarget(storage, access));
    }

    private static Optional<DirectEnergyStorageTarget> getUnwrappedDirectEnergyStorageTarget(IEnergyStorage storage) {
        Optional<VarHandle> wrapperField = DIRECT_ENERGY_WRAPPER_FIELD_CACHE.computeIfAbsent(storage.getClass(), DataDistributionTowerBlockEntity::resolveDirectEnergyWrapperField);
        if (wrapperField.isEmpty()) {
            return Optional.empty();
        }

        Object target = readDirectEnergyWrapperTarget(wrapperField.get(), storage);
        if (target == null || target == storage) {
            return Optional.empty();
        }

        Optional<DirectEnergyStorageAccess> access = getDirectEnergyStorageAccess(target.getClass());
        if (access.isPresent()) {
            return Optional.of(new DirectEnergyStorageTarget(target, access.get()));
        }

        if (target instanceof IEnergyStorage nestedStorage) {
            return getDirectEnergyStorageTarget(nestedStorage);
        }
        return Optional.empty();
    }

    private static Optional<DirectEnergyStorageAccess> getDirectEnergyStorageAccess(Class<?> storageClass) {
        return DIRECT_ENERGY_STORAGE_ACCESS_CACHE.computeIfAbsent(storageClass, DataDistributionTowerBlockEntity::resolveDirectEnergyStorageAccess);
    }

    private static Optional<DirectEnergyStorageAccess> resolveDirectEnergyStorageAccess(Class<?> storageClass) {
        Optional<Method> insertIgnoringLimit = findDirectInsertIgnoringLimit(storageClass);
        Optional<DirectEnergyAmountMethods> amountMethods = findDirectEnergyAmountMethods(storageClass);
        Optional<VarHandle> storedEnergy = findDirectNumericField(storageClass, DIRECT_ENERGY_FIELD_NAMES, true);
        if (insertIgnoringLimit.isEmpty() && amountMethods.isEmpty() && storedEnergy.isEmpty()) {
            return Optional.empty();
        }

        Optional<VarHandle> capacity = findDirectNumericField(storageClass, DIRECT_ENERGY_CAPACITY_FIELD_NAMES, false);
        return Optional.of(new DirectEnergyStorageAccess(storedEnergy.orElse(null), capacity.orElse(null), insertIgnoringLimit.orElse(null), amountMethods.orElse(null)));
    }

    private static Optional<VarHandle> resolveDirectEnergyWrapperField(Class<?> storageClass) {
        return findDirectObjectField(storageClass, DIRECT_ENERGY_WRAPPER_FIELD_NAMES);
    }

    @Nullable
    private static Object readDirectEnergyWrapperTarget(VarHandle handle, Object storage) {
        try {
            return handle.get(storage);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Optional<Method> findDirectInsertIgnoringLimit(Class<?> owner) {
        Class<?> type = owner;
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod("insertIgnoringLimit", long.class, boolean.class);
                method.setAccessible(true);
                if (method.getReturnType() == long.class) {
                    return Optional.of(method);
                }
                return Optional.empty();
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<VarHandle> findDirectObjectField(Class<?> owner, List<String> fieldNames) {
        for (String fieldName : fieldNames) {
            Class<?> type = owner;
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || field.getType().isPrimitive()) {
                        break;
                    }

                    field.setAccessible(true);
                    return Optional.of(MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflectVarHandle(field));
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                } catch (IllegalAccessException | RuntimeException | LinkageError ignored) {
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<DirectEnergyAmountMethods> findDirectEnergyAmountMethods(Class<?> owner) {
        Optional<Method> getAmount = findDirectNoArgLongMethod(owner, "getAmount");
        Optional<Method> getCapacity = findDirectNoArgLongMethod(owner, "getCapacity");
        Optional<Method> setAmount = findDirectSingleLongMethod(owner, "setAmount", void.class);
        if (getAmount.isEmpty() || getCapacity.isEmpty() || setAmount.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DirectEnergyAmountMethods(getAmount.get(), getCapacity.get(), setAmount.get()));
    }

    private static Optional<Method> findDirectNoArgLongMethod(Class<?> owner, String methodName) {
        Class<?> type = owner;
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                if (method.getReturnType() == long.class) {
                    return Optional.of(method);
                }
                return Optional.empty();
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Method> findDirectSingleLongMethod(Class<?> owner, String methodName, Class<?> returnType) {
        Class<?> type = owner;
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName, long.class);
                method.setAccessible(true);
                if (method.getReturnType() == returnType) {
                    return Optional.of(method);
                }
                return Optional.empty();
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<VarHandle> findDirectNumericField(Class<?> owner, List<String> fieldNames, boolean writable) {
        for (String fieldName : fieldNames) {
            Class<?> type = owner;
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || writable && Modifier.isFinal(modifiers) || !isDirectEnergyFieldType(field.getType())) {
                        break;
                    }

                    field.setAccessible(true);
                    return Optional.of(MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflectVarHandle(field));
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                } catch (IllegalAccessException | RuntimeException | LinkageError ignored) {
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isDirectEnergyFieldType(Class<?> fieldType) {
        return fieldType == int.class || fieldType == long.class;
    }

    private int extractEnergyFromRange(int amount, boolean simulate, @Nullable BlockPos excludedPos) {
        if (simulate) {
            return getCachedSimulatedExtract(amount, excludedPos);
        }
        return clampStoredAmount(extractEnergyFromRangeLong(amount, simulate, excludedPos));
    }

    private int getCachedSimulatedExtract(int amount, @Nullable BlockPos excludedPos) {
        if (amount <= 0 || this.level == null) {
            return 0;
        }

        long gameTime = this.level.getGameTime();
        if (this.cachedSimulatedExtractTick != gameTime) {
            this.cachedSimulatedExtracts.clear();
            this.cachedSimulatedExtractTick = gameTime;
        }

        BlockPos normalizedExcludedPos = normalizeExtractExcludedPos(excludedPos);
        ExtractSimulationKey key = new ExtractSimulationKey(normalizedExcludedPos, amount);
        Integer cached = this.cachedSimulatedExtracts.get(key);
        if (cached != null) {
            this.diagnosticSimulatedCacheHits++;
            return cached;
        }

        this.diagnosticSimulatedCacheMisses++;
        int simulated = clampStoredAmount(extractEnergyFromRangeLong(amount, true, normalizedExcludedPos));
        this.cachedSimulatedExtracts.put(key, simulated);
        return simulated;
    }

    private long extractEnergyFromRangeLong(long amount, boolean simulate, @Nullable BlockPos excludedPos) {
        if (!isTowerActive() || amount <= 0) {
            return 0;
        }

        BlockPos normalizedExcludedPos = normalizeExtractExcludedPos(excludedPos);
        List<EnergyEndpoint> endpoints = collectEnergyEndpoints(false, normalizedExcludedPos);
        this.diagnosticMaxExtractEndpoints = Math.max(this.diagnosticMaxExtractEndpoints, endpoints.size());
        long totalExtracted = 0;
        long remaining = amount;

        if (AE2FluxIntegration.isAvailable()) {
            long extracted = AE2FluxIntegration.extractEnergyFromOwnNetwork(this, remaining, simulate);
            if (extracted > 0) {
                totalExtracted += extracted;
                remaining -= extracted;
            }
        }

        int endpointCount = endpoints.size();
        int startIndex = getExtractStartIndex(normalizedExcludedPos, endpointCount);
        int lastSuccessfulIndex = -1;
        for (int offset = 0; offset < endpointCount; offset++) {
            if (remaining <= 0) {
                break;
            }

            int endpointIndex = (startIndex + offset) % endpointCount;
            EnergyEndpoint endpoint = endpoints.get(endpointIndex);
            IEnergyStorage storage = endpoint.storage();
            if (!storage.canExtract()) {
                continue;
            }

            int extracted = storage.extractEnergy(clampEnergyRequest(remaining), simulate);
            if (extracted > 0) {
                totalExtracted += extracted;
                remaining -= extracted;
                lastSuccessfulIndex = endpointIndex;
            }
        }

        if (lastSuccessfulIndex >= 0) {
            this.extractRoundRobinCursor.put(normalizedExcludedPos, lastSuccessfulIndex);
        }

        if (!simulate && totalExtracted > 0) {
            invalidateEnergyQueryCache();
        }
        return totalExtracted;
    }

    private long getTotalExtractableEnergy(@Nullable BlockPos excludedPos) {
        return getExtractQuerySummary(excludedPos).totalStored();
    }

    private long getTotalEnergyCapacity(@Nullable BlockPos excludedPos) {
        return getExtractQuerySummary(excludedPos).totalCapacity();
    }

    private boolean hasAnyReceiver(@Nullable BlockPos excludedPos) {
        return getReceiveQuerySummary(excludedPos).hasReceiver();
    }

    private boolean hasAnySource(@Nullable BlockPos excludedPos) {
        return getExtractQuerySummary(excludedPos).hasSource();
    }

    private EnergyQuerySummary getExtractQuerySummary(@Nullable BlockPos excludedPos) {
        if (!isTowerActive() || this.level == null) {
            return EnergyQuerySummary.EMPTY;
        }

        BlockPos normalizedExcludedPos = normalizeExtractExcludedPos(excludedPos);
        long gameTime = this.level.getGameTime();
        EnergyQuerySummary cached = this.cachedExtractQuerySummaries.get(normalizedExcludedPos);
        if (cached != null && cached.tick() == gameTime) {
            return cached;
        }

        long totalStored = 0L;
        long totalCapacity = 0L;
        List<EnergyEndpoint> endpoints = collectEnergyEndpoints(false, normalizedExcludedPos);
        for (EnergyEndpoint endpoint : endpoints) {
            totalStored = saturatingAdd(totalStored, endpoint.storage().getEnergyStored());
            totalCapacity = saturatingAdd(totalCapacity, endpoint.storage().getMaxEnergyStored());
        }
        long aeExtractable = 0L;
        if (AE2FluxIntegration.isAvailable()) {
            aeExtractable = AE2FluxIntegration.extractEnergyFromOwnNetwork(this, Long.MAX_VALUE, true);
            totalStored = saturatingAdd(totalStored, aeExtractable);
        }

        EnergyQuerySummary summary = new EnergyQuerySummary(gameTime, totalStored, totalCapacity, !endpoints.isEmpty() || aeExtractable > 0);
        this.cachedExtractQuerySummaries.put(normalizedExcludedPos, summary);
        return summary;
    }

    private ReceiverQuerySummary getReceiveQuerySummary(@Nullable BlockPos excludedPos) {
        if (!isTowerActive() || this.level == null) {
            return ReceiverQuerySummary.EMPTY;
        }

        BlockPos normalizedExcludedPos = normalizeReceiveExcludedPos(excludedPos);
        long gameTime = this.level.getGameTime();
        ReceiverQuerySummary cached = this.cachedReceiveQuerySummaries.get(normalizedExcludedPos);
        if (cached != null && cached.tick() == gameTime) {
            return cached;
        }

        ReceiverQuerySummary summary = new ReceiverQuerySummary(gameTime, !collectEnergyEndpoints(true, normalizedExcludedPos).isEmpty());
        this.cachedReceiveQuerySummaries.put(normalizedExcludedPos, summary);
        return summary;
    }

    private boolean queueLink(BlockPos targetPos, int delay) {
        if (!canMaintainGridLinkTo(targetPos)) {
            return false;
        }

        Integer existingDelay = this.pendingLinkPositions.get(targetPos);
        if (existingDelay == null || existingDelay > delay) {
            this.pendingLinkPositions.put(targetPos.immutable(), delay);
            return true;
        }
        return false;
    }

    private void cleanupInvalidBoundTargets() {
        if (this.level == null) {
            return;
        }

        ArrayList<BlockPos> invalidPositions = new ArrayList<>();
        for (BlockPos pos : this.linkedPositions) {
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
        this.pendingLinkPositions.remove(targetPos);
        this.linkedPositions.remove(targetPos);
        this.targetTransferModes.remove(targetPos);
        destroyTargetConnections(targetPos);

        this.invalidateEndpointCache();
        this.invalidateClusterCache();
        this.setChanged();
    }

    private void destroyTargetConnections(BlockPos targetPos) {
        List<IGridConnection> existingConnections = this.linkedConnections.remove(targetPos);
        if (existingConnections != null) {
            for (IGridConnection connection : existingConnections) {
                if (connection != null) {
                    connection.destroy();
                }
            }
        }
    }

    private void destroyAllConnections() {
        for (List<IGridConnection> connections : this.linkedConnections.values()) {
            for (IGridConnection connection : connections) {
                if (connection != null) {
                    connection.destroy();
                }
            }
        }
        this.linkedConnections.clear();
        this.invalidateClusterCache();
    }

    private int reconnectTarget(IGridNode selfNode, BlockPos targetPos, List<IGridNode> targetNodes) {
        destroyTargetConnections(targetPos);

        if (targetNodes.isEmpty()) {
            this.setChanged();
            return 0;
        }

        ArrayList<IGridConnection> newConnections = new ArrayList<>();
        for (IGridNode targetNode : targetNodes) {
            try {
                newConnections.add(GridHelper.createConnection(selfNode, targetNode));
            } catch (IllegalStateException ignored) {}
        }

        if (newConnections.isEmpty()) {
            this.setChanged();
            return 0;
        }

        this.linkedConnections.put(targetPos.immutable(), newConnections);
        this.linkedPositions.add(targetPos.immutable());
        this.invalidateEndpointCache();
        this.invalidateClusterCache();
        this.setChanged();
        return newConnections.size();
    }

    private List<IGridNode> getLinkableTargetNodes(IGridNode selfNode, BlockPos targetPos) {
        if (!canMaintainGridLinkTo(targetPos)) {
            return List.of();
        }

        List<IGridNode> targetNodes = getConnectableNodes(this.level, targetPos);
        if (targetNodes.isEmpty()) {
            return List.of();
        }

        boolean towerTarget = this.level.getBlockEntity(targetPos) instanceof DataDistributionTowerBlockEntity;
        ArrayList<IGridNode> linkableNodes = new ArrayList<>();
        for (IGridNode targetNode : targetNodes) {
            if (isLinkableTargetNode(selfNode, targetNode, towerTarget)) {
                linkableNodes.add(targetNode);
            }
        }
        return List.copyOf(linkableNodes);
    }

    private boolean isLinkableTargetNode(IGridNode selfNode, @Nullable IGridNode targetNode, boolean towerTarget) {
        if (targetNode == null || targetNode == selfNode) {
            return false;
        }
        if (!towerTarget && targetNode.isOnline()) {
            return false;
        }

        IGrid targetGrid = targetNode.getGrid();
        IGrid selfGrid = selfNode.getGrid();
        if (targetGrid != null && selfGrid != null) {
            if (targetGrid == selfGrid) {
                return !targetNode.meetsChannelRequirements();
            }

            ControllerState targetControllerState = targetGrid.getPathingService().getControllerState();
            ControllerState selfControllerState = selfGrid.getPathingService().getControllerState();
            return targetControllerState == ControllerState.NO_CONTROLLER || selfControllerState == ControllerState.NO_CONTROLLER;
        }
        return true;
    }

    private void requeuePersistedLinks() {
        if (this.linkedPositions.isEmpty()) {
            return;
        }

        List<BlockPos> persisted = List.copyOf(this.linkedPositions);
        this.pendingLinkPositions.clear();
        destroyAllConnections();
        for (BlockPos pos : persisted) {
            queueLink(pos, 0);
        }
    }

    private void resetAutoDiscoveryCooldown() {
        this.autoDiscoveryCooldown = Math.floorMod(this.worldPosition.hashCode(), INITIAL_DISCOVERY_STAGGER_TICKS);
    }

    private void processAutoDiscovery() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        if (!allowsAutomaticRangeConnections()) {
            return;
        }

        if (this.autoDiscoveryCooldown > 0) {
            this.autoDiscoveryCooldown--;
            return;
        }

        this.autoDiscoveryCooldown = AUTO_DISCOVERY_INTERVAL_TICKS;
        scanNearbyConnectableNodes();
    }

    public static List<IGridNode> getConnectableNodes(Level level, BlockPos pos) {
        LinkedHashSet<IGridNode> nodes = new LinkedHashSet<>();
        IInWorldGridNodeHost nodeHost = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
        if (nodeHost == null) {
            return List.of();
        }

        if (nodeHost instanceof CableBusBlockEntity cableBusBlockEntity) {
            CableBusContainer cableBus = cableBusBlockEntity.getCableBus();
            IPart center = cableBus.getPart(null);
            if (center != null) {
                nodes.add(center.getGridNode());
            }
            for (var direction : net.minecraft.core.Direction.values()) {
                IPart part = cableBus.getPart(direction);
                if (part != null) {
                    nodes.add(part.getGridNode());
                    nodes.add(cableBus.getGridNode(direction));
                }
            }
        } else {
            for (var direction : net.minecraft.core.Direction.values()) {
                IGridNode node = nodeHost.getGridNode(direction);
                if (node != null) {
                    nodes.add(node);
                    break;
                }
            }
        }

        nodes.removeIf(Objects::isNull);
        return List.copyOf(nodes);
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

    private static int clampEnergyRequest(long amount) {
        if (amount <= 0) {
            return 0;
        }
        return (int) Math.min(amount, Integer.MAX_VALUE);
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

    private static BlockPos normalizeTargetPos(BlockPos targetPos) {
        return targetPos.immutable();
    }

    @Nullable
    private BlockPos normalizeExtractExcludedPos(@Nullable BlockPos excludedPos) {
        BlockPos normalizedExcludedPos = normalizeExcludedPos(excludedPos);
        if (normalizedExcludedPos == null) {
            return null;
        }

        for (EnergyEndpoint endpoint : getCachedResolvedEnergyEndpoints(false)) {
            if (normalizedExcludedPos.equals(endpoint.pos())) {
                return normalizedExcludedPos;
            }
        }
        return null;
    }

    @Nullable
    private BlockPos normalizeReceiveExcludedPos(@Nullable BlockPos excludedPos) {
        BlockPos normalizedExcludedPos = normalizeExcludedPos(excludedPos);
        if (normalizedExcludedPos == null) {
            return null;
        }

        for (EnergyEndpoint endpoint : getCachedResolvedEnergyEndpoints(true)) {
            if (normalizedExcludedPos.equals(endpoint.pos())) {
                return normalizedExcludedPos;
            }
        }
        return null;
    }

    private int getExtractStartIndex(@Nullable BlockPos excludedPos, int endpointCount) {
        if (endpointCount <= 0) {
            return 0;
        }
        return Math.floorMod(this.extractRoundRobinCursor.getOrDefault(excludedPos, 0), endpointCount);
    }

    private int getReceiveStartIndex(@Nullable BlockPos excludedPos, int endpointCount) {
        if (endpointCount <= 0) {
            return 0;
        }
        return Math.floorMod(this.receiveRoundRobinCursor.getOrDefault(excludedPos, 0), endpointCount);
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

    private static long divideCeil(long dividend, int divisor) {
        if (dividend <= 0 || divisor <= 0) {
            return 0;
        }
        return 1L + (dividend - 1L) / divisor;
    }

    private static String formatFeAmount(long amount) {
        if (amount >= 1_000_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fG", amount / 1_000_000_000.0);
        }
        if (amount >= 1_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 1_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fk", amount / 1_000.0);
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
            scanNearbyConnectableNodes();
        }
    }

    private void refreshConnectionTargets() {
        if (this.level == null || this.level.isClientSide()) {
            invalidateEndpointCache();
            invalidateClusterCache();
            return;
        }

        ArrayList<BlockPos> retainedTargets = new ArrayList<>(this.linkedPositions);
        for (BlockPos pos : this.pendingLinkPositions.keySet()) {
            if (!retainedTargets.contains(pos)) {
                retainedTargets.add(pos);
            }
        }

        this.pendingLinkPositions.clear();
        destroyAllConnections();
        invalidateEndpointCache();
        invalidateClusterCache();

        for (BlockPos pos : retainedTargets) {
            if (canMaintainGridLinkTo(pos)) {
                queueLink(pos, 0);
            }
        }

        if (allowsAutomaticRangeConnections()) {
            scanNearbyConnectableNodes();
        }
    }

    private boolean canMaintainGridLinkTo(BlockPos targetPos) {
        if (this.level == null || this.worldPosition.equals(targetPos) || !isWithinTowerCoverage(targetPos)) {
            return false;
        }
        if (getTargetTransferMode(targetPos) == TargetTransferMode.DISABLED) {
            return false;
        }

        BlockEntity blockEntity = this.level.getBlockEntity(targetPos);
        if (blockEntity instanceof DataDistributionTowerBlockEntity) {
            return true;
        }

        return allowsAeTargets() && needsAeChannelLink(targetPos);
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

    private boolean targetAllowsAe(BlockPos targetPos) {
        if (getTargetTransferMode(targetPos) == TargetTransferMode.DISABLED) {
            return false;
        }
        return needsAeChannelLink(targetPos);
    }

    private boolean targetAllowsFe(BlockPos targetPos) {
        if (getTargetTransferMode(targetPos) == TargetTransferMode.DISABLED) {
            return false;
        }
        return hasAnyEnergyCapability(targetPos);
    }

    private boolean hasAeNodeCapability(BlockPos targetPos) {
        return this.level != null && this.level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, targetPos, null) != null;
    }

    private boolean needsAeChannelLink(BlockPos targetPos) {
        if (this.level == null || !hasAeNodeCapability(targetPos)) {
            return false;
        }

        for (IGridNode node : getConnectableNodes(this.level, targetPos)) {
            if (node != null && !node.isOnline()) {
                return true;
            }
        }
        return false;
    }

    private void pruneTargetsOutsideRange() {
        ArrayList<BlockPos> toRemove = new ArrayList<>();
        for (BlockPos pos : this.linkedPositions) {
            if (!isWithinTowerCoverage(pos)) {
                toRemove.add(pos);
            }
        }
        for (BlockPos pos : this.pendingLinkPositions.keySet()) {
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

    private record EnergyEndpointKey(BlockPos pos, @Nullable net.minecraft.core.Direction side) {}

    private record DirectEnergyStorageTarget(Object target, DirectEnergyStorageAccess access) {

        private long insert(IEnergyStorage storage, long amount, boolean simulate) {
            return this.access.insert(storage, this.target, amount, simulate);
        }
    }

    private record DirectEnergyAmountMethods(Method getAmount, Method getCapacity, Method setAmount) {}

    private record DirectEnergyStorageAccess(@Nullable VarHandle storedEnergy, @Nullable VarHandle capacity,
                                             @Nullable Method insertIgnoringLimit,
                                             @Nullable DirectEnergyAmountMethods amountMethods) {

        private long insert(IEnergyStorage storage, Object target, long amount, boolean simulate) {
            long methodInserted = insertIgnoringLimit(target, amount, simulate);
            if (methodInserted != DIRECT_ENERGY_INSERT_UNAVAILABLE) {
                if (!simulate && methodInserted > 0) {
                    notifyDirectTargetChanged(target);
                }
                return methodInserted;
            }

            methodInserted = insertByAmountMethods(storage, target, amount, simulate);
            if (methodInserted != DIRECT_ENERGY_INSERT_UNAVAILABLE) {
                return methodInserted;
            }

            if (this.storedEnergy == null) {
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }

            Long current = readEnergyAmount(this.storedEnergy, target);
            if (current == null || current < 0 || !matchesReportedAmount(current, storage.getEnergyStored())) {
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }

            Long directCapacity = this.capacity == null ? null : readEnergyAmount(this.capacity, target);
            if (directCapacity != null && directCapacity < 0) {
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }

            long maxStored = directCapacity == null ? Math.max(0, storage.getMaxEnergyStored()) : directCapacity;
            if (directCapacity != null && !matchesReportedCapacity(directCapacity, storage.getMaxEnergyStored())) {
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }

            maxStored = Math.min(maxStored, getMaxStoredValue());
            if (maxStored <= current) {
                return 0;
            }

            long inserted = Math.min(amount, maxStored - current);
            if (inserted <= 0) {
                return 0;
            }
            if (simulate) {
                return inserted;
            }

            long targetAmount = current + inserted;
            if (!writeEnergyAmount(this.storedEnergy, target, targetAmount)) {
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }

            Long updated = readEnergyAmount(this.storedEnergy, target);
            if (updated == null || updated != targetAmount || !matchesReportedAmount(targetAmount, storage.getEnergyStored())) {
                writeEnergyAmount(this.storedEnergy, target, current);
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }
            notifyDirectTargetChanged(target);
            return inserted;
        }

        private long getMaxStoredValue() {
            return this.storedEnergy.varType() == int.class ? Integer.MAX_VALUE : Long.MAX_VALUE;
        }

        private long insertByAmountMethods(IEnergyStorage storage, Object target, long amount, boolean simulate) {
            if (this.amountMethods == null) {
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }

            Long current = invokeLong(this.amountMethods.getAmount(), target);
            if (current == null || current < 0 || !matchesReportedAmount(current, storage.getEnergyStored())) {
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }

            Long capacity = invokeLong(this.amountMethods.getCapacity(), target);
            if (capacity == null || capacity < 0 || !matchesReportedCapacity(capacity, storage.getMaxEnergyStored())) {
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }
            if (capacity <= current) {
                return 0;
            }

            long inserted = Math.min(amount, capacity - current);
            if (inserted <= 0) {
                return 0;
            }
            if (simulate) {
                return inserted;
            }

            long targetAmount = current + inserted;
            ReflectionAccess.invoke(this.amountMethods.setAmount(), target, targetAmount);
            Long updated = invokeLong(this.amountMethods.getAmount(), target);
            if (updated == null || updated != targetAmount || !matchesReportedAmount(targetAmount, storage.getEnergyStored())) {
                ReflectionAccess.invoke(this.amountMethods.setAmount(), target, current);
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }
            notifyDirectTargetChanged(target);
            return inserted;
        }

        private long insertIgnoringLimit(Object target, long amount, boolean simulate) {
            if (this.insertIgnoringLimit == null) {
                return DIRECT_ENERGY_INSERT_UNAVAILABLE;
            }

            Object result = ReflectionAccess.invoke(this.insertIgnoringLimit, target, amount, simulate);
            return result instanceof Number number ? number.longValue() : DIRECT_ENERGY_INSERT_UNAVAILABLE;
        }

        @Nullable
        private static Long invokeLong(Method method, Object target) {
            Object result = ReflectionAccess.invoke(method, target);
            return result instanceof Number number ? number.longValue() : null;
        }

        @Nullable
        private static Long readEnergyAmount(VarHandle handle, Object target) {
            try {
                Object value = handle.get(target);
                return value instanceof Number number ? number.longValue() : null;
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private static boolean writeEnergyAmount(VarHandle handle, Object target, long amount) {
            try {
                if (handle.varType() == int.class) {
                    if (amount > Integer.MAX_VALUE || amount < Integer.MIN_VALUE) {
                        return false;
                    }
                    handle.set(target, (int) amount);
                } else if (handle.varType() == long.class) {
                    handle.set(target, amount);
                } else {
                    return false;
                }
                return true;
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        private static boolean matchesReportedAmount(long directAmount, int reportedAmount) {
            return reportedAmount == clampStoredAmount(directAmount);
        }

        private static boolean matchesReportedCapacity(long directCapacity, int reportedCapacity) {
            return reportedCapacity <= 0 || matchesReportedAmount(directCapacity, reportedCapacity);
        }

        private static void notifyDirectTargetChanged(Object target) {
            ReflectionAccess.invokeNoArgBestEffort(target, "update");
        }
    }

    private record EnergyEndpoint(BlockPos pos, @Nullable net.minecraft.core.Direction side, IEnergyStorage storage) {}

    private record ExtractSimulationKey(@Nullable BlockPos excludedPos, int amount) {}

    private record EnergyQuerySummary(long tick, long totalStored, long totalCapacity, boolean hasSource) {

        private static final EnergyQuerySummary EMPTY = new EnergyQuerySummary(Long.MIN_VALUE, 0L, 0L, false);
    }

    private record ReceiverQuerySummary(long tick, boolean hasReceiver) {

        private static final ReceiverQuerySummary EMPTY = new ReceiverQuerySummary(Long.MIN_VALUE, false);
    }

    private record TransferScanSnapshot(long tick, long aeExtractable, List<EnergyEndpoint> extractEndpoints,
                                        List<EnergyEndpoint> receiveEndpoints) {

        private static final TransferScanSnapshot EMPTY = new TransferScanSnapshot(Long.MIN_VALUE, 0L, List.of(), List.of());
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

        public TargetTransferMode next() {
            return switch (this) {
                case AUTO -> DISABLED;
                case DISABLED -> AUTO;
            };
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

        public RangeAdjustmentMode next() {
            return this == POINT ? SCOPE : POINT;
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

    private record CableBusDisplayPart(IPart part, @Nullable net.minecraft.core.Direction direction) {}

    private record DisplayTarget(BlockPos pos, TargetKind kind) {}

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
