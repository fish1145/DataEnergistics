package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.tower.BrandonsCoreEnergyBridge;
import com.fish_dan_.data_energistics.integration.tower.MekanismEnergyAccess;
import com.fish_dan_.data_energistics.integration.tower.OritechEnergyBridge;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.blockentity.networking.CableBusBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Resolves side-sensitive tower FE endpoints while caching topology until invalidation and directions per game tick.
 */
public final class CachedTowerEnergyEndpointResolver implements TowerEnergyEndpointResolver {

    private final TowerEnergyEndpointResolverContext context;
    private final BrandonsCoreEnergyBridge brandonsCoreEnergyBridge;
    private final OritechEnergyBridge oritechEnergyBridge;
    private final UnlimitedEnergyAccess unlimitedEnergyAccess;
    private final ArrayList<TowerEnergyEndpoint> reusableEndpointFilter = new ArrayList<>();
    private List<TowerEnergyEndpointCandidate> cachedTopologyEndpoints = List.of();
    private List<TowerEnergyEndpoint> cachedReceiveEnergyEndpoints = List.of();
    private List<TowerEnergyEndpoint> cachedExtractEnergyEndpoints = List.of();
    private boolean topologyResolutionValid;
    private long directionSnapshotTick = Long.MIN_VALUE;

    /**
     * Creates an endpoint resolver for one tower.
     *
     * @param context                  tower state and callbacks required for endpoint discovery
     * @param brandonsCoreEnergyBridge optional BrandonsCore OP capability bridge
     * @param oritechEnergyBridge      optional Oritech energy lookup bridge
     * @param unlimitedEnergyAccess    rate-limit-free storage access used for capability checks
     */
    public CachedTowerEnergyEndpointResolver(TowerEnergyEndpointResolverContext context,
                                             BrandonsCoreEnergyBridge brandonsCoreEnergyBridge,
                                             OritechEnergyBridge oritechEnergyBridge,
                                             UnlimitedEnergyAccess unlimitedEnergyAccess) {
        this.context = context;
        this.brandonsCoreEnergyBridge = brandonsCoreEnergyBridge;
        this.oritechEnergyBridge = oritechEnergyBridge;
        this.unlimitedEnergyAccess = unlimitedEnergyAccess;
    }

    @Override
    @Nullable
    public IEnergyStorage getEnergyStorageAt(BlockPos pos, @Nullable Direction side) {
        Level level = this.context.level();
        if (level == null || !level.isLoaded(pos) || this.context.isTowerBlock(pos)) {
            return null;
        }

        IEnergyStorage storage = this.brandonsCoreEnergyBridge.findEnergyStorage(level, pos, side);
        if (storage != null) {
            return storage;
        }

        storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        if (storage != null || !ModFlags.isOritechEnergySupportLoaded()) {
            return storage;
        }
        return this.oritechEnergyBridge.findEnergyStorage(level, pos, side);
    }

    @Override
    @Nullable
    public IEnergyStorage findAccessibleEnergyStorage(BlockPos pos, boolean forReceive) {
        List<TowerEnergyEndpoint> endpoints = findAccessibleEnergyEndpoints(pos, forReceive);
        return endpoints.isEmpty() ? null : endpoints.getFirst().storage();
    }

    @Override
    public List<TowerEnergyEndpoint> findAccessibleEnergyEndpoints(BlockPos pos, boolean forReceive) {
        return filterByDirection(resolveDirectionalEndpoints(resolveEndpointCandidates(pos)), forReceive);
    }

    @Override
    public List<TowerEnergyEndpoint> collectEnergyEndpoints(boolean forReceive, @Nullable BlockPos excludedPos) {
        return excludeEnergyEndpoint(getCachedResolvedEnergyEndpoints(forReceive), excludedPos);
    }

    @Override
    public List<TowerEnergyEndpoint> collectEnergyEndpoints(List<DataDistributionTowerBlockEntity> towers,
                                                            boolean forReceive) {
        return filterByDirection(resolveDirectionalEndpoints(resolveTopologyEndpoints(towers)), forReceive);
    }

    @Override
    public List<TowerEnergyEndpoint> collectClusterEnergyEndpoints(boolean forReceive) {
        return getCachedResolvedEnergyEndpoints(forReceive);
    }

