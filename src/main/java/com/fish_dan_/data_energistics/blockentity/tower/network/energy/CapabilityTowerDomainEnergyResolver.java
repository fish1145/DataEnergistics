package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.tower.energy.appflux.AE2FluxIntegration;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.tower.energy.VerifiedUnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.tower.energy.modernindustrialization.ModernIndustrializationEnergyStorage;
import com.fish_dan_.data_energistics.integration.tower.energy.brandonscore.BrandonsCoreEnergyBridge;
import com.fish_dan_.data_energistics.integration.tower.energy.mekanism.MekanismEnergyAccess;
import com.fish_dan_.data_energistics.integration.tower.energy.modernindustrialization.ModernIndustrializationEnergyBridge;
import com.fish_dan_.data_energistics.integration.tower.energy.oritech.OritechEnergyBridge;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Resolves loaded FE capabilities for domain-level topology snapshots without forcing chunks.
 */
public final class CapabilityTowerDomainEnergyResolver {

    private final BrandonsCoreEnergyBridge brandonsCore = new BrandonsCoreEnergyBridge();
    private final ModernIndustrializationEnergyBridge modernIndustrialization = new ModernIndustrializationEnergyBridge();
    private final OritechEnergyBridge oritech = new OritechEnergyBridge();
    private final UnlimitedEnergyAccess unlimitedEnergy = new VerifiedUnlimitedEnergyAccess();

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
        Set<IEnergyStorage> seenStorageRoutes = Collections.newSetFromMap(new IdentityHashMap<>());
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
        Object backingIdentity = backingIdentity(location, side, storage);
        boolean brandonsCoreSupported = this.brandonsCore.supports(storage);
        boolean canExtract = brandonsCoreSupported ? this.brandonsCore.canExtract(storage) : this.unlimitedEnergy.canExtract(storage);
        boolean canReceive = brandonsCoreSupported ? this.brandonsCore.canReceive(storage) : this.unlimitedEnergy.canReceive(storage);
        TowerEnergyDirection direction = TowerEnergyDirection.fromPermissions(canExtract, canReceive);
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
                backingIdentity,
                direction));
        return Math.incrementExact(storageIdentity);
    }

    @Nullable
    private IEnergyStorage findStorage(TowerEnergyLocation location, @Nullable Direction side) {
        IEnergyStorage storage = this.brandonsCore.findEnergyStorage(
                location.level(), location.position(), side);
        if (storage != null) {
            return storage;
        }
        storage = location.level().getCapability(
                Capabilities.EnergyStorage.BLOCK, location.position(), side);
        if (storage != null) {
            return storage;
        }

        storage = this.modernIndustrialization.findEnergyStorage(
                location.level(), location.position(), side);
        if (storage != null || !ModFlags.isOritechEnergySupportLoaded()) {
            return storage;
        }
        return this.oritech.findEnergyStorage(location.level(), location.position(), side);
    }

    /**
     * Resolves only integration identities that are stronger than the capability object's identity.
     */
    private Object backingIdentity(TowerEnergyLocation location, @Nullable Direction side, IEnergyStorage storage) {
        if (ModFlags.isAppFluxEnergySupportLoaded()) {
            Object appFluxIdentity = AE2FluxIntegration.networkEnergyStorageIdentity(storage);
            if (appFluxIdentity != null) {
                return appFluxIdentity;
            }
        }
        if (storage instanceof ModernIndustrializationEnergyStorage modernIndustrializationStorage) {
            return modernIndustrializationStorage.backingIdentity();
        }
        Object mekanismIdentity = MekanismEnergyAccess.findBackingIdentity(
                location.level(), location.position(), side, storage);
        if (mekanismIdentity != null) {
            return mekanismIdentity;
        }
        return storage;
    }
}
