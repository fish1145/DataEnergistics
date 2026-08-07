package com.fish_dan_.data_energistics.blockentity.tower.network;

import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.appflux.AE2FluxIntegration;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccessImpl;
import com.fish_dan_.data_energistics.integration.tower.BrandonsCoreEnergyBridge;
import com.fish_dan_.data_energistics.integration.tower.OritechEnergyBridge;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Default capability resolver shared by every active tower in one primary-grid domain.
 */
public final class TowerDomainEnergyResolverImpl implements TowerDomainEnergyResolver {

    private final BrandonsCoreEnergyBridge brandonsCore = new BrandonsCoreEnergyBridge();
    private final OritechEnergyBridge oritech = new OritechEnergyBridge();
    private final UnlimitedEnergyAccess unlimitedEnergy = new UnlimitedEnergyAccessImpl();

    @Override
    public List<TowerDomainEnergyEndpoint> resolve(TowerEnergyLocation location) {
        Level level = location.level();
        if (!level.isLoaded(location.position())) {
            return List.of();
        }
        Set<Object> seenStorageIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<TowerDomainEnergyEndpoint> endpoints = new ArrayList<>();
        int storageIdentity = 0;
        for (Direction side : Direction.values()) {
            storageIdentity = addEndpoint(location, side, storageIdentity, seenStorageIdentities, endpoints);
        }
        addEndpoint(location, null, storageIdentity, seenStorageIdentities, endpoints);
        return List.copyOf(endpoints);
    }

    private int addEndpoint(TowerEnergyLocation location,
                            @Nullable Direction side,
                            int storageIdentity,
                            Set<Object> seenStorageIdentities,
                            List<TowerDomainEnergyEndpoint> endpoints) {
        IEnergyStorage storage = findStorage(location, side);
        if (storage == null) {
            return storageIdentity;
        }
        Object backingIdentity = backingIdentity(storage);
        boolean brandonsCoreSupported = this.brandonsCore.supports(storage);
        boolean canExtract = brandonsCoreSupported ? this.brandonsCore.canExtract(storage) : this.unlimitedEnergy.canExtract(storage);
        boolean canReceive = brandonsCoreSupported ? this.brandonsCore.canReceive(storage) : this.unlimitedEnergy.canReceive(storage);
        TowerEnergyDirection direction = TowerEnergyDirection.fromPermissions(canExtract, canReceive);
        if (direction == null) {
            return storageIdentity;
        }
        if (!seenStorageIdentities.add(backingIdentity)) {
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
        if (storage != null || !ModFlags.isOritechEnergySupportLoaded()) {
            return storage;
        }
        return this.oritech.findEnergyStorage(location.level(), location.position(), side);
    }

    /** Resolves only integration identities that are stronger than the capability object's identity. */
    private static Object backingIdentity(IEnergyStorage storage) {
        if (ModFlags.isAppFluxEnergySupportLoaded()) {
            Object appFluxIdentity = AE2FluxIntegration.networkEnergyStorageIdentity(storage);
            if (appFluxIdentity != null) {
                return appFluxIdentity;
            }
        }
        return storage;
    }
}