    @Override
    public List<TowerEnergyEndpoint> getCachedResolvedEnergyEndpoints(boolean forReceive) {
        Level level = this.context.level();
        if (level == null) {
            return List.of();
        }

        if (!this.topologyResolutionValid) {
            this.cachedTopologyEndpoints = resolveTopologyEndpoints(this.context.collectTowerCluster());
            this.topologyResolutionValid = true;
        }

        long gameTime = level.getGameTime();
        if (this.directionSnapshotTick != gameTime) {
            List<TowerEnergyEndpoint> directionalEndpoints = resolveDirectionalEndpoints(this.cachedTopologyEndpoints);
            this.cachedReceiveEnergyEndpoints = filterByDirection(directionalEndpoints, true);
            this.cachedExtractEnergyEndpoints = filterByDirection(directionalEndpoints, false);
            this.directionSnapshotTick = gameTime;
        }
        return forReceive ? this.cachedReceiveEnergyEndpoints : this.cachedExtractEnergyEndpoints;
    }

    @Override
    @Nullable
    public BlockPos normalizeExtractExcludedPos(@Nullable BlockPos excludedPos) {
        return normalizeExcludedPos(excludedPos, false);
    }

    @Override
    @Nullable
    public BlockPos normalizeReceiveExcludedPos(@Nullable BlockPos excludedPos) {
        return normalizeExcludedPos(excludedPos, true);
    }

    @Override
    public boolean canReceiveEnergy(@Nullable IEnergyStorage storage) {
        return storage != null && canReceive(storage);
    }

    @Override
    public void invalidateResolvedCache() {
        this.cachedTopologyEndpoints = List.of();
        this.cachedReceiveEnergyEndpoints = List.of();
        this.cachedExtractEnergyEndpoints = List.of();
        this.topologyResolutionValid = false;
        this.directionSnapshotTick = Long.MIN_VALUE;
    }

    @Override
    public void clearReusableCache() {
        this.reusableEndpointFilter.clear();
    }

    private List<TowerEnergyEndpointCandidate> resolveTopologyEndpoints(
                                                                        List<DataDistributionTowerBlockEntity> towers) {
        LinkedHashMap<TowerEnergyEndpointKey, TowerEnergyEndpointCandidate> endpoints = new LinkedHashMap<>();
        for (DataDistributionTowerBlockEntity tower : towers) {
            for (BlockPos pos : this.context.cachedEndpointPositions(tower)) {
                if (!this.context.targetAllowsFe(tower, pos)) {
                    continue;
                }

                boolean receiveExcluded = this.context.isDedicatedAeGridTarget(tower, pos);
                for (TowerEnergyEndpointCandidate endpoint : resolveEndpointCandidates(pos)) {
                    TowerEnergyEndpointCandidate candidate = endpoint.withReceiveExcluded(receiveExcluded);
                    endpoints.merge(
                            new TowerEnergyEndpointKey(candidate.pos(), candidate.side()),
                            candidate,
                            TowerEnergyEndpointCandidate::mergeReceiveAccess);
                }
            }
        }
        return List.copyOf(endpoints.values());
    }

    private List<TowerEnergyEndpointCandidate> resolveEndpointCandidates(BlockPos pos) {
        Level level = this.context.level();
        if (level == null || !level.isLoaded(pos)) {
            return List.of();
        }

        ArrayList<TowerEnergyEndpointCandidate> endpoints = new ArrayList<>();
        Set<IEnergyStorage> seenStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean collectAllSides = level.getBlockEntity(pos) instanceof CableBusBlockEntity;
        for (Direction direction : Direction.values()) {
            addEndpointCandidate(endpoints, seenStorages, pos, direction, collectAllSides);
        }
        if (endpoints.isEmpty()) {
            addEndpointCandidate(endpoints, seenStorages, pos, null, collectAllSides);
        }
        return List.copyOf(endpoints);
    }

    private void addEndpointCandidate(List<TowerEnergyEndpointCandidate> endpoints, Set<IEnergyStorage> seenStorages,
                                      BlockPos pos, @Nullable Direction side, boolean collectAllSides) {
        IEnergyStorage storage = getEnergyStorageAt(pos, side);
        if (storage != null && seenStorages.add(storage)) {
            endpoints.add(new TowerEnergyEndpointCandidate(pos.immutable(), side, storage, collectAllSides, false));
        }
    }

