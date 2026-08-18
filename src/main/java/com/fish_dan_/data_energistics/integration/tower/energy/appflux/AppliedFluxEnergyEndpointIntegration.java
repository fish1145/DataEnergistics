package com.fish_dan_.data_energistics.integration.tower.energy.appflux;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointContext;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccess.EnergySnapshot;

/** Registered virtual-buffer strategy for Applied Flux network energy. */
public final class AppliedFluxEnergyEndpointIntegration implements TowerEnergyEndpointIntegration {

    @Override
    public String id() {
        return "appflux-network";
    }

    @Override
    public boolean supports(TowerEnergyEndpointContext context) {
        return AE2FluxIntegration.isNetworkEnergyStorage(context.storage());
    }

    @Override
    public Object backingIdentity(TowerEnergyEndpointContext context) {
        Object identity = AE2FluxIntegration.networkEnergyStorageIdentity(context.storage());
        return identity == null ? context.storage() : identity;
    }

    @Override
    public TowerEnergyDirection direction(TowerEnergyEndpointContext context) {
        if (!context.storage().canExtract() || !context.storage().canReceive()) {
            throw new IllegalStateException("Applied Flux network endpoint is not bidirectional");
        }
        return TowerEnergyDirection.BIDIRECTIONAL;
    }

    @Override
    public EnergySnapshot snapshot(TowerEnergyEndpointContext context) {
        long stored = AE2FluxIntegration.extractEnergyFromNetworkStorage(
                context.storage(), Long.MAX_VALUE, true);
        long receivable = AE2FluxIntegration.insertEnergyIntoNetworkStorage(
                context.storage(), Long.MAX_VALUE, true);
        return new EnergySnapshot(stored, saturatingAdd(stored, receivable));
    }

    @Override
    public long extract(TowerEnergyEndpointContext context, long amount, boolean simulate) {
        return AE2FluxIntegration.extractEnergyFromNetworkStorage(context.storage(), amount, simulate);
    }

    @Override
    public long insert(TowerEnergyEndpointContext context, long amount, boolean simulate) {
        return AE2FluxIntegration.insertEnergyIntoNetworkStorage(context.storage(), amount, simulate);
    }

    @Override
    public long compensateExtraction(TowerEnergyEndpointContext context, long amount) {
        return AE2FluxIntegration.insertEnergyIntoNetworkStorage(context.storage(), amount, false);
    }

    @Override
    public boolean isBuffer() {
        return true;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
