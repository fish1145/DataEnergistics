package com.fish_dan_.data_energistics.blockentity.tower.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.TowerChannelCapacity;
import com.fish_dan_.data_energistics.ae2.TowerChannelCapacityImpl;
import com.fish_dan_.data_energistics.ae2.VirtualGridBridge;
import com.fish_dan_.data_energistics.ae2.VirtualGridBridgeException;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBinding;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBindingRuntimeSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBindingSource;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerRuntimeKey;
import com.fish_dan_.data_energistics.blockentity.tower.network.discovery.CapabilityExposedTowerAeTargetResolver;
import com.fish_dan_.data_energistics.blockentity.tower.network.discovery.TowerResolvedDevice;
import com.fish_dan_.data_energistics.blockentity.tower.network.discovery.TowerResolvedGrid;
import com.fish_dan_.data_energistics.blockentity.tower.network.discovery.TowerTargetDiscoveryMode;
import com.fish_dan_.data_energistics.blockentity.tower.network.discovery.TowerTargetResolution;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.AppFluxGridEnergyTransferEndpoint;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.CapabilityEnergyTransferEndpoint;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.CapabilityTowerDomainEnergyResolver;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.CompensatingTowerEnergyTransaction;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.MultiRouteEnergyTransferEndpoint;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.TowerDomainEnergyEndpoint;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.TowerEnergyLocation;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.TowerEnergyTransactionResult;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.TowerEnergyTransferEndpoint;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualChannelBindingAllocation;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualChannelBindingRequest;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualChannelBindingSource;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualChannelCapacity;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualChannelLedger;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualChannelLedgerImpl;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualChannelLedgerSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualChannelNodeAllocation;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualChannelNodeRequest;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualChannelNodeState;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualGridCandidateState;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualGridCandidateStatus;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualGridOwner;
import com.fish_dan_.data_energistics.blockentity.tower.virtual.VirtualGridOwnershipSnapshot;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.appflux.AE2FluxIntegration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.stacks.AEItemKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Grid-local coordinator for target ownership, virtual-channel leases, and service-bridge membership.
 */
public final class TowerNetworkDomainImpl implements TowerNetworkDomain, IGridServiceProvider {

    private static final int SAFETY_RESCAN_INTERVAL_TICKS = 100;
    private static final int BRIDGE_FAILURE_LOG_INTERVAL_TICKS = 100;
    private static final int ENERGY_FAILURE_LOG_INTERVAL_TICKS = 100;
    private static final TowerEnergyTransactionResult EMPTY_ENERGY_RESULT = new TowerEnergyTransactionResult(
            List.of(), 0, 0, 0, false, "");
    private static final Comparator<TowerEnergyEndpointId> ENERGY_ENDPOINT_ORDER = Comparator
            .comparing((TowerEnergyEndpointId endpoint) -> endpoint.dimensionId().toString())
            .thenComparingInt(endpoint -> endpoint.pos().getX())
            .thenComparingInt(endpoint -> endpoint.pos().getY())
            .thenComparingInt(endpoint -> endpoint.pos().getZ())
            .thenComparingInt(endpoint -> endpoint.side() == null ? Direction.values().length : endpoint.side().ordinal())
            .thenComparingInt(TowerEnergyEndpointId::storageIdentity);

    private final IGrid grid;
    private final Map<IGridNode, Long> registrationOrders = new IdentityHashMap<>();
    private final Map<TowerRuntimeKey, TowerNetworkParticipant> towers = new LinkedHashMap<>();
    private final Map<TowerRuntimeKey, TowerNetworkTowerSnapshot> towerSnapshots = new HashMap<>();
    private final Set<IGrid> attachedTargets = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<IGrid, TowerRuntimeKey> attachedOwners = new IdentityHashMap<>();
    private final Map<IGrid, Long> lastBridgeFailureLogTicks = new IdentityHashMap<>();
    private final CapabilityExposedTowerAeTargetResolver targetResolver = new CapabilityExposedTowerAeTargetResolver();
    private final CapabilityTowerDomainEnergyResolver energyResolver = new CapabilityTowerDomainEnergyResolver();
    private final CompensatingTowerEnergyTransaction energyTransaction = new CompensatingTowerEnergyTransaction();
    private final TowerChannelCapacity capacityCalculator = new TowerChannelCapacityImpl();
    private List<IGridNode> cachedLocalNodes = List.of();
    private List<TowerEnergyTransferEndpoint> energyEndpoints = List.of();
    private TowerEnergyTransactionResult lastEnergyResult = EMPTY_ENERGY_RESULT;
    private long nextRegistrationOrder;
    private long revision;
    private long reconciledRevision = Long.MIN_VALUE;
    private long lastSafetyRescanTick = Long.MIN_VALUE;
    private long lastPhysicalUsage = Long.MIN_VALUE;
    private long lastOwnershipRevision = Long.MIN_VALUE;
    private long lastEnergyTransactionTick = Long.MIN_VALUE;
    private long lastEnergyFailureLogTick = Long.MIN_VALUE;
    private VirtualChannelCapacity lastCapacity = VirtualChannelCapacity.limited(0);
    private boolean localNodesCacheValid = true;
    private boolean reconciling;

