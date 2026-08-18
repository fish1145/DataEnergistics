package com.fish_dan_.data_energistics.integration.tower.energy.mekanism;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointContext;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccess.EnergySnapshot;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Registered long-width energy endpoint strategy for Mekanism. */
public final class MekanismEnergyEndpointIntegration implements TowerEnergyEndpointIntegration {

    @Override
    public String id() {
        return "mekanism-energy";
    }

    @Override
    public boolean supports(TowerEnergyEndpointContext context) {
        return MekanismEnergyAccess.supports(
                context.level(), context.position(), context.side(), context.storage());
    }

    @Override
    public Object backingIdentity(TowerEnergyEndpointContext context) {
        Object identity = MekanismEnergyAccess.findBackingIdentity(
                context.level(), context.position(), context.side(), context.storage());
        return identity == null ? context.storage() : identity;
    }

    @Override
    public TowerEnergyDirection direction(TowerEnergyEndpointContext context) {
        TowerEnergyDirection direction = MekanismEnergyAccess.resolveTransferDirection(
                context.level(), context.position(), context.side(), context.storage());
        if (direction == null) {
            throw new IllegalStateException("Mekanism endpoint exposes no usable direction");
        }
        return direction;
    }

    @Override
    public EnergySnapshot snapshot(TowerEnergyEndpointContext context) {
        return MekanismEnergyAccess.snapshot(
                context.level(), context.position(), context.side(), context.storage());
    }

    @Override
    public long extractionQuantum(TowerEnergyEndpointContext context) {
        return MekanismEnergyAccess.transferQuantum();
    }

    @Override
    public long insertionQuantum(TowerEnergyEndpointContext context) {
        return MekanismEnergyAccess.transferQuantum();
    }

    @Override
    public long extract(TowerEnergyEndpointContext context, long amount, boolean simulate) {
        return MekanismEnergyAccess.extract(
                context.level(), context.position(), context.side(), context.storage(), amount, simulate);
    }

    @Override
    public long insert(TowerEnergyEndpointContext context, long amount, boolean simulate) {
        return MekanismEnergyAccess.insert(
                context.level(), context.position(), context.side(), context.storage(), amount, simulate);
    }

    @Override
    public long compensateExtraction(TowerEnergyEndpointContext context, long amount) {
        return MekanismEnergyAccess.compensateExtraction(
                context.level(), context.position(), context.storage(), amount);
    }

    @Override
    public void publishMutation(TowerEnergyEndpointContext context) {
        BlockEntity blockEntity = context.level().getBlockEntity(context.position());
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }
}
