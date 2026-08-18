package com.fish_dan_.data_energistics.blockentity.tower.energy.registry;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccess.EnergySnapshot;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccessException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

/** Fallback strategy for ordinary NeoForge energy capabilities and typed long-width wrappers. */
public class NeoForgeEnergyEndpointIntegration implements TowerEnergyEndpointIntegration {

    protected final UnlimitedEnergyAccess unlimitedEnergy;

    public NeoForgeEnergyEndpointIntegration(UnlimitedEnergyAccess unlimitedEnergy) {
        this.unlimitedEnergy = unlimitedEnergy;
    }

    @Override
    public String id() {
        return "neoforge-energy";
    }

    @Override
    @Nullable
    public IEnergyStorage findEnergyStorage(Level level, BlockPos position, @Nullable Direction side) {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, position, side);
    }

    @Override
    public boolean supports(TowerEnergyEndpointContext context) {
        return true;
    }

    @Override
    public TowerEnergyDirection direction(TowerEnergyEndpointContext context) {
        TowerEnergyDirection direction = TowerEnergyDirection.fromPermissions(
                context.storage().canExtract(), context.storage().canReceive());
        if (direction == null) {
            throw new IllegalStateException("NeoForge energy endpoint exposes no usable direction");
        }
        return direction;
    }

    @Override
    public EnergySnapshot snapshot(TowerEnergyEndpointContext context) {
        return this.unlimitedEnergy.snapshot(context.storage());
    }

    @Override
    public long extract(TowerEnergyEndpointContext context, long amount, boolean simulate) {
        long direct = this.unlimitedEnergy.extract(context.storage(), amount, simulate);
        return direct == UnlimitedEnergyAccess.UNAVAILABLE ? transferThroughCapability(context.storage(), amount, simulate, false) : direct;
    }

    @Override
    public long insert(TowerEnergyEndpointContext context, long amount, boolean simulate) {
        long direct = this.unlimitedEnergy.insert(context.storage(), amount, simulate);
        return direct == UnlimitedEnergyAccess.UNAVAILABLE ? transferThroughCapability(context.storage(), amount, simulate, true) : direct;
    }

    @Override
    public long compensateExtraction(TowerEnergyEndpointContext context, long amount) {
        long restored = this.unlimitedEnergy.rollbackExtraction(context.storage(), amount);
        return restored == UnlimitedEnergyAccess.UNAVAILABLE ? transferThroughCapability(context.storage(), amount, false, true) : restored;
    }

    @Override
    public void publishMutation(TowerEnergyEndpointContext context) {
        this.unlimitedEnergy.notifyStorageChanged(context.storage());
        BlockEntity blockEntity = context.level().getBlockEntity(context.position());
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }

    protected static long transferThroughCapability(
                                                    IEnergyStorage storage, long amount, boolean simulate, boolean inserting) {
        if (amount <= 0 || (inserting ? !storage.canReceive() : !storage.canExtract())) {
            return 0L;
        }
        long remaining = amount;
        long transferred = 0L;
        while (remaining > 0) {
            int request = (int) Math.min(remaining, Integer.MAX_VALUE);
            int moved = inserting ? storage.receiveEnergy(request, simulate) : storage.extractEnergy(request, simulate);
            if (moved < 0 || moved > request) {
                throw new UnlimitedEnergyAccessException(
                        "NeoForge energy capability returned invalid transfer " + moved + " for " + request);
            }
            if (moved == 0) {
                break;
            }
            transferred += moved;
            remaining -= moved;
        }
        return transferred;
    }
}
