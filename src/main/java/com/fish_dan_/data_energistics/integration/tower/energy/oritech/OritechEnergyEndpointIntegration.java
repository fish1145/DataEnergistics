package com.fish_dan_.data_energistics.integration.tower.energy.oritech;

import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.NeoForgeEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointContext;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

/** Oritech lookup strategy backed by the generic verified endpoint operations. */
public final class OritechEnergyEndpointIntegration extends NeoForgeEnergyEndpointIntegration {

    private final OritechEnergyBridge bridge;

    public OritechEnergyEndpointIntegration(OritechEnergyBridge bridge, UnlimitedEnergyAccess unlimitedEnergy) {
        super(unlimitedEnergy);
        this.bridge = bridge;
    }

    @Override
    public String id() {
        return "oritech-energy";
    }

    @Override
    @Nullable
    public IEnergyStorage findEnergyStorage(Level level, BlockPos position, @Nullable Direction side) {
        return this.bridge.findEnergyStorage(level, position, side);
    }

    @Override
    public boolean supports(TowerEnergyEndpointContext context) {
        return context.storage() instanceof UnlimitedEnergyStorage;
    }

    @Override
    public int lookupOrder() {
        return 200;
    }
}