    /**
     * Creates the service instance constructed by AE for one grid.
     *
     * @param grid owning physical grid
     */
    public TowerNetworkDomainImpl(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public IGrid grid() {
        return this.grid;
    }

    @Override
    public long revision() {
        return this.revision;
    }

    @Override
    public void invalidate(TowerNetworkDomainChange reason) {
        this.revision = Math.incrementExact(this.revision);
    }

    @Override
    public List<IGridNode> localNodes() {
        if (this.localNodesCacheValid) {
            return this.cachedLocalNodes;
        }
        ArrayList<Map.Entry<IGridNode, Long>> entries = new ArrayList<>(this.registrationOrders.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        this.cachedLocalNodes = entries.stream().map(Map.Entry::getKey).toList();
        this.localNodesCacheValid = true;
        return this.cachedLocalNodes;
    }

    @Override
    public long registrationOrder(IGridNode node) {
        Long order = this.registrationOrders.get(node);
        if (order == null) {
            throw new IllegalArgumentException("Grid node is not registered in this tower domain");
        }
        return order;
    }

    @Override
    public void registerTower(TowerNetworkParticipant tower) {
        if (tower.towerGrid() != this.grid) {
            throw new IllegalArgumentException("Tower participant belongs to another physical grid");
        }
        TowerNetworkParticipant previous = this.towers.put(tower.towerKey(), tower);
        if (previous != null && previous != tower) {
            throw new IllegalStateException("Two loaded tower participants share identity " + tower.towerKey());
        }
        if (previous == null) {
            invalidate(TowerNetworkDomainChange.TOWER);
        }
    }

    @Override
    public void unregisterTower(TowerNetworkParticipant tower) {
        if (this.towers.get(tower.towerKey()) != tower) {
            return;
        }
        this.towers.remove(tower.towerKey());
        this.towerSnapshots.remove(tower.towerKey());
        MinecraftServer server = tower.towerLevel().getServer();
        TowerGridOwnershipRegistry.markTowerUnavailable(server, tower.towerKey());
        for (IGrid targetGrid : List.copyOf(this.attachedTargets)) {
            if (tower.towerKey().equals(this.attachedOwners.get(targetGrid))) {
                dataEnergistics$detachTarget(targetGrid);
            }
        }
        invalidate(TowerNetworkDomainChange.TOWER);
    }

    @Override
    public Optional<TowerNetworkTowerSnapshot> towerSnapshot(TowerRuntimeKey towerKey) {
        return Optional.ofNullable(this.towerSnapshots.get(towerKey));
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        if (!this.registrationOrders.containsKey(gridNode)) {
            this.registrationOrders.put(gridNode, this.nextRegistrationOrder);
            this.nextRegistrationOrder = Math.incrementExact(this.nextRegistrationOrder);
            this.localNodesCacheValid = false;
            invalidate(TowerNetworkDomainChange.PHYSICAL_NODE);
        }
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        if (this.registrationOrders.remove(gridNode) != null) {
            this.localNodesCacheValid = false;
            invalidate(TowerNetworkDomainChange.PHYSICAL_NODE);
        }
    }

    @Override
    public void onServerEndTick() {
        if (this.reconciling || this.grid.isEmpty() || ((VirtualGridBridge) this.grid).virtualPrimaryGrid() != null) {
            return;
        }
        long gameTime = this.grid.getPivot().getLevel().getGameTime();
        MinecraftServer server = this.grid.getPivot().getLevel().getServer();
        VirtualChannelCapacity capacity = dataEnergistics$currentCapacity();
        long physicalUsage = this.grid.getPathingService().getUsedChannels();
        long ownershipRevision = TowerGridOwnershipRegistry.revision(server);
        boolean safetyRescanDue = this.lastSafetyRescanTick == Long.MIN_VALUE || gameTime - this.lastSafetyRescanTick >= SAFETY_RESCAN_INTERVAL_TICKS;
        boolean reconcileDue = this.reconciledRevision != this.revision || this.lastPhysicalUsage != physicalUsage || this.lastOwnershipRevision != ownershipRevision || !this.lastCapacity.equals(capacity) || safetyRescanDue;
        if (reconcileDue) {
            long reconciliationRevision = this.revision;
            this.reconciling = true;
            try {
                dataEnergistics$reconcile(capacity, physicalUsage, gameTime);
                this.lastCapacity = capacity;
                this.lastPhysicalUsage = physicalUsage;
                this.lastOwnershipRevision = TowerGridOwnershipRegistry.revision(server);
                this.lastSafetyRescanTick = gameTime;
                this.reconciledRevision = reconciliationRevision;
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error("Failed to reconcile tower network domain for {}", this.grid, exception);
            } finally {
                this.reconciling = false;
            }
        } else {
            dataEnergistics$executeEnergyTransaction(gameTime);
        }
    }

    private void dataEnergistics$reconcile(
                                           VirtualChannelCapacity capacity, long physicalUsage, long gameTime) {
        MinecraftServer server = this.grid.getPivot().getLevel().getServer();
        List<TowerWork> towerWorks = dataEnergistics$resolveTowers();
        for (TowerWork towerWork : towerWorks) {
            Set<IGrid> candidateTargets = Collections.newSetFromMap(new IdentityHashMap<>());
            candidateTargets.addAll(towerWork.bindingByTarget().keySet());
            TowerGridOwnershipRegistry.replaceTowerCandidates(
                    server,
                    towerWork.participant().towerKey(),
                    this.grid,
                    towerWork.participant().isTowerNetworkActive(),
                    candidateTargets);
        }

        VirtualGridOwnershipSnapshot<IGrid, TowerRuntimeKey> ownership = TowerGridOwnershipRegistry.snapshot(server);
        dataEnergistics$releaseStaleGlobalAttachments(ownership);

        Map<TowerRuntimeKey, TowerWork> worksByTower = new HashMap<>();
        for (TowerWork towerWork : towerWorks) {
            worksByTower.put(towerWork.participant().towerKey(), towerWork);
        }
        Map<IGrid, OwnedGridWork> ownedTargets = new IdentityHashMap<>();
        for (VirtualGridOwner<IGrid, TowerRuntimeKey> owner : ownership.owners()) {
            if (owner.sourceGrid() != this.grid) {
                continue;
            }
            TowerWork towerWork = worksByTower.get(owner.towerKey());
            if (towerWork == null) {
                continue;
            }
            BindingTargetWork bindingTargetWork = towerWork.bindingByTarget().get(owner.targetGrid());
            if (bindingTargetWork != null) {
                ownedTargets.put(
                        owner.targetGrid(),
                        new OwnedGridWork(
                                towerWork,
                                bindingTargetWork.bindingWork(),
                                owner.targetGrid(),
                                bindingTargetWork.resolvedGrid()));
            }
        }

        for (IGrid attachedTarget : List.copyOf(this.attachedTargets)) {
            if (!ownedTargets.containsKey(attachedTarget)) {
                dataEnergistics$detachTarget(attachedTarget);
            }
        }

        List<DeviceWork> orderedDevices = dataEnergistics$orderedDevices(ownedTargets);
        VirtualChannelLedger<DeviceLeaseKey, IGridNode> ledger = new VirtualChannelLedgerImpl<>(capacity);
        ledger.setPhysicalChannelUsage(physicalUsage);
        Map<DeviceLeaseKey, DeviceWork> devicesByLease = new LinkedHashMap<>();
        long manualOrder = 0;
        long automaticOrder = 0;
        for (DeviceWork deviceWork : orderedDevices) {
            DeviceLeaseKey leaseKey = new DeviceLeaseKey(
                    deviceWork.towerWork().participant().towerKey(),
                    deviceWork.bindingWork().binding().anchor(),
                    deviceWork.device().key(),
                    deviceWork.targetGrid());
            VirtualChannelBindingSource source = deviceWork.bindingWork().binding().source() == TowerBindingSource.MANUAL ? VirtualChannelBindingSource.MANUAL : VirtualChannelBindingSource.AUTOMATIC;
            long queueOrder = source == VirtualChannelBindingSource.MANUAL ? manualOrder++ : automaticOrder++;
            boolean enabled = deviceWork.bindingWork().binding().enabled() && !deviceWork.bindingWork().binding().disabledDeviceKeys().contains(deviceWork.device().key());
            ledger.upsertBinding(new VirtualChannelBindingRequest<>(
                    leaseKey,
                    source,
                    queueOrder,
                    enabled,
                    List.of(new VirtualChannelNodeRequest<>(
                            deviceWork.device().node(), 0, deviceWork.device().requiresChannel()))));
            devicesByLease.put(leaseKey, deviceWork);
        }
        VirtualChannelLedgerSnapshot<DeviceLeaseKey, IGridNode> channelSnapshot = ledger.snapshot();

        Map<DeviceLeaseKey, VirtualChannelNodeAllocation<IGridNode>> allocations = new LinkedHashMap<>();
        for (VirtualChannelBindingAllocation<DeviceLeaseKey, IGridNode> binding : channelSnapshot.bindings()) {
            allocations.put(binding.bindingKey(), binding.nodes().getFirst());
        }
        Map<IGrid, ArrayList<IGridNode>> activeNodesByTarget = new IdentityHashMap<>();
        for (Map.Entry<DeviceLeaseKey, DeviceWork> entry : devicesByLease.entrySet()) {
            VirtualChannelNodeState state = allocations.get(entry.getKey()).state();
            if (state != VirtualChannelNodeState.LEASED && state != VirtualChannelNodeState.AVAILABLE_WITHOUT_CHANNEL) {
                continue;
            }
            DeviceWork deviceWork = entry.getValue();
            activeNodesByTarget.computeIfAbsent(deviceWork.targetGrid(), ignored -> new ArrayList<>())
                    .add(deviceWork.device().node());
        }
        Set<IGrid> bridgeFailures = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<IGrid, OwnedGridWork> entry : ownedTargets.entrySet()) {
            IGrid targetGrid = entry.getKey();
            List<IGridNode> allNodes = entry.getValue().resolvedGrid().devices().stream()
                    .map(TowerResolvedDevice::node)
                    .toList();
            List<IGridNode> activeNodes = activeNodesByTarget.get(targetGrid);
            if (activeNodes == null) {
                activeNodes = List.of();
            }
            try {
                ((VirtualGridBridge) targetGrid).replaceVirtualMembers(this.grid, allNodes, activeNodes);
                this.attachedTargets.add(targetGrid);
                this.attachedOwners.put(targetGrid, entry.getValue().towerWork().participant().towerKey());
                this.lastBridgeFailureLogTicks.remove(targetGrid);
            } catch (VirtualGridBridgeException exception) {
                bridgeFailures.add(targetGrid);
                try {
                    ((VirtualGridBridge) targetGrid).clearVirtualMembers();
                } catch (VirtualGridBridgeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
                dataEnergistics$logBridgeFailure(targetGrid, gameTime, "attach", exception);
                if (((VirtualGridBridge) targetGrid).virtualPrimaryGrid() == this.grid) {
                    this.attachedTargets.add(targetGrid);
                    this.attachedOwners.put(
                            targetGrid, entry.getValue().towerWork().participant().towerKey());
                } else {
                    this.attachedTargets.remove(targetGrid);
                    this.attachedOwners.remove(targetGrid);
                }
            }
        }

        TowerChannelOverview overview = new TowerChannelOverview(
                capacity.finiteLimit(),
                channelSnapshot.physicalChannelUsage(),
                channelSnapshot.virtualChannelUsage(),
                channelSnapshot.remainingChannelCapacity());
        this.energyEndpoints = dataEnergistics$buildEnergyTopology(
                towerWorks, devicesByLease, allocations, bridgeFailures);
        this.lastEnergyTransactionTick = Long.MIN_VALUE;
        TowerEnergyTransactionResult energyResult = dataEnergistics$executeEnergyTransaction(gameTime);
        dataEnergistics$publishSnapshots(
                towerWorks,
                ownership,
                devicesByLease,
                allocations,
                bridgeFailures,
                overview,
                energyResult.snapshots());
    }

    /**
     * Builds one stable cross-dimensional endpoint topology shared by every active tower on this primary grid.
     */
    private List<TowerEnergyTransferEndpoint> dataEnergistics$buildEnergyTopology(
                                                                                  List<TowerWork> towerWorks,
                                                                                  Map<DeviceLeaseKey, DeviceWork> devicesByLease,
                                                                                  Map<DeviceLeaseKey, VirtualChannelNodeAllocation<IGridNode>> allocations,
                                                                                  Set<IGrid> bridgeFailures) {
        Map<EnergyLocationKey, TowerEnergyLocation> locations = new LinkedHashMap<>();
        for (TowerWork towerWork : towerWorks) {
            TowerNetworkParticipant participant = towerWork.participant();
            if (!participant.isTowerNetworkActive() || !participant.towerAllowsFe()) {
                continue;
            }
            for (TowerEnergyLocation location : participant.towerEnergyLocations()) {
                EnergyLocationKey key = new EnergyLocationKey(
                        location.level().dimension().location(), location.position());
                locations.putIfAbsent(key, location);
            }
        }

        for (Map.Entry<DeviceLeaseKey, DeviceWork> entry : devicesByLease.entrySet()) {
            DeviceWork work = entry.getValue();
            TowerNetworkParticipant participant = work.towerWork().participant();
            VirtualChannelNodeState state = allocations.get(entry.getKey()).state();
            BlockPos position = work.device().key().position();
            if (position == null || bridgeFailures.contains(work.targetGrid()) || !participant.isTowerNetworkActive() || !participant.towerAllowsFe() || state == VirtualChannelNodeState.WAITING_CHANNEL || state == VirtualChannelNodeState.DISABLED) {
                continue;
            }
            Level nodeLevel = work.device().node().getLevel();
            if (!nodeLevel.dimension().location().equals(work.device().key().dimensionId()) || !nodeLevel.isLoaded(position)) {
                continue;
            }
            EnergyLocationKey key = new EnergyLocationKey(
                    nodeLevel.dimension().location(), position);
            locations.putIfAbsent(key, new TowerEnergyLocation(nodeLevel, position));
        }

        ArrayList<Map.Entry<EnergyLocationKey, TowerEnergyLocation>> orderedLocations = new ArrayList<>(locations.entrySet());
        orderedLocations.sort(Map.Entry.comparingByKey());
        IdentityHashMap<Object, ArrayList<TowerEnergyTransferEndpoint>> routesByStorage = new IdentityHashMap<>();
        ArrayList<ArrayList<TowerEnergyTransferEndpoint>> orderedRouteGroups = new ArrayList<>();
        for (Map.Entry<EnergyLocationKey, TowerEnergyLocation> entry : orderedLocations) {
            for (TowerDomainEnergyEndpoint endpoint : this.energyResolver.resolve(entry.getValue())) {
                dataEnergistics$addEnergyRoute(
                        routesByStorage,
                        orderedRouteGroups,
                        endpoint.storageIdentity(),
                        new CapabilityEnergyTransferEndpoint(endpoint));
            }
        }

        if (ModFlags.isAppFluxEnergySupportLoaded()) {
            for (TowerWork towerWork : towerWorks) {
                TowerNetworkParticipant participant = towerWork.participant();
                if (!participant.isTowerNetworkActive() || !participant.towerAllowsFe()) {
                    continue;
                }
                TowerRuntimeKey towerKey = participant.towerKey();
                Object storageIdentity = AE2FluxIntegration.ownNetworkEnergyStorageIdentity(
                        participant.towerEnergyHost());
                if (storageIdentity == null) {
                    continue;
                }
                dataEnergistics$addEnergyRoute(
                        routesByStorage,
                        orderedRouteGroups,
                        storageIdentity,
                        new AppFluxGridEnergyTransferEndpoint(
                                new TowerEnergyEndpointId(
                                        towerKey.dimensionId(),
                                        towerKey.position(),
                                        null,
                                        Integer.MAX_VALUE),
                                participant.towerEnergyHost()));
            }
        }
        ArrayList<TowerEnergyTransferEndpoint> endpoints = new ArrayList<>(orderedRouteGroups.size());
        for (List<TowerEnergyTransferEndpoint> routes : orderedRouteGroups) {
            endpoints.add(routes.size() == 1 ? routes.getFirst() : new MultiRouteEnergyTransferEndpoint(routes));
        }
        endpoints.sort(Comparator.comparing(TowerEnergyTransferEndpoint::endpoint, ENERGY_ENDPOINT_ORDER));
        return List.copyOf(endpoints);
    }

    /** Adds one context-sensitive access route without duplicating its physical backing in the planner. */
    private static void dataEnergistics$addEnergyRoute(
                                                       IdentityHashMap<Object, ArrayList<TowerEnergyTransferEndpoint>> routesByStorage,
                                                       List<ArrayList<TowerEnergyTransferEndpoint>> orderedRouteGroups,
                                                       Object storageIdentity,
                                                       TowerEnergyTransferEndpoint route) {
        ArrayList<TowerEnergyTransferEndpoint> routes = routesByStorage.get(storageIdentity);
        if (routes == null) {
            routes = new ArrayList<>();
            routesByStorage.put(storageIdentity, routes);
            orderedRouteGroups.add(routes);
        }
        routes.add(route);
    }

    /**
     * Executes at most one FE transaction for this grid and server tick, independent of tower AE tick sleep state.
     */
    private TowerEnergyTransactionResult dataEnergistics$executeEnergyTransaction(long gameTime) {
        if (this.lastEnergyTransactionTick == gameTime) {
            return this.lastEnergyResult;
        }
        this.lastEnergyTransactionTick = gameTime;
        if (this.energyEndpoints.isEmpty()) {
            this.lastEnergyResult = EMPTY_ENERGY_RESULT;
            return EMPTY_ENERGY_RESULT;
        }
        TowerEnergyTransactionResult result = this.energyTransaction.execute(this.energyEndpoints);
        this.lastEnergyResult = result;
        if (result.quarantinedFe() > 0) {
            dataEnergistics$quarantineEnergy(result.quarantinedFe());
        }
        if (!result.failure().isEmpty() && (this.lastEnergyFailureLogTick == Long.MIN_VALUE || gameTime - this.lastEnergyFailureLogTick >= ENERGY_FAILURE_LOG_INTERVAL_TICKS)) {
            Data_Energistics.LOGGER.error(
                    "Tower FE equalization failed on primary grid {}: {} (planned={}, inserted={}, quarantined={})",
                    this.grid,
                    result.failure(),
                    result.plannedFe(),
                    result.insertedFe(),
                    result.quarantinedFe());
            this.lastEnergyFailureLogTick = gameTime;
        }
        return result;
    }

    /** Stores unrecoverable compensation energy in the first stable active tower's existing isolation buffer. */
    private void dataEnergistics$quarantineEnergy(long amount) {
        ArrayList<TowerNetworkParticipant> orderedTowers = new ArrayList<>(this.towers.values());
        orderedTowers.sort(Comparator.comparing(TowerNetworkParticipant::towerKey));
        for (TowerNetworkParticipant tower : orderedTowers) {
            if (!tower.isTowerNetworkActive()) {
                continue;
            }
            long current = tower.towerQuarantinedEnergy();
            long updated = Long.MAX_VALUE - current < amount ? Long.MAX_VALUE : current + amount;
            tower.setTowerQuarantinedEnergy(updated);
            if (updated - current < amount) {
                Data_Energistics.LOGGER.error(
                        "Tower quarantine at {} saturated while retaining {} FE",
                        tower.towerKey(),
                        amount);
            }
            return;
        }
        Data_Energistics.LOGGER.error(
                "Could not retain {} FE from tower compensation because no active tower remained on grid {}",
                amount,
                this.grid);
    }

    private List<TowerWork> dataEnergistics$resolveTowers() {
        ArrayList<TowerNetworkParticipant> orderedTowers = new ArrayList<>(this.towers.values());
        orderedTowers.sort(Comparator.comparing(TowerNetworkParticipant::towerKey));
        ArrayList<TowerWork> result = new ArrayList<>(orderedTowers.size());
        Map<TargetResolutionKey, TowerTargetResolution> resolutionCache = new HashMap<>();
        CapabilityExposedTowerAeTargetResolver.ResolutionRound resolutionRound = this.targetResolver.beginResolutionRound();
        for (TowerNetworkParticipant participant : orderedTowers) {
            Set<EnergyLocationKey> energyLocations = new HashSet<>();
            if (participant.towerAllowsFe()) {
                for (TowerEnergyLocation location : participant.towerEnergyLocations()) {
                    energyLocations.add(new EnergyLocationKey(
                            location.level().dimension().location(), location.position()));
                }
            }
            ArrayList<TowerBinding> orderedBindings = new ArrayList<>(participant.towerBindings());
            orderedBindings.sort(Comparator
                    .comparingInt((TowerBinding binding) -> binding.source() == TowerBindingSource.MANUAL ? 0 : 1)
                    .thenComparingLong(TowerBinding::fifoSequence));
            ArrayList<BindingWork> bindingWorks = new ArrayList<>(orderedBindings.size());
            Map<IGrid, BindingTargetWork> bindingByTarget = new IdentityHashMap<>();
            for (TowerBinding binding : orderedBindings) {
                TowerTargetResolution resolution;
                if (!participant.towerAllowsAe() || !participant.towerLevel().dimension().location().equals(binding.dimensionId()) || !participant.towerLevel().isLoaded(binding.anchor())) {
                    resolution = new TowerTargetResolution(List.of(), List.of());
                } else {
                    TowerTargetDiscoveryMode discoveryMode = binding.source() == TowerBindingSource.MANUAL ? TowerTargetDiscoveryMode.POINT : TowerTargetDiscoveryMode.SCOPE;
                    TargetResolutionKey resolutionKey = new TargetResolutionKey(
                            participant.towerLevel().dimension().location(), binding.anchor(), discoveryMode);
                    resolution = resolutionCache.computeIfAbsent(
                            resolutionKey,
                            ignored -> resolutionRound.resolve(
                                    participant.towerLevel(), binding.anchor(), this.grid, discoveryMode));
                }
                boolean hasEnergyEndpoint = energyLocations.contains(
                        new EnergyLocationKey(binding.dimensionId(), binding.anchor()));
                BindingWork bindingWork = new BindingWork(
                        participant, binding, resolution, hasEnergyEndpoint);
                bindingWorks.add(bindingWork);
                if (!binding.enabled()) {
                    continue;
                }
                for (TowerResolvedGrid gridResult : resolution.grids()) {
                    if (gridResult.accepted()) {
                        bindingByTarget.putIfAbsent(
                                gridResult.grid(), new BindingTargetWork(bindingWork, gridResult));
                    }
                }
            }
            result.add(new TowerWork(participant, List.copyOf(bindingWorks), bindingByTarget));
        }
        return List.copyOf(result);
    }

    private static List<DeviceWork> dataEnergistics$orderedDevices(Map<IGrid, OwnedGridWork> ownedTargets) {
        Set<IGridNode> seenNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<DeviceWork> devices = new ArrayList<>();
        for (Map.Entry<IGrid, OwnedGridWork> entry : ownedTargets.entrySet()) {
            OwnedGridWork ownedGrid = entry.getValue();
            for (TowerResolvedDevice device : ownedGrid.resolvedGrid().devices()) {
                if (seenNodes.add(device.node())) {
                    devices.add(new DeviceWork(
                            ownedGrid.towerWork(), ownedGrid.bindingWork(), entry.getKey(), device));
                }
            }
        }
        devices.sort(Comparator
                .comparingInt((DeviceWork work) -> work.bindingWork().binding().source() == TowerBindingSource.MANUAL ? 0 : 1)
                .thenComparingLong(work -> work.bindingWork().binding().fifoSequence())
                .thenComparing(work -> work.towerWork().participant().towerKey())
                .thenComparing(work -> work.device().key()));
        return List.copyOf(devices);
    }

    private void dataEnergistics$publishSnapshots(
                                                  List<TowerWork> towerWorks,
                                                  VirtualGridOwnershipSnapshot<IGrid, TowerRuntimeKey> ownership,
                                                  Map<DeviceLeaseKey, DeviceWork> devicesByLease,
                                                  Map<DeviceLeaseKey, VirtualChannelNodeAllocation<IGridNode>> allocations,
                                                  Set<IGrid> bridgeFailures,
                                                  TowerChannelOverview overview,
                                                  List<TowerEnergyEndpointSnapshot> energySnapshots) {
        Map<EnergyLocationKey, EnergySnapshotSummary> energyByLocation = dataEnergistics$aggregateEnergySnapshots(energySnapshots);
        Map<BindingIdentity, ArrayList<TowerVirtualDeviceSnapshot>> deviceSnapshots = new HashMap<>();
        for (Map.Entry<DeviceLeaseKey, DeviceWork> entry : devicesByLease.entrySet()) {
            DeviceWork deviceWork = entry.getValue();
            VirtualChannelNodeAllocation<IGridNode> allocation = allocations.get(entry.getKey());
            TowerVirtualDeviceState state = dataEnergistics$deviceState(allocation.state());
            String failure = "";
            if (bridgeFailures.contains(deviceWork.targetGrid())) {
                state = TowerVirtualDeviceState.BRIDGE_ERROR;
                failure = "GRID_SERVICE_REGISTRATION";
            }
            BlockPos devicePosition = deviceWork.device().key().position();
            BlockPos energyPosition = devicePosition == null ? deviceWork.bindingWork().binding().anchor() : devicePosition;
            ResourceLocation energyDimension = devicePosition == null ? deviceWork.bindingWork().binding().dimensionId() : deviceWork.device().key().dimensionId();
            EnergySnapshotSummary energy = state == TowerVirtualDeviceState.ALLOCATED ? energyByLocation.getOrDefault(
                    new EnergyLocationKey(energyDimension, energyPosition), EnergySnapshotSummary.EMPTY) : EnergySnapshotSummary.EMPTY;
            int requested = deviceWork.device().requiresChannel() ? 1 : 0;
            int granted = allocation.state() == VirtualChannelNodeState.LEASED ? 1 : 0;
            BindingIdentity bindingIdentity = new BindingIdentity(
                    deviceWork.towerWork().participant().towerKey(), deviceWork.bindingWork().binding().fifoSequence());
            DeviceDisplay deviceDisplay = dataEnergistics$deviceDisplay(deviceWork.device().node());
            deviceSnapshots.computeIfAbsent(bindingIdentity, ignored -> new ArrayList<>()).add(
                    new TowerVirtualDeviceSnapshot(
                            deviceWork.bindingWork().binding().anchor(),
                            deviceWork.device().key(),
                            deviceDisplay.itemId(),
                            deviceDisplay.displayName(),
                            requested,
                            granted,
                            state,
                            failure,
                            energy.stored(),
                            energy.capacity(),
                            energy.canExtract(),
                            energy.canReceive()));
        }

        for (TowerWork towerWork : towerWorks) {
            ArrayList<TowerBindingRuntimeSnapshot> bindingSnapshots = new ArrayList<>();
            for (BindingWork bindingWork : towerWork.bindings()) {
                BindingIdentity identity = new BindingIdentity(
                        towerWork.participant().towerKey(), bindingWork.binding().fifoSequence());
                List<TowerVirtualDeviceSnapshot> devices = List.copyOf(
                        deviceSnapshots.getOrDefault(identity, new ArrayList<>()));
                BindingState bindingState = dataEnergistics$bindingState(
                        towerWork, bindingWork, ownership, devices, bridgeFailures);
                long requested = devices.stream().mapToLong(TowerVirtualDeviceSnapshot::requestedChannels).sum();
                long granted = devices.stream().mapToLong(TowerVirtualDeviceSnapshot::grantedChannels).sum();
                EnergySnapshotSummary bindingEnergy = energyByLocation.getOrDefault(
                        new EnergyLocationKey(
                                bindingWork.binding().dimensionId(), bindingWork.binding().anchor()),
                        EnergySnapshotSummary.EMPTY);
                bindingSnapshots.add(new TowerBindingRuntimeSnapshot(
                        bindingWork.binding(),
                        bindingState.state(),
                        bindingState.failure(),
                        requested,
                        granted,
                        devices,
                        bindingEnergy.stored(),
                        bindingEnergy.capacity(),
                        bindingEnergy.canExtract(),
                        bindingEnergy.canReceive()));
            }
            TowerNetworkTowerSnapshot snapshot = new TowerNetworkTowerSnapshot(
                    this.revision, overview, bindingSnapshots);
            this.towerSnapshots.put(towerWork.participant().towerKey(), snapshot);
            towerWork.participant().applyTowerNetworkSnapshot(snapshot);
        }
    }

    /** Aggregates side-specific endpoint snapshots for concise per-device protocol records. */
    private static Map<EnergyLocationKey, EnergySnapshotSummary> dataEnergistics$aggregateEnergySnapshots(
                                                                                                          List<TowerEnergyEndpointSnapshot> snapshots) {
        Map<EnergyLocationKey, EnergySnapshotSummary> result = new HashMap<>();
        for (TowerEnergyEndpointSnapshot snapshot : snapshots) {
            EnergyLocationKey key = new EnergyLocationKey(
                    snapshot.endpoint().dimensionId(), snapshot.endpoint().pos());
            EnergySnapshotSummary current = result.getOrDefault(key, EnergySnapshotSummary.EMPTY);
            result.put(key, current.merge(snapshot));
        }
        return result;
    }

    private static BindingState dataEnergistics$bindingState(
                                                             TowerWork towerWork,
                                                             BindingWork bindingWork,
                                                             VirtualGridOwnershipSnapshot<IGrid, TowerRuntimeKey> ownership,
                                                             List<TowerVirtualDeviceSnapshot> devices,
                                                             Set<IGrid> bridgeFailures) {
        if (!bindingWork.binding().enabled()) {
            return new BindingState(TowerVirtualDeviceState.DISABLED, "");
        }
        if (bindingWork.resolution().grids().isEmpty()) {
            if (bindingWork.hasEnergyEndpoint()) {
                return new BindingState(TowerVirtualDeviceState.ALLOCATED, "");
            }
            return new BindingState(TowerVirtualDeviceState.WAITING_TARGET, "TARGET_UNAVAILABLE");
        }
        ArrayList<String> failures = new ArrayList<>();
        int usableGridCount = 0;
        boolean bridgeFailed = false;
        for (TowerResolvedGrid gridResult : bindingWork.resolution().grids()) {
            if (!gridResult.accepted()) {
                failures.add(gridResult.failure().name());
                continue;
            }
            Optional<VirtualGridCandidateStatus<IGrid, TowerRuntimeKey>> status = ownership.candidateStatus(
                    gridResult.grid(), towerWork.participant().towerKey());
            if (status.isEmpty()) {
                failures.add("OWNERSHIP_UNAVAILABLE");
                continue;
            }
            if (status.orElseThrow().state() != VirtualGridCandidateState.OWNER) {
                failures.add(status.orElseThrow().state().name());
                continue;
            }
            if (bridgeFailures.contains(gridResult.grid())) {
                failures.add("GRID_SERVICE_REGISTRATION");
                bridgeFailed = true;
                continue;
            }
            usableGridCount++;
        }
        if (usableGridCount == 0) {
            return new BindingState(
                    bridgeFailed ? TowerVirtualDeviceState.BRIDGE_ERROR : TowerVirtualDeviceState.CONFLICT,
                    String.join("|", failures));
        }
        String partialFailure = failures.isEmpty() ? "" : "PARTIAL:" + String.join("|", failures);
        if (devices.stream().anyMatch(device -> device.state() == TowerVirtualDeviceState.WAITING_CHANNEL)) {
            String failure = partialFailure.isEmpty() ? "CHANNEL_UNAVAILABLE" : partialFailure + "|CHANNEL_UNAVAILABLE";
            return new BindingState(TowerVirtualDeviceState.WAITING_CHANNEL, failure);
        }
        if (devices.stream().anyMatch(device -> device.state() == TowerVirtualDeviceState.ALLOCATED)) {
            return new BindingState(TowerVirtualDeviceState.ALLOCATED, partialFailure);
        }
        if (devices.stream().anyMatch(device -> device.state() == TowerVirtualDeviceState.DISABLED)) {
            return new BindingState(TowerVirtualDeviceState.DISABLED, partialFailure);
        }
        return new BindingState(TowerVirtualDeviceState.ALLOCATED, partialFailure);
    }

    private static TowerVirtualDeviceState dataEnergistics$deviceState(VirtualChannelNodeState state) {
        return switch (state) {
            case LEASED, AVAILABLE_WITHOUT_CHANNEL -> TowerVirtualDeviceState.ALLOCATED;
            case WAITING_CHANNEL -> TowerVirtualDeviceState.WAITING_CHANNEL;
            case DISABLED -> TowerVirtualDeviceState.DISABLED;
        };
    }

    /** Captures the node's intended UI representation before its chunk or target Grid can unload. */
    private static DeviceDisplay dataEnergistics$deviceDisplay(IGridNode node) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(Items.BARRIER);
        String displayName = "";
        AEItemKey visualRepresentation = node.getVisualRepresentation();
        if (visualRepresentation != null) {
            itemId = visualRepresentation.getId();
            displayName = visualRepresentation.getReadOnlyStack().getHoverName().getString();
        }

        if (node.getOwner() instanceof Nameable nameable) {
            String ownerName = nameable.getDisplayName().getString();
            if (!ownerName.isBlank()) {
                displayName = ownerName;
            }
        }
        return new DeviceDisplay(itemId, displayName);
    }

    private void dataEnergistics$releaseStaleGlobalAttachments(
                                                               VirtualGridOwnershipSnapshot<IGrid, TowerRuntimeKey> ownership) {
        Set<IGrid> checkedTargets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (VirtualGridCandidateStatus<IGrid, TowerRuntimeKey> candidate : ownership.candidates()) {
            IGrid targetGrid = candidate.targetGrid();
            if (!checkedTargets.add(targetGrid)) {
                continue;
            }
            IGrid currentPrimary = ((VirtualGridBridge) targetGrid).virtualPrimaryGrid();
            IGrid desiredPrimary = ownership.ownerOf(targetGrid)
                    .map(VirtualGridOwner::sourceGrid)
                    .orElse(null);
            if (currentPrimary != null && currentPrimary != desiredPrimary) {
                try {
                    ((VirtualGridBridge) targetGrid).clearVirtualMembers();
                } catch (VirtualGridBridgeException exception) {
                    dataEnergistics$logBridgeFailure(
                            targetGrid,
                            this.grid.getPivot().getLevel().getGameTime(),
                            "release stale ownership",
                            exception);
                }
            }
        }
    }

    private void dataEnergistics$detachTarget(IGrid targetGrid) {
        boolean detached = false;
        try {
            ((VirtualGridBridge) targetGrid).clearVirtualMembers();
            detached = true;
        } catch (VirtualGridBridgeException exception) {
            dataEnergistics$logBridgeFailure(
                    targetGrid,
                    this.grid.getPivot().getLevel().getGameTime(),
                    "detach",
                    exception);
        }
        if (detached) {
            this.attachedTargets.remove(targetGrid);
            this.attachedOwners.remove(targetGrid);
            this.lastBridgeFailureLogTicks.remove(targetGrid);
        }
    }

    /**
     * Rate-limits repeated third-party grid-provider failures without hiding their first complete stack trace.
     */
    private void dataEnergistics$logBridgeFailure(
                                                  IGrid targetGrid, long gameTime, String operation, VirtualGridBridgeException exception) {
        Long lastLogTick = this.lastBridgeFailureLogTicks.get(targetGrid);
        if (lastLogTick != null && gameTime - lastLogTick < BRIDGE_FAILURE_LOG_INTERVAL_TICKS) {
            return;
        }
        this.lastBridgeFailureLogTicks.put(targetGrid, gameTime);
        Data_Energistics.LOGGER.error(
                "Failed to {} virtual target grid {} for primary grid {}",
                operation,
                targetGrid,
                this.grid,
                exception);
    }

    private VirtualChannelCapacity dataEnergistics$currentCapacity() {
        if (this.grid.getPathingService().getChannelMode() == ChannelMode.INFINITE) {
            return VirtualChannelCapacity.unlimited();
        }
        return VirtualChannelCapacity.limited(this.capacityCalculator.calculate(this.grid));
    }

    private record DeviceLeaseKey(TowerRuntimeKey towerKey,
                                  BlockPos bindingAnchor,
                                  TowerDeviceKey deviceKey,
                                  IGrid targetGrid) {}

    private record BindingIdentity(TowerRuntimeKey towerKey, long bindingFifo) {}

    /** Identifies one immutable target resolution within a single network reconciliation pass. */
    private record TargetResolutionKey(ResourceLocation dimensionId,
                                       BlockPos anchor,
                                       TowerTargetDiscoveryMode mode) {

        private TargetResolutionKey {
            anchor = anchor.immutable();
        }
    }

    /** Immutable UI identity captured directly from an AE node. */
    private record DeviceDisplay(ResourceLocation itemId, String displayName) {}

    /** Saturated FE aggregate used only for protocol and UI display. */
    private record EnergySnapshotSummary(long stored,
                                         long capacity,
                                         boolean canExtract,
                                         boolean canReceive) {

        private static final EnergySnapshotSummary EMPTY = new EnergySnapshotSummary(0, 0, false, false);

        private EnergySnapshotSummary merge(TowerEnergyEndpointSnapshot snapshot) {
            return new EnergySnapshotSummary(
                    dataEnergistics$saturatingAdd(this.stored, snapshot.stored()),
                    dataEnergistics$saturatingAdd(this.capacity, snapshot.capacity()),
                    this.canExtract || snapshot.direction().allowsExtract(),
                    this.canReceive || snapshot.direction().allowsReceive());
        }
    }

    /** Stable location key used before mutable capability resolution. */
    private record EnergyLocationKey(ResourceLocation dimensionId, BlockPos position)
            implements Comparable<EnergyLocationKey> {

        private EnergyLocationKey {
            position = position.immutable();
        }

        @Override
        public int compareTo(EnergyLocationKey other) {
            int comparison = this.dimensionId.toString().compareTo(other.dimensionId.toString());
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(this.position.getX(), other.position.getX());
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(this.position.getY(), other.position.getY());
            if (comparison != 0) {
                return comparison;
            }
            return Integer.compare(this.position.getZ(), other.position.getZ());
        }
    }

    private record TowerWork(TowerNetworkParticipant participant,
                             List<BindingWork> bindings,
                             Map<IGrid, BindingTargetWork> bindingByTarget) {}

    private record BindingTargetWork(BindingWork bindingWork, TowerResolvedGrid resolvedGrid) {}

    private record BindingWork(TowerNetworkParticipant participant,
                               TowerBinding binding,
                               TowerTargetResolution resolution,
                               boolean hasEnergyEndpoint) {}

    private record OwnedGridWork(TowerWork towerWork,
                                 BindingWork bindingWork,
                                 IGrid targetGrid,
                                 TowerResolvedGrid resolvedGrid) {}

    private record DeviceWork(TowerWork towerWork,
                              BindingWork bindingWork,
                              IGrid targetGrid,
                              TowerResolvedDevice device) {}

    private record BindingState(TowerVirtualDeviceState state, String failure) {}

    /** Adds non-negative FE counters without wrapping UI values. */
    private static long dataEnergistics$saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
