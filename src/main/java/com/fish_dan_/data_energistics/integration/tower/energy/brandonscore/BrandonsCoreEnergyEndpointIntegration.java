package com.fish_dan_.data_energistics.integration.tower.energy.brandonscore;

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

/** Registered long-width OP endpoint strategy for Draconic Evolution and related blocks. */
public final class BrandonsCoreEnergyEndpointIntegration implements TowerEnergyEndpointIntegration {

    private final BrandonsCoreEnergyBridge bridge;

    public BrandonsCoreEnergyEndpointIntegration(BrandonsCoreEnergyBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String id() {
        return "brandonscore-op";
    }

    @Override
    @Nullable
    public IEnergyStorage findEnergyStorage(Level level, BlockPos position, @Nullable Direction side) {
        return this.bridge.findEnergyStorage(level, position, side);
    }

    @Override
    public boolean supports(TowerEnergyEndpointContext context) {
        return this.bridge.supports(context.storage());
    }

    @Override
    public TowerEnergyDirection direction(TowerEnergyEndpointContext context) {
        return requireDirection(
                this.bridge.canExtract(context.storage()),
                this.bridge.canReceive(context.storage()));
    }

    @Override
    public EnergySnapshot snapshot(TowerEnergyEndpointContext context) {
        return new EnergySnapshot(
                this.bridge.stored(context.storage()),
                this.bridge.capacity(context.storage()));
    }

    @Override
    public long extract(TowerEnergyEndpointContext context, long amount, boolean simulate) {
        return this.bridge.extract(context.storage(), amount, simulate);
    }

    @Override
    public long insert(TowerEnergyEndpointContext context, long amount, boolean simulate) {
        return this.bridge.insert(context.storage(), amount, simulate);
    }

    @Override
    public long compensateExtraction(TowerEnergyEndpointContext context, long amount) {
        return this.bridge.canReceive(context.storage()) ? this.bridge.insert(context.storage(), amount, false) : 0L;
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
        return 10;
    }

    private static TowerEnergyDirection requireDirection(boolean canExtract, boolean canReceive) {
        TowerEnergyDirection direction = TowerEnergyDirection.fromPermissions(canExtract, canReceive);
        if (direction == null) {
            throw new IllegalStateException("BrandonsCore OP endpoint exposes no usable direction");
        }
        return direction;
    }
}
