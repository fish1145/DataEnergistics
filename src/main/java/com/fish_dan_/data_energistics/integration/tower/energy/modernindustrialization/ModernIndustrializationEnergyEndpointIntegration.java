package com.fish_dan_.data_energistics.integration.tower.energy.modernindustrialization;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointContext;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccess.EnergySnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

/** Registered long-width EU endpoint strategy for Modern Industrialization. */
public final class ModernIndustrializationEnergyEndpointIntegration implements TowerEnergyEndpointIntegration {

    private final ModernIndustrializationEnergyBridge bridge;

    public ModernIndustrializationEnergyEndpointIntegration(ModernIndustrializationEnergyBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String id() {
        return "modern-industrialization-eu";
    }

    @Override
    @Nullable
    public IEnergyStorage findEnergyStorage(Level level, BlockPos position, @Nullable Direction side) {
        return this.bridge.findEnergyStorage(level, position, side);
    }

    @Override
    public boolean supports(TowerEnergyEndpointContext context) {
        return context.storage() instanceof ModernIndustrializationEnergyStorage;
    }

    @Override
    public TowerEnergyDirection direction(TowerEnergyEndpointContext context) {
        ModernIndustrializationEnergyStorage storage = storage(context);
        TowerEnergyDirection direction = TowerEnergyDirection.fromPermissions(storage.canExtract(), storage.canReceive());
        if (direction == null) {
            throw new IllegalStateException("Modern Industrialization endpoint exposes no usable direction");
        }
        return direction;
    }

    @Override
    public EnergySnapshot snapshot(TowerEnergyEndpointContext context) {
        return storage(context).snapshot();
    }

    @Override
    public long extractionQuantum(TowerEnergyEndpointContext context) {
        return storage(context).transferQuantum();
    }

    @Override
    public long insertionQuantum(TowerEnergyEndpointContext context) {
        return storage(context).transferQuantum();
    }

    @Override
    public long extract(TowerEnergyEndpointContext context, long amount, boolean simulate) {
        return storage(context).extract(amount, simulate);
    }

    @Override
    public long insert(TowerEnergyEndpointContext context, long amount, boolean simulate) {
        return storage(context).insert(amount, simulate);
    }

    @Override
    public long compensateExtraction(TowerEnergyEndpointContext context, long amount) {
        return storage(context).compensateExtraction(amount);
    }

    @Override
    public void publishMutation(TowerEnergyEndpointContext context) {
        BlockEntity blockEntity = context.level().getBlockEntity(context.position());
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }

    @Override
    public int lookupOrder() {
        return 20;
    }

    private static ModernIndustrializationEnergyStorage storage(TowerEnergyEndpointContext context) {
        if (context.storage() instanceof ModernIndustrializationEnergyStorage storage) {
            return storage;
        }
        throw new IllegalArgumentException("Endpoint is not a Modern Industrialization storage");
    }
}
