package com.fish_dan_.data_energistics.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Networked compartment base used only by ME-backed compartment roles.
 */
public abstract class AeCompartmentBlockEntity extends CompartmentBlockEntity implements IGridConnectedBlockEntity {

    private final IManagedGridNode mainNode;

    protected AeCompartmentBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState state) {
        super(blockEntityType, pos, state);
        this.mainNode = GridHelper.createManagedNode(this, new BlockEntityNodeListener<>())
                .setVisualRepresentation(state.getBlock())
                .setIdlePowerUsage(0.0D)
                .setInWorldNode(true)
                .setTagName("proxy");
        onGridConnectableSidesChanged();
    }

    @Override
    public final IManagedGridNode getMainNode() {
        return this.mainNode;
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        this.mainNode.loadFromNBT(tag);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.mainNode.saveToNBT(tag);
    }

    @Override
    public void onReady() {
        super.onReady();
        this.mainNode.create(getLevel(), getBlockPos());
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.mainNode.destroy();
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        onGridConnectableSidesChanged();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.mainNode.destroy();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        scheduleInit();
    }

    protected final void onGridConnectableSidesChanged() {
        this.mainNode.setExposedOnSides(getGridConnectableSides(getOrientation()));
    }
}