    private List<TowerEnergyEndpoint> resolveDirectionalEndpoints(List<TowerEnergyEndpointCandidate> candidates) {
        ArrayList<TowerEnergyEndpoint> endpoints = new ArrayList<>();
        Set<BlockPos> selectedSources = new HashSet<>();
        Set<BlockPos> selectedSinks = new HashSet<>();
        for (TowerEnergyEndpointCandidate candidate : candidates) {
            TowerEnergyDirection direction;
            try {
                direction = resolveTransferDirection(candidate);
            } catch (Throwable exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
                Data_Energistics.LOGGER.error(
                        "Failed to resolve tower energy directions at {} side {} storage {}",
                        candidate.pos(), candidate.side(), candidate.storage().getClass().getName(), exception);
                continue;
            }
            if (direction == null) {
                continue;
            }

            boolean canUseSource = direction.allowsExtract();
            boolean canUseSink = direction.allowsReceive() && !candidate.receiveExcluded();
            TowerEnergyDirection usableDirection = TowerEnergyDirection.fromPermissions(canUseSource, canUseSink);
            if (usableDirection == null) {
                continue;
            }

            if (candidate.collectAllSides()) {
                endpoints.add(candidate.withDirection(usableDirection));
                continue;
            }

            boolean selectSource = canUseSource && selectedSources.add(candidate.pos());
            boolean selectSink = canUseSink && selectedSinks.add(candidate.pos());
            TowerEnergyDirection selectedDirection = TowerEnergyDirection.fromPermissions(selectSource, selectSink);
            if (selectedDirection != null) {
                endpoints.add(candidate.withDirection(selectedDirection));
            }
        }
        return List.copyOf(endpoints);
    }

    @Nullable
    private TowerEnergyDirection resolveTransferDirection(TowerEnergyEndpointCandidate candidate) {
        IEnergyStorage storage = candidate.storage();
        if (this.brandonsCoreEnergyBridge.supports(storage)) {
            return TowerEnergyDirection.fromPermissions(
                    this.brandonsCoreEnergyBridge.canExtract(storage),
                    this.brandonsCoreEnergyBridge.canReceive(storage));
        }
        Level level = this.context.level();
        if (level != null && MekanismEnergyAccess.supports(
                level, candidate.pos(), candidate.side(), storage)) {
            return MekanismEnergyAccess.resolveTransferDirection(
                    level, candidate.pos(), candidate.side(), storage);
        }
        return TowerEnergyDirection.fromPermissions(
                this.unlimitedEnergyAccess.canExtract(storage),
                this.unlimitedEnergyAccess.canReceive(storage));
    }

    private boolean canReceive(IEnergyStorage storage) {
        if (this.brandonsCoreEnergyBridge.supports(storage)) {
            return this.brandonsCoreEnergyBridge.canReceive(storage);
        }
        return this.unlimitedEnergyAccess.canReceive(storage);
    }

    private List<TowerEnergyEndpoint> filterByDirection(List<TowerEnergyEndpoint> endpoints, boolean forReceive) {
        if (endpoints.isEmpty()) {
            return endpoints;
        }
        ArrayList<TowerEnergyEndpoint> filtered = new ArrayList<>(endpoints.size());
        for (TowerEnergyEndpoint endpoint : endpoints) {
            if (forReceive ? endpoint.direction().allowsReceive() : endpoint.direction().allowsExtract()) {
                filtered.add(endpoint);
            }
        }
        return List.copyOf(filtered);
    }

    private List<TowerEnergyEndpoint> excludeEnergyEndpoint(List<TowerEnergyEndpoint> endpoints,
                                                            @Nullable BlockPos excludedPos) {
        if (excludedPos == null || endpoints.isEmpty()) {
            return endpoints;
        }

        this.reusableEndpointFilter.clear();
        for (TowerEnergyEndpoint endpoint : endpoints) {
            if (!excludedPos.equals(endpoint.pos())) {
                this.reusableEndpointFilter.add(endpoint);
            }
        }
        return List.copyOf(this.reusableEndpointFilter);
    }

    @Nullable
    private BlockPos normalizeExcludedPos(@Nullable BlockPos excludedPos, boolean forReceive) {
        BlockPos normalizedExcludedPos = excludedPos == null ? null : excludedPos.immutable();
        if (normalizedExcludedPos == null) {
            return null;
        }

        for (TowerEnergyEndpoint endpoint : getCachedResolvedEnergyEndpoints(forReceive)) {
            if (normalizedExcludedPos.equals(endpoint.pos())) {
                return normalizedExcludedPos;
            }
        }
        return null;
    }

    private record TowerEnergyEndpointCandidate(BlockPos pos, @Nullable Direction side, IEnergyStorage storage,
                                                boolean collectAllSides, boolean receiveExcluded) {

        private TowerEnergyEndpointCandidate withReceiveExcluded(boolean receiveExcluded) {
            return new TowerEnergyEndpointCandidate(
                    this.pos, this.side, this.storage, this.collectAllSides, receiveExcluded);
        }

        private TowerEnergyEndpointCandidate mergeReceiveAccess(TowerEnergyEndpointCandidate other) {
            return withReceiveExcluded(this.receiveExcluded && other.receiveExcluded);
        }

        private TowerEnergyEndpoint withDirection(TowerEnergyDirection direction) {
            return new TowerEnergyEndpoint(this.pos, this.side, this.storage, direction);
        }
    }

    private record TowerEnergyEndpointKey(BlockPos pos, @Nullable Direction side) {}
}
