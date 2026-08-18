package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointContext;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegrationRegistry;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Resolves loaded FE capabilities for domain-level topology snapshots without forcing chunks.
 */
public final class CapabilityTowerDomainEnergyResolver {

    private final TowerEnergyEndpointIntegrationRegistry integrations;

    /**
     * Creates a resolver with an explicitly composed endpoint registry.
     */
    public CapabilityTowerDomainEnergyResolver(TowerEnergyEndpointIntegrationRegistry integrations) {
        this.integrations = integrations;
    }

    /**
     * Resolves all distinct capability access routes at one loaded location in stable side order. Multiple routes may
     * share one physical backing identity when their access context differs.
     *
     * @param location candidate location
     * @return immutable endpoint list, or empty when the chunk is unloaded
     */
    public List<TowerDomainEnergyEndpoint> resolve(TowerEnergyLocation location) {
        Level level = location.level();
        if (!level.isLoaded(location.position())) {
            return List.of();
        }
        Set<IEnergyStorage> seenStorageRoutes = new ReferenceOpenHashSet<>();
        ArrayList<TowerDomainEnergyEndpoint> endpoints = new ArrayList<>();
        int storageIdentity = 0;
        for (Direction side : Direction.values()) {
            storageIdentity = addEndpoint(location, side, storageIdentity, seenStorageRoutes, endpoints);
        }
        if (endpoints.isEmpty()) {
            addEndpoint(location, null, storageIdentity, seenStorageRoutes, endpoints);
        }
        return List.copyOf(endpoints);
    }

    private int addEndpoint(TowerEnergyLocation location,
                            @Nullable Direction side,
                            int storageIdentity,
                            Set<IEnergyStorage> seenStorageRoutes,
                            List<TowerDomainEnergyEndpoint> endpoints) {
        IEnergyStorage storage = findStorage(location, side);
        if (storage == null || !seenStorageRoutes.add(storage)) {
            return storageIdentity;
        }
        TowerEnergyEndpointContext endpointContext = new TowerEnergyEndpointContext(
                location.level(), location.position(), side, storage);
        TowerEnergyEndpointIntegration integration = this.integrations.resolve(endpointContext);
        TowerEnergyDirection direction = integration.direction(endpointContext);
        if (direction == null) {
            return storageIdentity;
        }
        endpoints.add(new TowerDomainEnergyEndpoint(
                location,
                new TowerEnergyEndpointId(
                        location.level().dimension().location(),
                        location.position(),
                        side,
                        storageIdentity),
                storage,
                integration.backingIdentity(endpointContext),
                direction));
        return Math.incrementExact(storageIdentity);
    }

    @Nullable
    private IEnergyStorage findStorage(TowerEnergyLocation location, @Nullable Direction side) {
        return this.integrations.findEnergyStorage(location.level(), location.position(), side);
    }
}
