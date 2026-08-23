package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class DataAsynchronousProcessingFactoryBlockEntity extends DataRipperReassemblerBlockEntity {

    public static final int ITEM_INPUT_SLOT_COUNT = 21;
    public static final int ITEM_OUTPUT_SLOT_COUNT = 14;
    public static final int FLUID_INPUT_SLOT_COUNT = 6;
    public static final int FLUID_OUTPUT_SLOT_COUNT = 4;
    public static final int KEY_INPUT_SLOT_COUNT = 3;
    public static final int KEY_OUTPUT_SLOT_COUNT = 2;
    private static final int PREVIOUS_ITEM_INPUT_SLOT_COUNT = 18;
    private static final int PREVIOUS_ITEM_OUTPUT_SLOT_COUNT = 12;
    private static final String STORAGE_LAYOUT_VERSION_TAG = "storage_layout_version";
    private static final int STORAGE_LAYOUT_VERSION = 3;

    public DataAsynchronousProcessingFactoryBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_ASYNCHRONOUS_PROCESSING_FACTORY_BLOCK_ENTITY.get(),
                DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(), blockPos, blockState);
    }

    @Override
    protected Block getMachineBlock() {
        return DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get();
    }

    @Override
    public int getItemInputSlotCount() {
        return ITEM_INPUT_SLOT_COUNT;
    }

    @Override
    public int getItemOutputSlotCount() {
        return ITEM_OUTPUT_SLOT_COUNT;
    }

    @Override
    public int getFluidInputSlotCount() {
        return FLUID_INPUT_SLOT_COUNT;
    }

    @Override
    public int getFluidOutputSlotCount() {
        return FLUID_OUTPUT_SLOT_COUNT;
    }

    @Override
    public int getKeyInputSlotCount() {
        return KEY_INPUT_SLOT_COUNT;
    }

    @Override
    public int getKeyOutputSlotCount() {
        return KEY_OUTPUT_SLOT_COUNT;
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        int storageLayoutVersion = data.contains(STORAGE_LAYOUT_VERSION_TAG) ? data.getInt(STORAGE_LAYOUT_VERSION_TAG) : 0;
        super.loadTag(data, registries);
        if (storageLayoutVersion >= STORAGE_LAYOUT_VERSION) {
            return;
        }

        int legacyInputSlotCount = storageLayoutVersion == 2 ? PREVIOUS_ITEM_INPUT_SLOT_COUNT : DataRipperReassemblerBlockEntity.ITEM_INPUT_SLOT_COUNT;
        int legacyOutputSlotCount = storageLayoutVersion == 2 ? PREVIOUS_ITEM_OUTPUT_SLOT_COUNT : DataRipperReassemblerBlockEntity.ITEM_OUTPUT_SLOT_COUNT;
        ItemStack[] legacyOutputs = new ItemStack[legacyOutputSlotCount];
        for (int slot = 0; slot < legacyOutputSlotCount; slot++) {
            int legacyOutputSlot = legacyInputSlotCount + slot;
            legacyOutputs[slot] = this.getStorageInventory().getStackInSlot(legacyOutputSlot).copy();
            this.getStorageInventory().setItemDirect(legacyOutputSlot, ItemStack.EMPTY);
        }
        for (int slot = 0; slot < legacyOutputs.length; slot++) {
            this.getStorageInventory().setItemDirect(this.getItemOutputStartSlot() + slot, legacyOutputs[slot]);
        }
        this.setChanged();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putInt(STORAGE_LAYOUT_VERSION_TAG, STORAGE_LAYOUT_VERSION);
    }
}
