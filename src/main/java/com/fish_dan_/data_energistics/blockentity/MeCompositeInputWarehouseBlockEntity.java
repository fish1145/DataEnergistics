package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.common.compartment.CompartmentInventory;
import com.fish_dan_.data_energistics.common.compartment.CompartmentKeyNormalizer;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.MachineSource;

import java.util.Objects;

/**
 * Persistent state and AE pull logic for ME input compartments.
 */
public class MeCompositeInputWarehouseBlockEntity extends AeCompartmentBlockEntity {

    private static final String MARKER_TAG = "markers";
    private static final String ME_INPUT_BUFFER_TAG = "me_input_buffer";
    private static final int ME_INPUT_CONFIGURABLE_SLOTS = 27;
    private static final int ME_INPUT_TRANSFER_PER_TICK = 4000;

    private final CompartmentInventory markerInventory = CompartmentInventory.config(
            MAX_COMPARTMENT_SLOTS,
            this::onStorageChanged,
            this::unlockedSlotCount);
    private final CompartmentInventory meInputBuffer = CompartmentInventory.storage(
            MAX_COMPARTMENT_SLOTS,
            this::onContentInventoryChanged,
            this::unlockedSlotCount);
    private final MachineSource actionSource = new MachineSource(this);

    public MeCompositeInputWarehouseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ME_COMPOSITE_INPUT_WAREHOUSE_BLOCK_ENTITY.get(), pos, state);
    }

    public CompartmentInventory markerInventory() {
        return this.markerInventory;
    }

    public CompartmentInventory meInputBuffer() {
        return this.meInputBuffer;
    }

    @Override
    public int configurableSlotLimit() {
        return ME_INPUT_CONFIGURABLE_SLOTS;
    }

    @Override
    protected void onBoundServerTick() {
        if (this.getMainNode().isActive()) {
            pullMarkedKeysFromNetwork();
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.markerInventory.writeToChildTag(tag, MARKER_TAG, registries);
        this.meInputBuffer.writeToChildTag(tag, ME_INPUT_BUFFER_TAG, registries);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        this.markerInventory.readFromChildTag(tag, MARKER_TAG, registries);
        this.meInputBuffer.readFromChildTag(tag, ME_INPUT_BUFFER_TAG, registries);
    }

    @Override
    protected void rebuildCanonicalStorage() {
        mutableStorage().clear();
        copyInventoryToStorage(this.meInputBuffer);
    }

    private void pullMarkedKeysFromNetwork() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return;
        }
        pullMarkedKeysFromNetwork(node.getGrid().getStorageService().getInventory());
    }

    void pullMarkedKeysFromNetwork(MEStorage networkStorage) {
        Objects.requireNonNull(networkStorage, "networkStorage");
        boolean changed = false;
        for (int slot = 0; slot < Math.min(this.markerInventory.size(), unlockedSlotCount()); slot++) {
            AEKey marker = CompartmentKeyNormalizer.normalize(this.markerInventory.getKey(slot));
            if (marker == null) {
                continue;
            }
            long inserted = pullMarkedKeyFromStorage(networkStorage, slot, marker);
            changed |= inserted > 0L;
        }
        if (changed) {
            setChanged();
        }
    }

    private long pullMarkedKeyFromStorage(MEStorage networkStorage, int slot, AEKey marker) {
        long request = ME_INPUT_TRANSFER_PER_TICK;
        long extracted = networkStorage.extract(marker, request, Actionable.MODULATE, this.actionSource);
        if (extracted <= 0L) {
            return 0L;
        }
        long buffered = this.meInputBuffer.insert(slot, marker, extracted, Actionable.MODULATE);
        long leftover = extracted - buffered;
        if (leftover > 0L) {
            long returned = networkStorage.insert(marker, leftover, Actionable.MODULATE, this.actionSource);
            if (returned != leftover) {
                LOGGER.error(
                        "ME input compartment at {} could not return {} of {} to the network after buffer insert failed",
                        this.worldPosition,
                        leftover - returned,
                        marker);
            }
        }
        return buffered;
    }
}
