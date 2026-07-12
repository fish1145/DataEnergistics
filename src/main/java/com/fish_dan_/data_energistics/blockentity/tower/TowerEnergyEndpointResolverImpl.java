package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.tower.OritechEnergyBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.blockentity.networking.CableBusBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Default FE endpoint resolver for Data Distribution Towers.
 */
public final class TowerEnergyEndpointResolverImpl implements TowerEnergyEndpointResolver {

    private final TowerEnergyEndpointResolverContext context;
    private final OritechEnergyBridge oritechEnergyBridge;
    private final UnlimitedEnergyAccess unlimitedEnergyAccess;
    private final ArrayList<TowerEnergyEndpoint> reusableEndpointFilter = new ArrayList<>();
    private List<TowerEnergyEndpoint> cachedReceiveEnergyEndpoints = List.of();
    private List<TowerEnergyEndpoint> cachedExtractEnergyEndpoints = List.of();
    private boolean receiveEndpointResolutionValid;
    private boolean extractEndpointResolutionValid;

    /**
     * Creates an endpoint resolver for one tower.
     *
     * @param context               tower state and callbacks required for endpoint discovery
     * @param oritechEnergyBridge   optional Oritech energy lookup bridge
     * @param unlimitedEnergyAccess rate-limit-free storage access used for capability checks
     */
    public TowerEnergyEndpointResolverImpl(TowerEnergyEndpointResolverContext context,
                                           OritechEnergyBridge oritechEnergyBridge,
                                           UnlimitedEnergyAccess unlimitedEnergyAccess) {
        this.context = context;
        this.oritechEnergyBridge = oritechEnergyBridge;
        this.unlimitedEnergyAccess = unlimitedEnergyAccess;
    }

    @Override
    @Nullable
    public IEnergyStorage getEnergyStorageAt(BlockPos pos, @Nullable Direction side) {
        Level level = this.context.level();
        if (level == null || this.context.isTowerBlock(pos)) {
            return null;
        }
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
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
        Level level = this.context.level();
        if (level == null) {
            return List.of();
        }

        ArrayList<TowerEnergyEndpoint> endpoints = new ArrayList<>();
        Set<IEnergyStorage> seenStorages = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean collectAllSides = level.getBlockEntity(pos) instanceof CableBusBlockEntity;

        for (Direction direction : Direction.values()) {
            IEnergyStorage storage = getEnergyStorageAt(pos, direction);
            if (isUsableEnergyStorage(storage, forReceive) && seenStorages.add(storage)) {
                endpoints.add(new TowerEnergyEndpoint(pos.immutable(), direction, storage));
                if (!collectAllSides) {
                    return List.copyOf(endpoints);
                }
            }
        }

        IEnergyStorage internal = getEnergyStorageAt(pos, null);
        if (isUsableEnergyStorage(internal, forReceive) && seenStorages.add(internal)) {
            endpoints.add(new TowerEnergyEndpoint(pos.immutable(), null, internal));
        }
        return List.copyOf(endpoints);
    }

    @Override
    public List<TowerEnergyEndpoint> collectEnergyEndpoints(boolean forReceive, @Nullable BlockPos excludedPos) {
        return excludeEnergyEndpoint(getCachedResolvedEnergyEndpoints(forReceive), excludedPos);
    }

    @Override
    public List<TowerEnergyEndpoint> collectEnergyEndpoints(List<DataDistributionTowerBlockEntity> towers, boolean forReceive) {
        return resolveEnergyEndpoints(towers, forReceive);
    }

    @Override
    public List<TowerEnergyEndpoint> collectClusterEnergyEndpoints(boolean forReceive) {
        return resolveEnergyEndpoints(this.context.collectTowerCluster(), forReceive);
    }

    @Override
    public List<TowerEnergyEndpoint> getCachedResolvedEnergyEndpoints(boolean forReceive) {
        if (this.context.level() == null) {
            return List.of();
        }

        if (forReceive) {
            if (!this.receiveEndpointResolutionValid) {
                this.cachedReceiveEnergyEndpoints = List.copyOf(resolveEnergyEndpoints(this.context.collectTowerCluster(), true));
                this.receiveEndpointResolutionValid = true;
            }
            return this.cachedReceiveEnergyEndpoints;
        }

        if (!this.extractEndpointResolutionValid) {
            this.cachedExtractEnergyEndpoints = List.copyOf(resolveEnergyEndpoints(this.context.collectTowerCluster(), false));
            this.extractEndpointResolutionValid = true;
        }
        return this.cachedExtractEnergyEndpoints;
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
        return storage != null && this.unlimitedEnergyAccess.canReceive(storage);
    }

    @Override
    public void invalidateResolvedCache() {
        this.cachedReceiveEnergyEndpoints = List.of();
        this.cachedExtractEnergyEndpoints = List.of();
        this.receiveEndpointResolutionValid = false;
        this.extractEndpointResolutionValid = false;
    }

    @Override
    public void clearReusableCache() {
        this.reusableEndpointFilter.clear();
    }

    private List<TowerEnergyEndpoint> resolveEnergyEndpoints(List<DataDistributionTowerBlockEntity> towers, boolean forReceive) {
        LinkedHashMap<TowerEnergyEndpointKey, TowerEnergyEndpoint> endpoints = new LinkedHashMap<>();
        for (DataDistributionTowerBlockEntity tower : towers) {
            for (BlockPos pos : this.context.cachedEndpointPositions(tower)) {
                if (!this.context.targetAllowsFe(tower, pos)) {
                    continue;
                }
                if (forReceive && this.context.isDedicatedAeGridTarget(tower, pos)) {
                    continue;
                }

                for (TowerEnergyEndpoint endpoint : this.context.accessibleEnergyEndpoints(tower, pos, forReceive)) {
                    endpoints.putIfAbsent(new TowerEnergyEndpointKey(endpoint.pos(), endpoint.side()), endpoint);
                }
            }
        }

        return List.copyOf(endpoints.values());
    }

    private List<TowerEnergyEndpoint> excludeEnergyEndpoint(List<TowerEnergyEndpoint> endpoints, @Nullable BlockPos excludedPos) {
        if (excludedPos == null || endpoints.isEmpty()) {
            return endpoints;
        }

        this.reusableEndpointFilter.clear();
        for (TowerEnergyEndpoint endpoint : endpoints) {
            if (!excludedPos.equals(endpoint.pos())) {
                this.reusableEndpointFilter.add(endpoint);
            }
        }
        return this.reusableEndpointFilter;
    }

    private boolean isUsableEnergyStorage(@Nullable IEnergyStorage storage, boolean forReceive) {
        return storage != null && (forReceive ? canReceiveEnergy(storage) : this.unlimitedEnergyAccess.canExtract(storage));
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

    private record TowerEnergyEndpointKey(BlockPos pos, @Nullable Direction side) {}
}
