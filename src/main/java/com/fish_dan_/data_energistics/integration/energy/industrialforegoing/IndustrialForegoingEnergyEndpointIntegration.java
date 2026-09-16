package com.fish_dan_.data_energistics.integration.energy.industrialforegoing;

import com.fish_dan_.data_energistics.blockentity.tower.energy.access.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.blockentity.tower.energy.access.UnlimitedEnergyStorage;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.NeoForgeEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

/** Industrial Foregoing precedence strategy for long-width infinity energy storage. */
public final class IndustrialForegoingEnergyEndpointIntegration extends NeoForgeEnergyEndpointIntegration {

    public IndustrialForegoingEnergyEndpointIntegration(UnlimitedEnergyAccess unlimitedEnergy) {
        super(unlimitedEnergy);
    }

    @Override
    public String id() {
        return "industrialforegoing-energy";
    }

    @Override
    @Nullable
    public IEnergyStorage findEnergyStorage(Level level, BlockPos position, @Nullable Direction side) {
        IEnergyStorage storage = super.findEnergyStorage(level, position, side);
        return storage != null && IndustrialForegoingEnergyIntegration.supports(storage) ? IndustrialForegoingEnergyIntegration.wrap(storage) : storage;
    }

    @Override
    public boolean supports(TowerEnergyEndpointContext context) {
        return context.storage() instanceof UnlimitedEnergyStorage;
    }

    @Override
    public int lookupOrder() {
        return 150;
    }
}
